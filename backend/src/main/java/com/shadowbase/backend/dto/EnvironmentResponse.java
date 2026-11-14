
package com.shadowbase.backend.dto;

public record EnvironmentResponse(
        String id,
        String status,
        String jdbcUrl,
        String username,
        String password
) {
}
