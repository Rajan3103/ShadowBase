package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.SQLException;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.DataSource;

/**
 * Dedicated internal abstraction for establishing live JDBC connections to external
 * source databases defined by {@link DataSource} configurations.
 */
public interface SourceConnectionProvider {

    /**
     * Opens a direct JDBC {@link Connection} to the upstream source database.
     * Configured for read-only access where supported.
     *
     * @param dataSource   the DataSource configuration holding host, port, DB name, and credentials
     * @param databaseType the engine type (POSTGRESQL or MYSQL)
     * @return active, open JDBC connection
     * @throws SQLException if connection fails or credentials/network are unreachable
     */
    Connection openSourceConnection(DataSource dataSource, DatabaseType databaseType) throws SQLException;

    /**
     * Tests whether the source database is currently reachable and responding to queries.
     *
     * @param dataSource   the DataSource configuration to test
     * @param databaseType the engine type
     * @return true if connection can be established and responds within timeout; false otherwise
     */
    boolean isSourceDatabaseReady(DataSource dataSource, DatabaseType databaseType);
}
