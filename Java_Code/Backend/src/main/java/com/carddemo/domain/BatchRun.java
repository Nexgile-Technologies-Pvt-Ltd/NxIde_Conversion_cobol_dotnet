package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Durable batch run record. Replaces the legacy JES job/return-code trail so every batch command
 * has the {@code BatchRunId}, counts and outcome required by the restart policy (FR-BATCH-017).
 */
@Entity
@Table(name = "batch_run")
@Getter
@Setter
@NoArgsConstructor
public class BatchRun {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Legacy job name such as {@code POSTTRAN}, {@code INTCALC}, {@code TRANREPT}. */
    @Column(name = "job_name", length = 40, nullable = false)
    private String jobName;

    @Column(name = "parameters", columnDefinition = "text")
    private String parameters;

    @Column(name = "status", length = 20, nullable = false)
    private String status = STATUS_RUNNING;

    /** COBOL completion code: 0 clean, 4 when any record was rejected. */
    @Column(name = "return_code", nullable = false)
    private int returnCode;

    @Column(name = "records_read", nullable = false)
    private int recordsRead;

    @Column(name = "records_accepted", nullable = false)
    private int recordsAccepted;

    @Column(name = "records_rejected", nullable = false)
    private int recordsRejected;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "started_by", length = 8)
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
