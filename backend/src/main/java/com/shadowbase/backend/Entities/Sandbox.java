package com.shadowbase.backend.Entities;

import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity 
@Table(name="sandbox")
public class Sandbox {
    //this class holds the sandbox details and is having foreign keys to datasource,user
    //and container.

    private UUID id;

    @Column(name="sandbox_name",nullable = false)
    private String sandboxName;

    @ManyToOne
    @JoinColumn(name = "data_source_id",referencedColumnName = "id")
    private DataSource dataSource;

    @ManyToOne
    @JoinColumn(name="user_id",referencedColumnName = "id")
    private User user;

    @ManyToOne
    @JoinColumn(name="container_id",referencedColumnName = "id")
    private Container container;

    @Column(name = "database_type")
    private DatabaseType databaseType;


    @Column(name = "status",nullable = false)
    private Status status;

   @CreationTimestamp
   @Column(nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "expires_at",nullable=true)
    private LocalDateTime expiresAt;

    @Column(name = "stopped_at",nullable = true)
    private LocalDateTime stoppedAt;

    private enum DatabaseType{
        MYSQL,POSTGRESQL
    }

    private enum Status{
        CREATED,RUNNING,STOPPED,FAILED
    }
}
