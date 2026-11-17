package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.UserRepository;
import com.shadowbase.backend.dto.CreateUserRequest;
import com.shadowbase.backend.dto.UserResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link UserService}.
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    public static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String DEMO_USER_EMAIL = "demo@shadowbase.local";

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return List.of(getOrCreateDemoUser());
        }
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return toResponse(user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request == null || request.getEmailId() == null || request.getEmailId().trim().isEmpty()) {
            throw new IllegalArgumentException("Email address must not be blank");
        }

        String email = request.getEmailId().trim().toLowerCase();

        // Return existing user if email is already registered
        return userRepository.findByEmailId(email)
                .map(this::toResponse)
                .orElseGet(() -> {
                    String password = (request.getPassword() != null && !request.getPassword().isBlank())
                            ? request.getPassword()
                            : "shadowbase";

                    User newUser = User.builder()
                            .emailId(email)
                            .password(password)
                            .build();

                    User saved = userRepository.save(newUser);
                    log.info("Created new user: {} (ID: {})", saved.getEmailId(), saved.getId());
                    return toResponse(saved);
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public UserResponse getOrCreateDemoUser() {
        return userRepository.findById(DEMO_USER_ID)
                .map(this::toResponse)
                .orElseGet(() -> {
                    User demoUser = User.builder()
                            .id(DEMO_USER_ID)
                            .emailId(DEMO_USER_EMAIL)
                            .password("demo123")
                            .build();
                    User saved = userRepository.save(demoUser);
                    log.info("Provisioned default demo user: {} (ID: {})", saved.getEmailId(), saved.getId());
                    return toResponse(saved);
                });
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .emailId(user.getEmailId())
                .createdAt(user.getCreateAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
