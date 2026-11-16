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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Table(name="sandbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"containers", "user", "dataSources"})
@EqualsAndHashCode(exclude = {"containers", "user", "dataSources"})
public class Sandbox {
    //this class holds the sandbox details and is having foreign keys to datasource,user
    //and container.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name="sandbox_name",nullable = false)
    private String name;

    @Builder.Default
    @OneToMany(mappedBy = "sandbox", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Container> containers = new ArrayList<>();

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "user_id",referencedColumnName = "id")
    private User user;

    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(
        name = "sandbox_data_sources",
        joinColumns = @JoinColumn(name = "sandbox_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "data_source_id", referencedColumnName = "id")
    )
    @Builder.Default
    private List<DataSource> dataSources = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SandboxStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

   public enum SandboxStatus{
    CREATING, STARTING, READY, DEGRADED, STOPPING, STOPPED, FAILED, DESTROYED
   }
}
