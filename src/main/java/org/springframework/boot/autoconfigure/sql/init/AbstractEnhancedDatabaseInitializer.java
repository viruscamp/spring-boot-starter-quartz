/*
 * Copyright 2012-2019 the original author or authors.
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

// copy from spring-boot-autoconfigure-1.5.22.RELEASE:\org\springframework\boot\autoconfigure\AbstractDatabaseInitializer.java
// add features from spring-boot-autoconfigure-2.6.6:org\springframework\boot\autoconfigure\quartz\jdbc\init\DataSourceScriptDatabaseInitializer.java
// copy from spring-boot-autoconfigure-1.5.22.RELEASE:\org\springframework\boot\autoconfigure\AbstractDatabaseInitializer.java
package org.springframework.boot.autoconfigure.sql.init;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.jdbc.EmbeddedDatabaseConnection;
import org.springframework.boot.autoconfigure.quartz.QuartzDatabaseInitializer;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * Copy from {@link org.springframework.boot.autoconfigure.AbstractDatabaseInitializer},
 * Add {@link #isEmbeddedDatabase()} and {@link #customize(ResourceDatabasePopulator)}
 *
 * Base class used for database initialization.
 *
 * @author Vedran Pavic
 * @author Stephane Nicoll
 * @since 1.5.0
 */
public abstract class AbstractEnhancedDatabaseInitializer {

	private static final Log logger = LogFactory.getLog(QuartzDatabaseInitializer.class);

	private static final String PLATFORM_PLACEHOLDER = "@@platform@@";

	private final DataSource dataSource;

	private final ResourceLoader resourceLoader;

	protected AbstractEnhancedDatabaseInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
		Assert.notNull(dataSource, "DataSource must not be null");
		Assert.notNull(resourceLoader, "ResourceLoader must not be null");
		this.dataSource = dataSource;
		this.resourceLoader = resourceLoader;
	}

	public boolean isEmbeddedDatabase() {
		try {
			return EmbeddedDatabaseConnection.isEmbedded(this.dataSource);
		}
		catch (Exception ex) {
			logger.debug("Could not determine if datasource is embedded", ex);
			return false;
		}
	}

	@PostConstruct
	protected void initialize() {
		if (!isEnabled()) {
			return;
		}
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
		String schemaLocation = getSchemaLocation();
		if (schemaLocation.contains(PLATFORM_PLACEHOLDER)) {
			String platform = getDatabaseName();
			schemaLocation = schemaLocation.replace(PLATFORM_PLACEHOLDER, platform);
		}
		populator.addScript(this.resourceLoader.getResource(schemaLocation));
		populator.setContinueOnError(true);
		customize(populator);
		DatabasePopulatorUtils.execute(populator, this.dataSource);
	}

	protected abstract boolean isEnabled();

	protected abstract String getSchemaLocation();

	protected void customize(ResourceDatabasePopulator populator) {
	}

	protected String getDatabaseName() {
		try {
			String productName = JdbcUtils.commonDatabaseName(
					JdbcUtils.extractDatabaseMetaData(this.dataSource, "getDatabaseProductName").toString());
			DatabaseDriver databaseDriver = DatabaseDriver.fromProductName(productName);
			if (databaseDriver == DatabaseDriver.UNKNOWN) {
				throw new IllegalStateException("Unable to detect database type");
			}
			return databaseDriver.getId();
		}
		catch (MetaDataAccessException ex) {
			throw new IllegalStateException("Unable to detect database type", ex);
		}
	}
}
