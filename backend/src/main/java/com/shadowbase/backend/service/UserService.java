package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.dto.CreateUserRequest;
import com.shadowbase.backend.dto.UserResponse;

/**
 * Service managing user accounts for ShadowBase.
 */
public interface UserService {

    /**
     * Lists all registered user accounts.
     *
     * @return list of UserResponse objects
     */
    List<UserResponse> listUsers();

    /**
     * Retrieves user details by ID.
     *
     * @param id UUID of the user
     * @return UserResponse
     */
    UserResponse getUserById(UUID id);

    /**
     * Creates a new user account or returns existing if email matches.
     *
     * @param request user creation details
     * @return UserResponse
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Retrieves or automatically seeds the default demo user account.
     * Guaranteed to match the DEMO_USER_ID expected by the frontend.
     *
     * @return UserResponse for the demo user
     */
    UserResponse getOrCreateDemoUser();
}
