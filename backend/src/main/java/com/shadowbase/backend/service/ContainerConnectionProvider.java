package com.shadowbase.backend.service;

import java.sql.Connection;
import java.sql.SQLException;

import com.shadowbase.backend.Entity.Container;

/**
 * Dedicated internal abstraction for obtaining live JDBC connections
 * to database containers managed by ShadowBase.
 * <p>
 * Decouples SchemaService from container provisioning lifecycles while ensuring
 * credentials remain isolated within backend services and never exposed to REST layers.
 */
public interface ContainerConnectionProvider {

    /**
     * Opens a direct JDBC {@link Connection} to the database inside the target container.
     *
     * @param containerId unique Docker container ID
     * @return active, open JDBC connection
     * @throws SQLException if a database access error occurs or container cannot be reached
     */
    Connection openConnection(String containerId) throws SQLException;

    /**
     * Opens a direct JDBC {@link Connection} to the database using the provided container metadata.
     *
     * @param container the Container entity containing host, port, credentials, and JDBC URL
     * @return active, open JDBC connection
     * @throws SQLException if a database access error occurs or credentials/network are invalid
     */
    Connection openConnection(Container container) throws SQLException;

    /**
     * Tests whether the database inside the container is fully initialized and ready
     * to accept client connections and execute queries.
     *
     * @param container the Container entity to probe
     * @return true if connection can be established and responds to validation checks; false otherwise
     */
    boolean isDatabaseReady(Container container);
}
