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

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.BDDMockito;
import org.quartz.*;
import org.quartz.impl.calendar.MonthlyCalendar;
import org.quartz.impl.calendar.WeeklyCalendar;
import org.quartz.simpl.RAMJobStore;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
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
		registerAndRefresh();

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(RAMJobStore.class);
	}

	/**
	 * the old behavior, `job-store-type=jdbc` without data-source will pass
	 * @throws Exception
	 */
	@Test
	@Ignore
	public void withNoDataSourceAndPropertyJdbcPassSilent() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh();

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(RAMJobStore.class);
	}

	/**
	 * the new behavior, `job-store-type=jdbc` without data-source will fail
	 */
	@Test(expected = UnsatisfiedDependencyException.class)
	public void withNoDataSourceAndPropertyJdbcShouldFail() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAndRefresh();
	}

	@Test
	public void withDataSourceUseMemoryByDefault() throws Exception {
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh();

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getMetaData().getJobStoreClass()).isAssignableFrom(RAMJobStore.class);
	}

	@Test
	public void withDataSource() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);

		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	/**
	 * when {@link QuartzAutoConfiguration} is before {@link DataSourceAutoConfiguration}, it should not fail。
	 * {@link AutoConfigureAfter} and {@link AutoConfigureBefore} should be valid only for
	 * `EnableAutoConfiguration=QuartzAutoConfiguration` in file `spring.factories`.
	 * So this test is not required.
	 * @throws Exception
	 */
	@Test
	@Ignore
	public void withDataSourceConfigOrder() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);

		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	@Test
	public void withDataSourceAndInMemoryStoreDoesNotInitializeDataSource() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=memory");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);

		JdbcTemplate jdbcTemplate = new JdbcTemplate(context.getBean("dataSource", DataSource.class));
		assertThat(jdbcTemplate.queryForList("SHOW TABLES"))
				.areNot(new org.assertj.core.api.Condition<Map<String, Object>>() {
					public boolean matches(Map<String, Object> table) {
						String name = (String) table.get("TABLE_NAME");
						return name.startsWith("QRTZ");
					}
				});
	}

	@Test
	public void withDataSourceNoTransactionManager() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAutoConfigurations(DataSourceAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);

		assertDataSourceInitializedByDatabaseInitializer("dataSource");
	}

	@Test
	public void dataSourceWithQuartzDataSourceQualifierUsedWhenMultiplePresent() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.comment-prefix=--,#");
		registerAndRefresh(QuartzJobsConfiguration.class, MultipleDataSourceConfiguration.class);

		assertDataSourceInitializedByDatabaseInitializer("quartzDataSource");
	}

	@Test
	public void transactionManagerWithQuartzTransactionManagerUsedWhenMultiplePresent() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.comment-prefix=--,#");
		registerAndRefresh(QuartzJobsConfiguration.class, MultipleTransactionManagersConfiguration.class);

		SchedulerFactoryBean schedulerFactoryBean = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactoryBean).isNotNull();
		assertThat(schedulerFactoryBean)
				.hasFieldOrPropertyWithValue("transactionManager",
				context.getBean("quartzTransactionManager"));
	}

	@Test
	public void withTaskExecutor() throws SchedulerException {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.threadPool.threadCount=50");
		registerAndRefresh(MockExecutorConfiguration.class);

		Scheduler scheduler = context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();assertThat(scheduler.getMetaData().getThreadPoolSize()).isEqualTo(50);
		Executor executor = context.getBean(Executor.class);
		BDDMockito.verifyZeroInteractions(executor);
	}

	@Test
	public void withOverwriteExistingJobs() throws SchedulerException {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.overwrite-existing-jobs=true");
		registerAndRefresh(OverwriteTriggerConfiguration.class);

		Scheduler scheduler = context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		Trigger fooTrigger = scheduler.getTrigger(TriggerKey.triggerKey("fooTrigger"));
		assertThat(fooTrigger).isNotNull();
		assertThat(((SimpleTrigger) fooTrigger).getRepeatInterval()).isEqualTo(30000);
	}

	@Test
	public void withConfiguredJobAndTrigger() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "test-name=withConfiguredJobAndTrigger");
		registerAndRefresh(QuartzFullConfiguration.class);

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler.getJobDetail(JobKey.jobKey("fooJob"))).isNotNull();
		assertThat(scheduler.getTrigger(TriggerKey.triggerKey("fooTrigger"))).isNotNull();
		Thread.sleep(1000L);
		this.output.expect(containsString("withConfiguredJobAndTrigger"));
		this.output.expect(containsString("jobDataValue"));
	}

	@Test
	public void withConfiguredCalendars() throws Exception {
		registerAndRefresh(QuartzCalendarsConfiguration.class);

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler.getCalendar("weekly")).isNotNull();
		assertThat(scheduler.getCalendar("monthly")).isNotNull();
	}

	@Test
	public void withQuartzProperties() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.scheduler.instanceId=FOO");
		registerAndRefresh();

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getSchedulerInstanceId()).isEqualTo("FOO");
	}

	@Test
	public void withCustomizer() throws Exception {
		registerAndRefresh(QuartzCustomConfig.class);

		Scheduler scheduler = this.context.getBean(Scheduler.class);
		assertThat(scheduler).isNotNull();
		assertThat(scheduler.getSchedulerName()).isEqualTo("fooScheduler");
	}

	@Test
	public void validateDefaultProperties() {
		registerAndRefresh(ManualSchedulerConfiguration.class);

		SchedulerFactoryBean schedulerFactory = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactory).isNotNull();
		QuartzProperties properties = new QuartzProperties();
		assertThat(properties.isAutoStartup()).isEqualTo(schedulerFactory.isAutoStartup());
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("startupDelay",
				(int) properties.getStartupDelay().getStandardSeconds());
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("waitForJobsToCompleteOnShutdown",
				properties.isWaitForJobsToCompleteOnShutdown());
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("overwriteExistingJobs",
				properties.isOverwriteExistingJobs());
	}

	/**
	 * in spring-context-4.x, the {@link org.springframework.format.datetime.standard.DurationFormatter}
	 * does not support `Duration` simple format like '1m', '3s' etc.
	 */
	@Test
	public void withCustomConfiguration() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.startup-delay=PT60S");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.auto-startup=false");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.wait-for-jobs-to-complete-on-shutdown=true");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.overwrite-existing-jobs=true");
		registerAndRefresh();

		SchedulerFactoryBean schedulerFactory = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactory).isNotNull();
		assertThat(schedulerFactory.isAutoStartup()).isFalse();
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("startupDelay", 60);
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("waitForJobsToCompleteOnShutdown", true);
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("overwriteExistingJobs", true);
	}

	@Test
	public void withLiquibase() throws Exception {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.initialize-schema=never");
		EnvironmentTestUtils.addEnvironment(context, "liquibase.change-log=classpath:org/quartz/impl/jdbcjobstore/liquibase.quartz.init.xml");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				LiquibaseAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);
		assertDataSourceInitialized("dataSource");
		assertNotHaveBean(context, QuartzDatabaseInitializer.class);
	}

	@Test
	public void withFlyway() throws Exception {
		File flywayLocation = FileUtils.getFile(FileUtils.getTempDirectory(), "starter-quartz-tests-");
		flywayLocation.mkdirs();
		flywayLocation.deleteOnExit();
		ClassPathResource tablesResource = new ClassPathResource("org/quartz/impl/jdbcjobstore/tables_h2.sql");
		File flywaySqlFile = FileUtils.getFile(flywayLocation, "V2__quartz.sql");
		FileUtils.copyToFile(tablesResource.getInputStream(), flywaySqlFile);
		flywaySqlFile.deleteOnExit();

		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.jdbc.initialize-schema=never");
		EnvironmentTestUtils.addEnvironment(context, "flyway.locations=filesystem:" + flywayLocation);
		EnvironmentTestUtils.addEnvironment(context, "flyway.baseline-on-migrate=true");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class,
				FlywayAutoConfiguration.class);
		registerAndRefresh(QuartzJobsConfiguration.class);

		assertDataSourceInitialized("dataSource");
		assertNotHaveBean(context, QuartzDatabaseInitializer.class);
	}

	@Test
	public void schedulerNameWithDedicatedProperty() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.scheduler-name=testScheduler");
		registerAndRefresh();
		assertSchedulerName("testScheduler");
	}

	/**
	 * in spring-context-support-4.x, the {@link SchedulerFactoryBean}
	 * use {@link SchedulerFactoryBean#schedulerName} as {@link SchedulerFactoryBean#setBeanName(String)}.
	 * With {@link QuartzProperties#schedulerName} == null, the {@link org.springframework.beans.factory.BeanNameAware}
	 * use {@link SchedulerFactoryBean#setBeanName(String)} to set {@link SchedulerFactoryBean#schedulerName}.
	 * Cause the property "spring.quartz.properties.org.quartz.scheduler.instanceName=testScheduler" useless.
	 */
	@Test
	@Ignore
	public void schedulerNameWithQuartzProperty() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.scheduler.instanceName=testScheduler");
		registerAndRefresh();
		SchedulerFactoryBean schedulerFactory = context.getBean(SchedulerFactoryBean.class);
		QuartzProperties quartzProperties = context.getBean(QuartzProperties.class);
		assertSchedulerName("testScheduler");
	}

	@Test
	public void schedulerNameWithDedicatedPropertyTakesPrecedence() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.scheduler-name=specificTestScheduler");
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.properties.org.quartz.scheduler.instanceName=testScheduler");
		registerAndRefresh();
		assertSchedulerName("specificTestScheduler");
	}

	@Test
	public void schedulerNameUseBeanNameByDefault() {
		registerAndRefresh();
		assertSchedulerName("quartzScheduler");
	}

	@Test
	public void whenTheUserDefinesTheirOwnQuartzDatabaseInitializerThenTheAutoConfiguredInitializerBacksOff() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh(CustomQuartzDatabaseInitializerConfiguration.class);

		assertThat(context.getBean(QuartzDatabaseInitializer.class)).isNotNull();
		assertNotHaveBean(context, "quartzDatabaseInitializer");
		assertThat(context.getBean("customInitializer")).isNotNull();
	}

	@Test
	public void whenTheUserDefinesTheirOwnDatabaseInitializerThenTheAutoConfiguredQuartzInitializerRemains() {
		EnvironmentTestUtils.addEnvironment(context, "spring.quartz.job-store-type=jdbc");
		registerAutoConfigurations(
				DataSourceAutoConfiguration.class,
				DataSourceTransactionManagerAutoConfiguration.class);
		registerAndRefresh(CustomDatabaseInitializerConfiguration.class);

		assertThat(context.getBean(QuartzDatabaseInitializer.class)).isNotNull();
		assertThat(context.getBean("customInitializer")).isNotNull();
	}

	private Class<?>[] autoConfigurations = new Class<?>[0];
	// register system Configurations
	private void registerAutoConfigurations(Class<?>... autoConfigurations) {
		//context.register(autoConfigurations);
		this.autoConfigurations = autoConfigurations;
	}

	// register user Configurations and then refresh
	private void registerAndRefresh(Class<?>... userConfigurations) {
		EnvironmentTestUtils.addEnvironment(context, "spring.datasource.generate-unique-name=true");
		if (userConfigurations.length > 0) {
			context.register(userConfigurations);
		}
		if (autoConfigurations.length > 0) {
			context.register(autoConfigurations);
		}
		context.register(ConversionServiceConfiguration.class);
		context.register(QuartzAutoConfiguration.class);
		context.refresh();
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
		QuartzDatabaseInitializer initializer = context.getBean(QuartzDatabaseInitializer.class);
		assertThat(initializer).isNotNull();
		assertThat(initializer).hasFieldOrPropertyWithValue("dataSource", context.getBean(dataSourceName));
	}

	private void assertSchedulerName(String schedulerName) {
		SchedulerFactoryBean schedulerFactory = context.getBean(SchedulerFactoryBean.class);
		assertThat(schedulerFactory).isNotNull();
		assertThat(schedulerFactory).hasFieldOrPropertyWithValue("schedulerName", schedulerName);
	}

	private static void assertNotHaveBean(final ApplicationContext context, final Class<?> beanType) {
		Assertions.assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
			public void call() throws Throwable {
				context.getBean(beanType);
			}
		})
				.isInstanceOf(NoSuchBeanDefinitionException.class);
	}

	private static void assertNotHaveBean(final ApplicationContext context, final String beanName) {
		Assertions.assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
			public void call() throws Throwable {
				context.getBean(beanName);
			}
		})
				.isInstanceOf(NoSuchBeanDefinitionException.class);
	}

	/**
	 *  Ensure that userConfigurations are before autoConfigurations, to override DataSource etc.
	 *  But in spring-boot-1.5, {@link ComponentThatUsesScheduler} cannot get an initialized {@link Scheduler}.
	 *  So we should bypass it.
	 */
	@Configuration
	//@Import(ComponentThatUsesScheduler.class)
	static class BaseQuartzConfiguration {
	}

	@Configuration
	protected static class QuartzJobsConfiguration extends BaseQuartzConfiguration {
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
	protected static class QuartzFullConfiguration extends BaseQuartzConfiguration {
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
	@Import(QuartzFullConfiguration.class)
	static class OverwriteTriggerConfiguration extends BaseQuartzConfiguration {
		@Bean
		Trigger anotherFooTrigger(JobDetail fooJob) {
			SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(30)
					.repeatForever();

			return TriggerBuilder.newTrigger().forJob(fooJob).withIdentity("fooTrigger").withSchedule(scheduleBuilder)
					.build();
		}
	}

	@Configuration
	protected static class QuartzCalendarsConfiguration extends BaseQuartzConfiguration {
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
	static class MockExecutorConfiguration extends BaseQuartzConfiguration {
		@Bean
		Executor executor() {
			return mock(Executor.class);
		}
	}

	@Configuration
	protected static class QuartzCustomConfig extends BaseQuartzConfiguration {
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

	@Configuration
	static class ManualSchedulerConfiguration {
		@Bean
		SchedulerFactoryBean quartzScheduler() {
			return new SchedulerFactoryBean();
		}
	}

	@Configuration
	protected static class MultipleDataSourceConfiguration extends BaseQuartzConfiguration {
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
	protected static class MultipleTransactionManagersConfiguration extends BaseQuartzConfiguration {

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

	@Configuration
	static class CustomQuartzDatabaseInitializerConfiguration {
		@Bean
		QuartzDatabaseInitializer customInitializer(DataSource dataSource,
				ResourceLoader resourceLoader, QuartzProperties properties) {
			return new QuartzDatabaseInitializer(dataSource, resourceLoader, properties);
		}
	}

	@Configuration
	static class CustomDatabaseInitializerConfiguration {
		@Bean
		QuartzDatabaseInitializer customInitializer(
				DataSource dataSource, ResourceLoader resourceLoader) {
			return new QuartzDatabaseInitializer(dataSource, resourceLoader, new QuartzProperties());
		}
	}

	static class ComponentThatUsesScheduler {
		ComponentThatUsesScheduler(Scheduler scheduler) {
			Assert.assertNotNull("Scheduler must not be null", scheduler);
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

	/**
	 * spring-boot-1.5.x use {@link org.springframework.core.convert.support.DefaultConversionService} as bean 'conversionService',
	 * which cannot converter {@link java.time.Duration} etc.
	 * {@link org.springframework.format.support.DefaultFormattingConversionService} should be used.
	 */
	@Configuration
	static class ConversionServiceConfiguration {
		@Bean
		public FactoryBean<FormattingConversionService> conversionService() {
			return new FormattingConversionServiceFactoryBean();
		}
	}
}