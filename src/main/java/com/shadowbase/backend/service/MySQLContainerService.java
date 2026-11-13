
package com.shadowbase.backend.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.shadowbase.backend.dto.EnvironmentResponse;

@Service
public class MySQLContainerService {

    private final Map<String, MySQLContainer> containers =
            new ConcurrentHashMap<>();

    public EnvironmentResponse createEnvironment() {

        MySQLContainer container = new MySQLContainer(
                DockerImageName.parse("mysql:8.4")
        );

        container.start();

        String id = UUID.randomUUID().toString();
        containers.put(id, container);

        return toResponse(id, container);
    }

    public EnvironmentResponse getEnvironment(String id) {

        MySQLContainer container = containers.get(id);

        if (container == null) {
            return null;
        }

        return toResponse(id, container);
    }

    public boolean stopEnvironment(String id) {

        MySQLContainer container = containers.remove(id);

        if (container == null) {
            return false;
        }

        container.stop();
        return true;
    }

    private EnvironmentResponse toResponse(
            String id, MySQLContainer container) {

        String status = container.isRunning()
                ? "RUNNING"
                : "STOPPED";

        return new EnvironmentResponse(
                id,
                status,
                container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword()
        );
    }
}
