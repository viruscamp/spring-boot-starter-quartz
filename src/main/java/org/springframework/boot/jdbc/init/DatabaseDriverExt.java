package org.springframework.boot.jdbc.init;

import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;

public class DatabaseDriverExt {
    // copy from spring-boot-2.6.6.RELEASE:\org\springframework\boot\jdbc\DatabaseDriver.java
    /**
     * Find a {@link DatabaseDriver} for the given {@code DataSource}.
     * @param dataSource data source to inspect
     * @return the database driver of {@link DatabaseDriver#UNKNOWN} if not found
     * @since 2.6.0
     */
    public static DatabaseDriver fromDataSource(DataSource dataSource) {
        try {
            Object databaseProductName = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
            String productName = JdbcUtils.commonDatabaseName(databaseProductName.toString());
            return DatabaseDriver.fromProductName(productName);
        }
        catch (Exception ex) {
            return DatabaseDriver.UNKNOWN;
        }
    }
}
