package com.shadowbase.backend.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shadowbase.backend.Entity.Container;

import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Repository.ContainerRepository;
public class PostgresContainerService {

    @Autowired
    ContainerRepository containerRepository;
    Container createContainer(Sandbox sandbox,String databaseName, String userName,String password){
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:latest")
        .withDatabaseName(databaseName)
        .withUsername(userName)
        .withPassword(password);

        Container containerObject = new Container();
        containerObject.setId(container.getContainerId());
        containerObject.setSandbox(sandbox);
        containerObject.setImageName(container.getDockerImageName());

        containerObject.setJdbcUrl(container.getJdbcUrl());
        containerObject.setUsername(container.getUsername());
        containerObject.setPassword(container.getPassword());

        containerObject.setDatabaseName(container.getDatabaseName());
        containerObject.setHost(container.getHost());
        containerObject.setPort(container.getFirstMappedPort());
        containerObject.setStatus(ContainerStatus.RUNNING);

     containerRepository.save(containerObject);

     return containerObject;


    }
}
