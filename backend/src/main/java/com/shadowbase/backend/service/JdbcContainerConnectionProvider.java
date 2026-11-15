package com.shadowbase.backend.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Exception.ContainerOperationException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC-based implementation of {@link ContainerConnectionProvider}.
 * <p>
 * Connects directly to database instances running inside Testcontainers Docker instances
 * using dynamically mapped ports, persisted JDBC URLs, and administrative credentials.
 * Does not depend on keeping in-memory Testcontainers Java objects.
 */
@Slf4j
@Component
public class JdbcContainerConnectionProvider implements ContainerConnectionProvider {

    private final ContainerRepository containerRepository;

    public JdbcContainerConnectionProvider(ContainerRepository containerRepository) {
        this.containerRepository = containerRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection openConnection(String containerId) throws SQLException {
        Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found with ID: " + containerId));
        return openConnection(container);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection openConnection(Container container) throws SQLException {
        if (container == null) {
            throw new IllegalArgumentException("Container metadata must not be null");
        }

        if (container.getStatus() != ContainerStatus.RUNNING) {
            throw new ContainerOperationException(
                    "Cannot connect to container " + container.getId() + " because its status is " + container.getStatus());
        }

        String jdbcUrl = container.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new ContainerOperationException("Container " + container.getId() + " has no configured JDBC URL");
        }

        log.debug("Opening JDBC connection to container {} at {}", container.getId(), jdbcUrl);

        Properties props = new Properties();
        if (container.getUsername() != null) {
            props.setProperty("user", container.getUsername());
        }
        if (container.getPassword() != null) {
            props.setProperty("password", container.getPassword());
        }
        // Apply connection timeout properties for resilience
        props.setProperty("connectTimeout", "10000");
        props.setProperty("socketTimeout", "30000");

        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(jdbcUrl, props);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDatabaseReady(Container container) {
        if (container == null || container.getStatus() != ContainerStatus.RUNNING) {
            return false;
        }

        try (Connection conn = openConnection(container)) {
            return conn != null && conn.isValid(5);
        } catch (Exception e) {
            log.debug("Database readiness check failed for container {}: {}", container.getId(), e.getMessage());
            return false;
        }
    }
}
