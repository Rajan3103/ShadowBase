package com.shadowbase.backend.Service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Repository.SandboxRepository;

public class SanboxService {
    @Autowired
    PostgresContainerService postgresContainerService;

    @Autowired
    SandboxRepository sandboxRepository;
    Sandbox createSandbox(String sandboxName,User user){
        Sandbox sandbox = new Sandbox();
        sandbox.setName(sandboxName);
        sandbox.setUser(user);
        sandbox.setStatus(SandboxStatus.CREATED);

        return sandbox;
    }

    void addPostgresContainer(UUID sandboxId,String databaseName,String userName,String password){
        Sandbox sandbox = sandboxRepository.findById(sandboxId).orElse(null);
  Container containerObject = postgresContainerService.createContainer(sandbox,databaseName, userName, password);


  sandbox.getContainers().add(containerObject);
    }
}
