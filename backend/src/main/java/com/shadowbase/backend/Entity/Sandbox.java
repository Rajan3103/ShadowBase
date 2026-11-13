package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.cglib.core.Local;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name="sandbox")
public class Sandbox {
    //this class holds the sandbox details and is having foreign keys to datasource,user
    //and container.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name="sandbox_name",nullable = false)
    private String Name;

    @OneToMany(mappedBy = "id")
    @Column(name = "containers",nullable = true)
    private List<Container> containers;

    @ManyToOne(fetch=FetchType.LAZY)
    @Column(name = "user")
    @JoinColumn(name = "user_id",referencedColumnName = "id")
    private User user;

    @Column(name = "status")
    private SandboxStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

   public enum SandboxStatus{
    CREATED,RUNNING,DEGRADED
   }
}
