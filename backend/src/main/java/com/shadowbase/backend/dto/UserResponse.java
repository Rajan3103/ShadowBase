package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload representing a user account.
 * Passwords and sensitive credentials are intentionally excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /**
     * Unique identifier for the user.
     */
    private UUID id;

    /**
     * User's registered email address.
     */
    private String emailId;

    /**
     * Account registration timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Account last update timestamp.
     */
    private LocalDateTime updatedAt;
}
