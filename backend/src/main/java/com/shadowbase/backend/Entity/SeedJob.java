package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity representing a persistent background data seeding job.
 * <p>
 * Manages the end-to-end lifecycle of transferring records from a source {@link DataSource}
 * into the isolated database {@link Container} belonging to a {@link Sandbox}.
 */
@Entity
@Table(name = "seed_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sandbox", "container", "sourceDataSource", "tableProgressList"})
@EqualsAndHashCode(exclude = {"sandbox", "container", "sourceDataSource", "tableProgressList"})
public class SeedJob {

    /**
     * Unique identifier for the seed job (UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Target sandbox environment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sandbox_id", nullable = false)
    private Sandbox sandbox;

    /**
     * Target database container inside the sandbox.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id", nullable = false)
    private Container container;

    /**
     * Source database connection configuration from which data is streamed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id", nullable = false)
    private DataSource sourceDataSource;

    /**
     * Current lifecycle status of the seeding job.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeedJobStatus status;

    /**
     * Scope mode: FULL_DATABASE or SELECTED_TABLES.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "seeding_mode", nullable = false)
    private SeedingMode mode;

    /**
     * Source read consistency mode: SNAPSHOT or STANDARD.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "consistency_mode", nullable = false)
    private ConsistencyMode consistencyMode;

    /**
     * Total number of tables planned for transfer.
     */
    @Builder.Default
    @Column(name = "total_tables")
    private Integer totalTables = 0;

    /**
     * Number of tables successfully transferred so far.
     */
    @Builder.Default
    @Column(name = "completed_tables")
    private Integer completedTables = 0;

    /**
     * Running total count of rows read from the source database.
     */
    @Builder.Default
    @Column(name = "total_rows_read")
    private Long totalRowsRead = 0L;

    /**
     * Running total count of rows successfully inserted and committed into target tables.
     */
    @Builder.Default
    @Column(name = "total_rows_inserted")
    private Long totalRowsInserted = 0L;

    /**
     * Timestamp when the seed job was registered.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the worker claimed and began executing the job.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * Timestamp when the job completed, failed, or was cancelled.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Sanitized error summary if the seeding job failed.
     */
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    /**
     * Identifier of the user who requested the job, if known.
     */
    @Column(name = "created_by")
    private String createdBy;

    /**
     * Ordered list of individual table progress records for this job.
     */
    @Builder.Default
    @OneToMany(mappedBy = "seedJob", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC")
    private List<SeedTableProgress> tableProgressList = new ArrayList<>();
}
