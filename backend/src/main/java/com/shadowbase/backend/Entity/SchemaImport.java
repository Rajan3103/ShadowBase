package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.shadowbase.backend.Entity.Container.DatabaseType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

/**
 * JPA entity representing the execution history of an SQL schema import
 * against a target database container in a ShadowBase sandbox.
 * <p>
 * Tracks execution state, timestamps, SHA-256 checksum of the uploaded script,
 * and error details if the import fails.
 */
@Entity
@Table(name = "schema_imports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "sandbox")
@EqualsAndHashCode(exclude = "sandbox")
public class SchemaImport {

    /**
     * Unique identifier for the schema import execution record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The parent Sandbox whose container received this schema script.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", nullable = false)
    private Sandbox sandbox;

    /**
     * Unique Docker container ID of the database instance targeted by the import.
     */
    @Column(name = "container_id", nullable = false)
    private String containerId;

    /**
     * Original filename of the uploaded SQL script.
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * Engine type of the target database (POSTGRESQL or MYSQL).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false)
    private DatabaseType databaseType;

    /**
     * Hex-encoded SHA-256 cryptographic digest of the uploaded script contents.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    /**
     * Current execution/lifecycle status of the schema import operation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SchemaStatus status;

    /**
     * Timestamp when the import execution started.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * Timestamp when the import execution completed (either success or failure).
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Sanitized error summary if execution encountered failures.
     */
    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    /**
     * Record creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Record update timestamp.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
