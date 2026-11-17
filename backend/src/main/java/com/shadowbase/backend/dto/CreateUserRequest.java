package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new user account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    /**
     * User email address used as the login and account identifier.
     */
    private String emailId;

    /**
     * Account password. If omitted, a default password will be assigned.
     */
    private String password;
}
