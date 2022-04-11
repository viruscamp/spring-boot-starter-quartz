/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.quartz;

import java.util.concurrent.Executor;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.BDDMockito;
import org.quartz.*;
import org.quartz.impl.calendar.MonthlyCalendar;
import org.quartz.impl.calendar.WeeklyCalendar;
import org.quartz.simpl.RAMJobStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link QuartzAutoConfiguration}.
 *
 * @author Vedran Pavic
 */
public class QuartzAutoConfigurationTests {

	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Rule
	public OutputCapture output = new OutputCapture();

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void withNoDataSource() throws Exception {
		registerAndRefresh(QuartzAutoConfiguration.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(RAMJobStore.class);
	}

	@Test
	public void withDataSourceUseMemoryByDefault() throws Exception {
		registerAndRefresh(
				QuartzAutoConfiguration.class,
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(RAMJobStore.class);
	}

	@Test
	public void withDataSource() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh(
				QuartzJobsConfiguration.class,
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				QuartzAutoConfiguration.class);
		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	@Test
	@Ignore
	public void withDataSourceChangeConfigurationOrder() throws Exception {
		// when `QuartzAutoConfiguration` is before `DataSourceAutoConfiguration`, it should not fail
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh(
				QuartzAutoConfiguration.class,
				QuartzJobsConfiguration.class,
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	@Test
	public void withDataSourceAndInMemoryStoreDoesNotInitializeDataSource() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh(
				QuartzJobsConfiguration.class,
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				QuartzAutoConfiguration.class);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean("dataSource", DataSource.class));
		assertThat(jdbcTemplate.queryForList("SHOW TABLES").stream()
				.map((table) -> (String) table.get("TABLE_NAME"))
				.noneMatch((name) -> name.startsWith("QRTZ")));
	}

	@Test
	public void withDataSourceNoTransactionManager() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh(
				QuartzJobsConfiguration.class,
				DataSourceAutoConfiguration.class,
				QuartzAutoConfiguration.class);
		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	@Test
	public void dataSourceWithQuartzDataSourceQualifierUsedWhenMultiplePresent() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.comment-prefix=--,#");
		registerAndRefresh(
				QuartzJobsConfiguration.class,
				MultipleDataSourceConfiguration.class,
				QuartzAutoConfiguration.class);
		assertDataSourceInitializedByDatabaseInitializer("quartzDataSource");
	}

	@Test
	public void transactionManagerWithQuartzTransactionManagerUsedWhenMultiplePresent() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.comment-prefix=--,#");
		registerAndRefresh(
				QuartzJobsConfiguration.class,
				MultipleTransactionManagersConfiguration.class,
				QuartzAutoConfiguration.class);
		SchedulerFactoryBean schedulerFactoryBean = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactoryBean).isNotNull();
		assertThat(schedulerFactoryBean)
				.hasFieldOrPropertyWithValue("transactionManager",
				context.getBean("quartzTransactionManager"));
	}

	@Test
	public void withTaskExecutor() throws SchedulerException {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.threadPool.threadCount=50");
		registerAndRefresh(QuartzAutoConfiguration.class, MockExecutorConfiguration.class);

		Scheduler scheduler = context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();assertThat(scheduler.getMetaData().getThreadPoolSize()).isEqualTo(50);
		Executor executor = context.getBean(Executor.class);
		BDDMockito.verifyZeroInteractions(executor);
	}

	@Test
	public void withOverwriteExistingJobs() throws SchedulerException {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.overwrite-existing-jobs=true");
		registerAndRefresh(QuartzAutoConfiguration.class, OverwriteTriggerConfiguration.class);

		Scheduler scheduler = context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		Trigger fooTrigger = scheduler.getTrigger(TriggerKey.triggerKey("fooTrigger"));
		assertThat(fooTrigger).isNotNull();
		assertThat(((SimpleTrigger) fooTrigger).getRepeatInterval()).isEqualTo(30000);
	}

	@Test
	public void withConfiguredJobAndTrigger() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "test-name=withConfiguredJobAndTrigger");
		registerAndRefresh(QuartzAutoConfiguration.class, QuartzFullConfiguration.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler.getJobDetail(JobKey.jobKey("fooJob"))).isNotNull();
		assertThat(scheduler.getTrigger(TriggerKey.triggerKey("fooTrigger"))).isNotNull();
		Thread.sleep(1000L);
		this.output.expect(containsString("withConfiguredJobAndTrigger"));
		this.output.expect(containsString("jobDataValue"));
	}

	@Test
	public void withConfiguredCalendars() throws Exception {
		registerAndRefresh(QuartzAutoConfiguration.class, QuartzCalendarsConfiguration.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler.getCalendar("weekly")).isNotNull();
		assertThat(scheduler.getCalendar("monthly")).isNotNull();
	}

	@Test
	public void withQuartzProperties() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.scheduler.instanceId=FOO");
		registerAndRefresh(QuartzAutoConfiguration.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getSchedulerInstanceId()).isEqualTo("FOO");
	}

	@Test
	public void withCustomizer() throws Exception {
		registerAndRefresh(QuartzAutoConfiguration.class, QuartzCustomConfig.class);
		Scheduler scheduler = this.context.getBean(Scheduler.class);

		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getSchedulerName()).isEqualTo("fooScheduler");
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	private void assertDataSourceInitialized(String dataSourceName) throws Exception {
		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(LocalDataSourceJobStore.class);
		JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean(dataSourceName, DataSource.class));
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS", Integer.class))
				.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM QRTZ_SIMPLE_TRIGGERS", Integer.class))
				.isEqualTo(0);
	}

	private void assertDataSourceInitializedByDatabaseInitializer(
			String dataSourceName) throws Exception {
		assertDataSourceInitialized(dataSourceName);

		QuartzDatabaseInitializer initializer = context
				.getBean(QuartzDatabaseInitializer.class);
		assertThat(initializer).isNotNull();
		assertThat(initializer).hasFieldOrPropertyWithValue("dataSource", context.getBean(dataSourceName));
	}

	private void assertSchedulerName(String schedulerName) {
		SchedulerFactoryBean schedulerFactory = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactory).isNotNull();
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("schedulerName", schedulerName);
	}

	@Configuration
	protected static class QuartzJobsConfiguration {

		@Bean
		JobDetail fooJob() {
			return JobBuilder.newJob().ofType(FooJob.class).withIdentity("fooJob").storeDurably().build();
		}

		@Bean
		JobDetail barJob() {
			return JobBuilder.newJob().ofType(FooJob.class).withIdentity("barJob").storeDurably().build();
		}

	}

	@Configuration
	protected static class QuartzFullConfiguration {

		@Bean
		public JobDetail fooJob() {
			return JobBuilder.newJob().ofType(FooJob.class).withIdentity("fooJob")
					.usingJobData("jobDataKey", "jobDataValue").storeDurably().build();
		}

		@Bean
		public Trigger fooTrigger(JobDetail jobDetail) {
			SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(10)
					.repeatForever();

			return TriggerBuilder.newTrigger().forJob(jobDetail).withIdentity("fooTrigger")
					.withSchedule(scheduleBuilder).build();
		}

	}

	@Configuration
	protected static class QuartzCalendarsConfiguration {

		@Bean
		public Calendar weekly() {
			return new WeeklyCalendar();
		}

		@Bean
		public Calendar monthly() {
			return new MonthlyCalendar();
		}

	}

	@Configuration
	static class MockExecutorConfiguration {
		@Bean
		Executor executor() {
			return mock(Executor.class);
		}
	}

	@Configuration
	@Import(QuartzFullConfiguration.class)
	static class OverwriteTriggerConfiguration {
		@Bean
		Trigger anotherFooTrigger(JobDetail fooJob) {
			SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(30)
					.repeatForever();

			return TriggerBuilder.newTrigger().forJob(fooJob).withIdentity("fooTrigger").withSchedule(scheduleBuilder)
					.build();
		}
	}

	@Configuration
	protected static class QuartzCustomConfig {

		@Bean
		public SchedulerFactoryBeanCustomizer customizer() {
			return new SchedulerFactoryBeanCustomizer() {
				@Override
				public void customize(SchedulerFactoryBean schedulerFactoryBean) {
					schedulerFactoryBean.setSchedulerName("fooScheduler");
				}
			};
		}
	}

	public static class FooJob extends QuartzJobBean {

		@Autowired
		private Environment env;

		private String jobDataKey;

		@Override
		protected void executeInternal(JobExecutionContext context) {
			System.out.println(this.env.getProperty("test-name", "unknown") + " - " + this.jobDataKey);
		}

		public void setJobDataKey(String jobDataKey) {
			this.jobDataKey = jobDataKey;
		}

	}

	@Configuration
	protected static class MultipleDataSourceConfiguration {

		@Bean
		@Primary
		DataSource applicationDataSource() throws Exception {
			return createTestDataSource();
		}

		@QuartzDataSource
		@Bean
		DataSource quartzDataSource() throws Exception {
			return createTestDataSource();
		}

		private DataSource createTestDataSource() throws Exception {
			DataSourceProperties properties = new DataSourceProperties();
			properties.setGenerateUniqueName(true);
			properties.afterPropertiesSet();
			return properties.initializeDataSourceBuilder().build();
		}
	}

	@Configuration
	protected static class MultipleTransactionManagersConfiguration {

		private final DataSource primaryDataSource = createTestDataSource();

		private final DataSource quartzDataSource = createTestDataSource();

		@Bean
		@Primary
		DataSource applicationDataSource() {
			return this.primaryDataSource;
		}

		@Bean
		@QuartzDataSource
		DataSource quartzDataSource() {
			return this.quartzDataSource;
		}

		@Bean
		@Primary
		PlatformTransactionManager applicationTransactionManager() {
			return new DataSourceTransactionManager(this.primaryDataSource);
		}

		@Bean
		@QuartzTransactionManager
		PlatformTransactionManager quartzTransactionManager() {
			return new DataSourceTransactionManager(this.quartzDataSource);
		}

		private DataSource createTestDataSource() {
			DataSourceProperties properties = new DataSourceProperties();
			properties.setGenerateUniqueName(true);
			try {
				properties.afterPropertiesSet();
			}
			catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return properties.initializeDataSourceBuilder().build();
		}
	}
}