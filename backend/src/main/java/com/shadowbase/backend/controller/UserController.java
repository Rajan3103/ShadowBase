package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.CreateUserRequest;
import com.shadowbase.backend.dto.UserResponse;
import com.shadowbase.backend.service.UserService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing user management endpoints.
 * <p>
 * Base URI: {@code /api/users}
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists all registered user accounts.
     *
     * @return 200 OK with list of UserResponse objects
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    /**
     * Retrieves user details by ID.
     *
     * @param id UUID of the user
     * @return 200 OK with UserResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Creates a new user account.
     *
     * @param request creation parameters
     * @return 201 CREATED with the provisioned UserResponse
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        log.info("REST request to create user: {}", request != null ? request.getEmailId() : "null");
        UserResponse response = userService.createUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Gets or seeds the default demo user account.
     *
     * @return 200 OK with demo UserResponse
     */
    @GetMapping("/demo")
    public ResponseEntity<UserResponse> getDemoUser() {
        return ResponseEntity.ok(userService.getOrCreateDemoUser());
    }
}
