package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "containers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sandbox", "dataSource"})
@EqualsAndHashCode(exclude = {"sandbox", "dataSource"})
public class Container {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", referencedColumnName = "id")
    private Sandbox sandbox;

    @Column(name = "image_name")
    private String imageName;

    private String host;

    private Integer port;

    @Column(name = "jdbc_url", length = 1024)
    private String jdbcUrl;

    private String username;

    private String password;

    @Column(name = "credential_reference")
    private String credentialReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id", referencedColumnName = "id")
    private DataSource dataSource;

    @Column(name = "database_name")
    private String databaseName;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type")
    private DatabaseType databaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContainerStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Column(name = "stopped_at", nullable = true)
    private LocalDateTime stoppedAt;

    public enum DatabaseType {
        MYSQL, POSTGRESQL
    }

    public enum ContainerStatus {
        CREATING, STARTING, RUNNING, UNHEALTHY, STOPPING, STOPPED, FAILED, REMOVED
    }
}
