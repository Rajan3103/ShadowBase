package com.shadowbase.backend.Service;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Repository.SandboxRepository;

@Service
public class SanboxService {
    @Autowired
    private PostgresContainerService postgresContainerService;

    @Autowired
    private SandboxRepository sandboxRepository;

    public Sandbox createSandbox(String sandboxName, User user) {
        Sandbox sandbox = new Sandbox();
        sandbox.setName(sandboxName);
        sandbox.setUser(user);
        sandbox.setStatus(SandboxStatus.CREATED);

        return sandboxRepository.save(sandbox);
    }

    @Transactional
    public Container addPostgresContainer(UUID sandboxId, String databaseName, String userName, String password) {
        Sandbox sandbox = sandboxRepository.findById(sandboxId)
            .orElseThrow(() -> new IllegalArgumentException("Sandbox not found with id: " + sandboxId));
        Container containerObject = postgresContainerService.createContainer(sandbox, databaseName, userName, password);

        if (sandbox.getContainers() == null) {
            sandbox.setContainers(new ArrayList<>());
        }
        sandbox.getContainers().add(containerObject);

        return containerObject;
    }
}
