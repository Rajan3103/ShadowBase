package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Exception.DataSeedingException;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC implementation of {@link SourceConnectionProvider}.
 * <p>
 * Connects directly to external production or staging databases using the host, port,
 * database name, and credentials configured in {@link DataSource}.
 * Configures connections in read-only mode to prevent inadvertent source modifications.
 */
@Slf4j
@Component
public class JdbcSourceConnectionProvider implements SourceConnectionProvider {

    @Override
    public Connection openSourceConnection(DataSource dataSource, DatabaseType databaseType) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        if (databaseType == null) {
            throw new IllegalArgumentException("DatabaseType must not be null");
        }

        String jdbcUrl = buildJdbcUrl(dataSource, databaseType);
        log.debug("Opening read-only source connection to {} ({})", dataSource.getConnectionName(), jdbcUrl);

        Properties props = new Properties();
        if (dataSource.getDatabaseUsername() != null && !dataSource.getDatabaseUsername().isBlank()) {
            props.setProperty("user", dataSource.getDatabaseUsername());
        }

        String password = resolvePassword(dataSource);
        if (password != null && !password.isBlank()) {
            props.setProperty("password", password);
        }

        // Network timeouts to prevent hanging threads
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "30000");

        DriverManager.setLoginTimeout(10);
        Connection conn = DriverManager.getConnection(jdbcUrl, props);

        try {
            conn.setReadOnly(true);
        } catch (SQLException e) {
            log.warn("Could not set read-only flag on source connection: {}", e.getMessage());
        }

        return conn;
    }

    @Override
    public boolean isSourceDatabaseReady(DataSource dataSource, DatabaseType databaseType) {
        if (dataSource == null || databaseType == null) {
            return false;
        }

        try (Connection conn = openSourceConnection(dataSource, databaseType)) {
            return conn != null && conn.isValid(5);
        } catch (Exception e) {
            log.debug("Source database reachability check failed for {}: {}", dataSource.getConnectionName(), e.getMessage());
            return false;
        }
    }

    /**
     * Constructs the database-specific JDBC connection URL.
     */
    public String buildJdbcUrl(DataSource ds, DatabaseType dbType) {
        if (ds.getHost() == null || ds.getHost().isBlank()) {
            throw new DataSeedingException("Source DataSource host is required");
        }
        int port = (ds.getPort() != null && ds.getPort() > 0)
                ? ds.getPort()
                : (dbType == DatabaseType.POSTGRESQL ? 5432 : 3306);
        String dbName = (ds.getDatabaseName() != null) ? ds.getDatabaseName() : "";

        return switch (dbType) {
            case POSTGRESQL -> "jdbc:postgresql://" + ds.getHost() + ":" + port + "/" + dbName;
            case MYSQL -> "jdbc:mysql://" + ds.getHost() + ":" + port + "/" + dbName
                    + "?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false";
        };
    }

    private String resolvePassword(DataSource ds) {
        if (ds.getDatabasePassword() != null && !ds.getDatabasePassword().isBlank()) {
            return ds.getDatabasePassword();
        }
        return ds.getCredentialReference();
    }
}
