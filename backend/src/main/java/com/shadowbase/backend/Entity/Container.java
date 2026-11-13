package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class Container {
   @Id
   private String id;

    @ManyToOne()
    @JoinColumn(name = "sandbox_id", referencedColumnName = "id")
    private Sandbox sandbox;

    private String imageName;

    private String host;

    private Integer port;

private String jdbcUrl;

private String username;

private String password;


      @ManyToOne
    @JoinColumn(name = "data_source_id",referencedColumnName = "id")
    private DataSource dataSource;

    private String databaseName;


    @Enumerated(EnumType.STRING)
    @Column(name = "database_type")
    private DatabaseType databaseType;


    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    private ContainerStatus status;

   @CreationTimestamp
   @Column(name="created_at",nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at",nullable=true)
    private LocalDateTime expiresAt;

    @Column(name = "stopped_at",nullable = true)
    private LocalDateTime stoppedAt;

    public enum DatabaseType{
        MYSQL,POSTGRESQL
    }
    public enum ContainerStatus{
        CREATED,RUNNING,STOPPED,FAILED
    }
}
