
package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;
import com.shadowbase.backend.dto.CreateSandboxRequest;
import com.shadowbase.backend.dto.SandboxResponse;
import com.shadowbase.backend.service.SandboxService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sandboxes")
public class SandboxController {

    private final SandboxService sandboxService;

    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    @PostMapping
    public ResponseEntity<SandboxResponse> createSandbox(
            @RequestBody CreateSandboxRequest request) {

        log.info("REST request to create sandbox: {}", request.getName());

        SandboxResponse response = sandboxService.createSandbox(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SandboxResponse> getSandbox(
            @PathVariable("id") UUID id) {

        return ResponseEntity.ok(sandboxService.getSandbox(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SandboxResponse>> listSandboxesByUser(
            @PathVariable("userId") UUID userId) {

        return ResponseEntity.ok(sandboxService.listSandboxesByUser(userId));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<SandboxResponse> startSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to start sandbox: {}", id);

        return ResponseEntity.ok(sandboxService.startSandbox(id));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<SandboxResponse> stopSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to stop sandbox: {}", id);

        return ResponseEntity.ok(sandboxService.stopSandbox(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to delete sandbox: {}", id);

        sandboxService.deleteSandbox(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/containers")
    public ResponseEntity<ContainerResponse> addContainer(
            @PathVariable("id") UUID id,
            @RequestBody CreateContainerRequest request) {

        log.info("REST request to add container to sandbox: {}", id);

        ContainerResponse response =
                sandboxService.addContainerToSandbox(id, request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}/containers")
    public ResponseEntity<List<ContainerResponse>> getContainers(
            @PathVariable("id") UUID id) {

        return ResponseEntity.ok(
                sandboxService.getSandbox(id).getContainers()
        );
    }
}
