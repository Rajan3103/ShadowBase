
package com.shadowbase.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.shadowbase.backend.dto.EnvironmentResponse;
import com.shadowbase.backend.service.MySQLContainerService;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final MySQLContainerService containerService;

    public EnvironmentController(MySQLContainerService containerService) {
        this.containerService = containerService;
    }

    // Create a new MySQL environment
    @PostMapping
    public ResponseEntity<EnvironmentResponse> createEnvironment() {
        EnvironmentResponse response =
                containerService.createEnvironment();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get environment details
    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentResponse> getEnvironment(
            @PathVariable String id) {

        EnvironmentResponse response =
                containerService.getEnvironment(id);

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Environment not found");
        }

        return ResponseEntity.ok(response);
    }

    // Stop an environment
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> stopEnvironment(
            @PathVariable String id) {

        boolean stopped = containerService.stopEnvironment(id);

        if (!stopped) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Environment not found");
        }

        return ResponseEntity.noContent().build();
    }
}
