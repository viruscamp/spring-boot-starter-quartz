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

// copy from org.springframework.boot.autoconfigure.quartz.QuartzDataSourceInitializerTests
// in spring-boot-autoconfigure-commit-c406dda18160a29649eef6bbb73f3c93674f4028
package org.springframework.boot.autoconfigure.quartz;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link QuartzDatabaseInitializer}.
 *
 * @author Stephane Nicoll
 */
public class QuartzDatabaseInitializerTests {
	private AnnotationConfigApplicationContext context;

	public QuartzDatabaseInitializerTests() {
		context = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(context,
			"spring.datasource.url=" + String.format(
						"jdbc:h2:mem:test-%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", UUID.randomUUID()));
		context.register(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class);
	}

	@After
	public void closeContext() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void hashIsUsedAsACommentPrefixByDefault() {
		EnvironmentTestUtils.addEnvironment(context,
				"spring.quartz.jdbc.schema=classpath:org/springframework/boot/autoconfigure/quartz/tables_#_comments.sql");
		registerAndRefresh(TestConfiguration.class);
		assertThatDatabaseHasBeenInitialized();
	}

	@Test
	public void doubleDashIsUsedAsACommentPrefixByDefault() {
		EnvironmentTestUtils.addEnvironment(context,
				"spring.quartz.jdbc.schema=classpath:org/springframework/boot/autoconfigure/quartz/tables_--_comments.sql");
		registerAndRefresh(TestConfiguration.class);
		assertThatDatabaseHasBeenInitialized();
	}

	@Test
	public void commentPrefixCanBeCustomized() {
		EnvironmentTestUtils.addEnvironment(context,
				"spring.quartz.jdbc.comment-prefix=**",
				"spring.quartz.jdbc.schema=classpath:org/springframework/boot/autoconfigure/quartz/tables_custom_comment_prefix.sql");
		registerAndRefresh(TestConfiguration.class);
		assertThatDatabaseHasBeenInitialized();
	}

	private void assertThatDatabaseHasBeenInitialized() {
		QuartzDatabaseInitializer quartzDatabaseInitializer = context.getBean(QuartzDatabaseInitializer.class);
		assertThat(quartzDatabaseInitializer).isNotNull();
		assertEquals(quartzDatabaseInitializer.isEnabled(), true);
		assertEquals(quartzDatabaseInitializer.isEmbeddedDatabase(), true);

		JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
		assertThat(jdbcTemplate).isNotNull();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM QRTZ_TEST_TABLE", Integer.class)).isEqualTo(0);
	}

	private void registerAndRefresh(Class<?>... annotatedClasses) {
		this.context.register(annotatedClasses);
		this.context.refresh();
	}

	@Configuration
	@Scope(proxyMode = ScopedProxyMode.NO)
	@EnableConfigurationProperties(QuartzProperties.class)
	static class TestConfiguration {

		@Bean
		QuartzDatabaseInitializer initializer(DataSource dataSource, ResourceLoader resourceLoader,
				QuartzProperties properties) {
			return new QuartzDatabaseInitializer(dataSource, resourceLoader, properties);
		}

	}

}
