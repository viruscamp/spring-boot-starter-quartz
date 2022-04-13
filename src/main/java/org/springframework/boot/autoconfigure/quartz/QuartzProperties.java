/*
 * Copyright 2012-2021 the original author or authors.
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

import org.joda.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;

/**
 * Configuration properties for the Quartz Scheduler integration.
 *
 * @author Vedran Pavic
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@ConfigurationProperties("spring.quartz")
public class QuartzProperties {

	/**
	 * Quartz job store type.
	 * different behavior with spring-boot-2.x, `job-store-type=jdbc` without data-source will fail
	 */
	private JobStoreType jobStoreType = JobStoreType.MEMORY;

	/**
	 * Name of the scheduler.
	 * Because there is a bug in spring-context-support-4.x {@link org.springframework.scheduling.quartz.SchedulerFactoryBean},
	 * "spring.quartz.properties.org.quartz.scheduler.instanceName=testScheduler" is invalid.
	 */
	private String schedulerName;

	/**
	 * Whether to automatically start the scheduler after initialization.
	 */
	private boolean autoStartup = true;

	/**
	 * Delay after which the scheduler is started once initialization completes. Setting
	 * this property makes sense if no jobs should be run before the entire application
	 * has started up.
	 * In spring-boot-1.5.x, `Duration` simple format like '1m', '3s' is not supported.
	 * Only ISO-8601 format like 'PT1M', 'P2D' is supported.
	 */
	private Duration startupDelay = Duration.ZERO;

	/**
	 * Whether to wait for running jobs to complete on shutdown.
	 */
	private boolean waitForJobsToCompleteOnShutdown = false;

	/**
	 * Whether configured jobs should overwrite existing job definitions.
	 */
	private boolean overwriteExistingJobs = false;

	/**
	 * Additional Quartz Scheduler properties.
	 */
	private final Map<String, String> properties = new HashMap<String, String>();

	private final Jdbc jdbc = new Jdbc();

	public JobStoreType getJobStoreType() {
		return this.jobStoreType;
	}

	public void setJobStoreType(JobStoreType jobStoreType) {
		this.jobStoreType = jobStoreType;
	}

	public String getSchedulerName() {
		return this.schedulerName;
	}

	public void setSchedulerName(String schedulerName) {
		this.schedulerName = schedulerName;
	}

	public boolean isAutoStartup() {
		return this.autoStartup;
	}

	public void setAutoStartup(boolean autoStartup) {
		this.autoStartup = autoStartup;
	}

	public Duration getStartupDelay() {
		return this.startupDelay;
	}

	public void setStartupDelay(Duration startupDelay) {
		this.startupDelay = startupDelay;
	}

	public boolean isWaitForJobsToCompleteOnShutdown() {
		return this.waitForJobsToCompleteOnShutdown;
	}

	public void setWaitForJobsToCompleteOnShutdown(boolean waitForJobsToCompleteOnShutdown) {
		this.waitForJobsToCompleteOnShutdown = waitForJobsToCompleteOnShutdown;
	}

	public boolean isOverwriteExistingJobs() {
		return this.overwriteExistingJobs;
	}

	public void setOverwriteExistingJobs(boolean overwriteExistingJobs) {
		this.overwriteExistingJobs = overwriteExistingJobs;
	}

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public Jdbc getJdbc() {
		return this.jdbc;
	}

	public static class Jdbc {

		private static final String DEFAULT_SCHEMA_LOCATION = "classpath:org/quartz/impl/"
				+ "jdbcjobstore/tables_@@platform@@.sql";

		/**
		 * Path to the SQL file to use to initialize the database schema.
		 */
		private String schema = DEFAULT_SCHEMA_LOCATION;

		/**
		 * Platform to use in initialization scripts if the @@platform@@ placeholder is
		 * used. Auto-detected by default.
		 */
		private String platform;

		/**
		 * Database schema initialization mode.
		 */
		private DatabaseInitializationMode initializeSchema = DatabaseInitializationMode.EMBEDDED;

		/**
		 * Prefixes for single-line comments in SQL initialization scripts.
		 * In spring-boot-1.5.x, accepts a list of string, but only use the first one.
		 */
		private List<String> commentPrefix = new ArrayList<String>(Arrays.asList("#", "--"));

		public String getSchema() {
			return this.schema;
		}

		public void setSchema(String schema) {
			this.schema = schema;
		}

		public String getPlatform() {
			return this.platform;
		}

		public void setPlatform(String platform) {
			this.platform = platform;
		}

		public DatabaseInitializationMode getInitializeSchema() {
			return this.initializeSchema;
		}

		public void setInitializeSchema(DatabaseInitializationMode initializeSchema) {
			this.initializeSchema = initializeSchema;
		}

		public List<String> getCommentPrefix() {
			return this.commentPrefix;
		}

		public void setCommentPrefix(List<String> commentPrefix) {
			this.commentPrefix = commentPrefix;
		}

	}

}
