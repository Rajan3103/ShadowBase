package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * JPA entity representing a user account in ShadowBase.
 * <p>
 * Holds user authentication and identity credentials, and acts as the owner
 * for provisioned sandboxes and data source connections.
 */
@Entity
@Data
@Table(name = "users")
public class User {

    /**
     * Primary unique identifier for the user account.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique email address used as username/login identifier.
     */
    @Column(name = "email_id", nullable = false, unique = true)
    private String emailId;

    /**
     * Hashed user account password.
     */
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Timestamp when the user account was registered.
     */
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createAt;

    /**
     * Timestamp when the user account details were last updated.
     */
    @UpdateTimestamp
    @Column(nullable = true)
    private LocalDateTime updatedAt;
}
