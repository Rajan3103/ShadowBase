package com.shadowbase.backend.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shadowbase.backend.Entity.Container;

import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Repository.ContainerRepository;

@Service
public class PostgresContainerService {

    @Autowired
    private ContainerRepository containerRepository;

    public Container createContainer(Sandbox sandbox, String databaseName, String userName, String password) {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:latest")
        .withDatabaseName(databaseName)
        .withUsername(userName)
        .withPassword(password);

        container.start();

        Container containerObject = new Container();
        containerObject.setId(container.getContainerId());
        containerObject.setSandbox(sandbox);
        containerObject.setImageName(container.getDockerImageName());

        containerObject.setJdbcUrl(container.getJdbcUrl());
        containerObject.setUsername(container.getUsername());
        containerObject.setPassword(container.getPassword());

        containerObject.setDatabaseType(DatabaseType.POSTGRESQL);
        containerObject.setDataSource(null);
        containerObject.setDatabaseName(container.getDatabaseName());
        containerObject.setHost(container.getHost());
        containerObject.setPort(container.getFirstMappedPort());
        containerObject.setStatus(ContainerStatus.RUNNING);

     containerRepository.save(containerObject);

     return containerObject;


    }
}
