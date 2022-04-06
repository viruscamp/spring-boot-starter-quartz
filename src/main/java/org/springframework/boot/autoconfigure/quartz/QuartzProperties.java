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

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Quartz Scheduler integration.
 *
 * @author Vedran Pavic
 * @since 2.0.0
 */
@ConfigurationProperties("spring.quartz")
public class QuartzProperties {

	private static final String DEFAULT_SCHEMA_LOCATION = "classpath:org/quartz/impl/"
			+ "jdbcjobstore/tables_@@platform@@.sql";

	private final Initializer initializer = new Initializer();

	/**
	 * Additional Quartz Scheduler properties.
	 */
	private Map<String, String> properties = new HashMap<String, String>();

	/**
	 * Path to the SQL file to use to initialize the database schema.
	 */
	private String schema = DEFAULT_SCHEMA_LOCATION;

	public Initializer getInitializer() {
		return this.initializer;
	}

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

	public String getSchema() {
		return this.schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public class Initializer {

		/**
		 * Create the required Quartz Scheduler tables on startup if necessary. Enabled
		 * automatically if the schema is configured.
		 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return this.enabled && QuartzProperties.this.getSchema() != null;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

}