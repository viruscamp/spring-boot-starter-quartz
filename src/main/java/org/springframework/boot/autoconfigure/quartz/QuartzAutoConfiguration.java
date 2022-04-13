/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.quartz;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.quartz.*;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.OnDatabaseInitializationCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Quartz Scheduler.
 *
 * @author Vedran Pavic
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@Configuration
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
		LiquibaseAutoConfiguration.class, FlywayAutoConfiguration.class })
@ConditionalOnClass({ Scheduler.class, SchedulerFactoryBean.class, PlatformTransactionManager.class })
@EnableConfigurationProperties(QuartzProperties.class)
public class QuartzAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SchedulerFactoryBean quartzScheduler(QuartzProperties properties,
			@Autowired(required = false) List<SchedulerFactoryBeanCustomizer> customizers,
			@Autowired(required = false) List<JobDetail> jobDetails,
			@Autowired(required = false) Map<String, Calendar> calendars,
			@Autowired(required = false) List<Trigger> triggers,
			ApplicationContext applicationContext) {
		SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
		SpringBeanJobFactory jobFactory = new AutowireCapableBeanJobFactory(applicationContext.getAutowireCapableBeanFactory());
		schedulerFactoryBean.setJobFactory(jobFactory);
		if (properties.getSchedulerName() != null) {
			schedulerFactoryBean.setSchedulerName(properties.getSchedulerName());
		}
		schedulerFactoryBean.setAutoStartup(properties.isAutoStartup());
		schedulerFactoryBean.setStartupDelay((int) properties.getStartupDelay().getStandardSeconds());
		schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(properties.isWaitForJobsToCompleteOnShutdown());
		schedulerFactoryBean.setOverwriteExistingJobs(properties.isOverwriteExistingJobs());
		if (!properties.getProperties().isEmpty()) {
			schedulerFactoryBean.setQuartzProperties(asProperties(properties.getProperties()));
		}
		if (jobDetails != null && !jobDetails.isEmpty()) {
			schedulerFactoryBean.setJobDetails(jobDetails.toArray(new JobDetail[0]));
		}
		if (calendars != null && !calendars.isEmpty()) {
			schedulerFactoryBean.setCalendars(calendars);
		}
		if (triggers != null && !triggers.isEmpty()) {
			schedulerFactoryBean.setTriggers(triggers.toArray(new Trigger[0]));
		}
		if (customizers != null) {
			AnnotationAwareOrderComparator.sort(customizers);
			for (SchedulerFactoryBeanCustomizer customizer : customizers) {
				customizer.customize(schedulerFactoryBean);
			}
		}
		return schedulerFactoryBean;
	}

	private Properties asProperties(Map<String, String> source) {
		Properties properties = new Properties();
		properties.putAll(source);
		return properties;
	}

	@Configuration
	@Scope(proxyMode = ScopedProxyMode.NO)
	@AutoConfigureBefore(QuartzAutoConfiguration.class)
	//@ConditionalOnSingleCandidate(DataSource.class) // make it fail at `job-store-type=jdbc` without data-source
	@ConditionalOnProperty(prefix = "spring.quartz", name = "job-store-type", havingValue = "jdbc")
	protected static class JdbcStoreTypeConfiguration {

		@Bean
		@Order(0)
		public SchedulerFactoryBeanCustomizer dataSourceCustomizer(
				QuartzProperties properties, final DataSource dataSource,
				@QuartzDataSource final ObjectProvider<DataSource> quartzDataSource,
				final ObjectProvider<PlatformTransactionManager> transactionManager,
				@QuartzTransactionManager final ObjectProvider<PlatformTransactionManager> quartzTransactionManager) {
			return new SchedulerFactoryBeanCustomizer() {
				@Override
				public void customize(SchedulerFactoryBean schedulerFactoryBean) {
					DataSource dataSourceToUse = getDataSource(dataSource, quartzDataSource);
					schedulerFactoryBean.setDataSource(dataSourceToUse);
					PlatformTransactionManager txManager = getTransactionManager(transactionManager, quartzTransactionManager);
					if (txManager != null) {
						schedulerFactoryBean.setTransactionManager(txManager);
					}
				}
			};
		}

		private DataSource getDataSource(DataSource dataSource, ObjectProvider<DataSource> quartzDataSource) {
			DataSource dataSourceIfAvailable = quartzDataSource.getIfAvailable();
			return (dataSourceIfAvailable != null) ? dataSourceIfAvailable : dataSource;
		}

		private PlatformTransactionManager getTransactionManager(
				ObjectProvider<PlatformTransactionManager> transactionManager,
				ObjectProvider<PlatformTransactionManager> quartzTransactionManager) {
			PlatformTransactionManager transactionManagerIfAvailable = quartzTransactionManager.getIfAvailable();
			return (transactionManagerIfAvailable != null) ? transactionManagerIfAvailable
					: transactionManager.getIfUnique();
		}

		@Bean
		@ConditionalOnMissingBean(QuartzDatabaseInitializer.class)
		@Conditional(OnQuartzDatasourceInitializationCondition.class)
		public QuartzDatabaseInitializer quartzDatabaseInitializer(
				DataSource dataSource,
				@QuartzDataSource ObjectProvider<DataSource> quartzDataSource,
				ResourceLoader resourceLoader,
				QuartzProperties properties) {
			DataSource dataSourceToUse = getDataSource(dataSource, quartzDataSource);
			return new QuartzDatabaseInitializer(dataSourceToUse, resourceLoader, properties);
		}

		static class OnQuartzDatasourceInitializationCondition extends OnDatabaseInitializationCondition {

			OnQuartzDatasourceInitializationCondition() {
				super("Quartz", "spring.quartz.jdbc.initialize-schema");
			}

		}

	}

}
