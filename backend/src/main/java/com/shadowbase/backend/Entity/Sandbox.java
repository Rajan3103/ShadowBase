package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing an isolated ShadowBase Sandbox environment.
 * <p>
 * A Sandbox acts as a logical workspace for developer testing and data virtualization. It manages:
 * <ul>
 *   <li>A collection of database containers (e.g., PostgreSQL, MySQL) provisioned for this environment.</li>
 *   <li>The owning user who created and manages the sandbox.</li>
 *   <li>The overall aggregate lifecycle state of the environment ({@link SandboxStatus}).</li>
 *   <li>The aggregated schema initialization status ({@link SchemaStatus}) across its containers.</li>
 * </ul>
 * <p>
 * <b>Important Architecture Note:</b> DataSources are linked exclusively to individual
 * {@link Container} entities running the respective database. A Sandbox is completely
 * independent of DataSource configurations.
 */
@Entity
@Table(name="sandbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"containers", "user"})
@EqualsAndHashCode(exclude = {"containers", "user"})
public class Sandbox {

    /**
     * Primary unique identifier for the Sandbox entity (UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User-defined human-readable name of the sandbox environment.
     */
    @Column(name="sandbox_name", nullable = false)
    private String name;

    /**
     * List of database containers associated with this sandbox.
     * Managed via a 1-to-many relationship with {@link Container}.
     */
    @Builder.Default
    @OneToMany(mappedBy = "sandbox", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Container> containers = new ArrayList<>();

    /**
     * The User who owns this sandbox environment.
     */
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    /**
     * Aggregated runtime status of this sandbox environment.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SandboxStatus status;

    /**
     * Schema initialization status of this sandbox environment.
     * Managed independently from the sandbox lifecycle status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schema_status")
    @Builder.Default
    private SchemaStatus schemaStatus = SchemaStatus.NOT_INITIALIZED;

    /**
     * Creation timestamp automatically populated by Hibernate.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last update timestamp automatically updated on modifications.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lifecycle statuses representing the aggregate health and lifecycle of the sandbox.
     */
    public enum SandboxStatus {
        /** Environment is currently being provisioned. */
        CREATING,
        /** Containers are starting up. */
        STARTING,
        /** All required containers are healthy and reachable. */
        READY,
        /** Some containers are running, but at least one failed or is unhealthy. */
        DEGRADED,
        /** Containers are shutting down. */
        STOPPING,
        /** All containers are stopped cleanly. */
        STOPPED,
        /** All containers failed to start or encountered errors. */
        FAILED,
        /** Containers have been decommissioned and removed; metadata preserved. */
        DESTROYED
    }
}
