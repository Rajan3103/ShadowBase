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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing an external source database connection configuration.
 * <p>
 * A DataSource configuration is linked exclusively to the single database {@link Container}
 * provisioned to run or shadow it. A DataSource is completely independent of Sandbox.
 */
@Entity
@Table(name = "data_source")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "container")
@EqualsAndHashCode(exclude = "container")
public class DataSource {

    /**
     * Unique identifier for the DataSource configuration.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Logical display name for this data source connection.
     */
    @Column(name = "connection_name", nullable = false)
    private String connectionName;

    /**
     * Hostname or IP address of the source database server.
     */
    @Column(name = "host", nullable = false)
    private String host;

    /**
     * Port number where the source database is reachable.
     */
    @Column(name = "port", nullable = false)
    private Integer port;

    /**
     * Logical database name on the source server.
     */
    @Column(name = "database_name", nullable = false)
    private String databaseName;

    /**
     * Database user account for connecting to the source.
     */
    @Column(name = "database_username", nullable = false)
    private String databaseUsername;

    /**
     * Reference key or vault alias pointing to secure storage for the database password.
     */
    @Column(name = "credential_reference")
    private String credentialReference;

    /**
     * The single database container running this data source.
     * Each container hosts exactly one database, and a DataSource is linked
     * exclusively to only this container (completely independent of Sandbox).
     */
    @OneToOne(mappedBy = "dataSource")
    private Container container;

    /**
     * Timestamp when this data source record was created.
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when this data source record was last modified.
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
