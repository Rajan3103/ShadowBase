package com.shadowbase.backend.Entity;

import java.time.LocalDateTime;
import java.util.UUID;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * JPA entity tracking the granular data transfer progress for an individual database table
 * within a parent {@link SeedJob}.
 */
@Entity
@Table(name = "seed_table_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "seedJob")
@EqualsAndHashCode(exclude = "seedJob")
public class SeedTableProgress {

    /**
     * Unique identifier for this table progress record (UUID).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Parent data seeding job.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seed_job_id", nullable = false)
    private SeedJob seedJob;

    /**
     * Name of the table being transferred.
     */
    @Column(name = "table_name", nullable = false)
    private String tableName;

    /**
     * Current transfer status of this table.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeedTableStatus status;

    /**
     * Number of rows read from the source table.
     */
    @Builder.Default
    @Column(name = "rows_read")
    private Long rowsRead = 0L;

    /**
     * Number of rows successfully inserted into the sandbox container table.
     */
    @Builder.Default
    @Column(name = "rows_inserted")
    private Long rowsInserted = 0L;

    /**
     * Timestamp when data transfer began for this table.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * Timestamp when data transfer finished for this table.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Error message if table transfer failed.
     */
    @Column(name = "error_message", length = 2048)
    private String errorMessage;
}
