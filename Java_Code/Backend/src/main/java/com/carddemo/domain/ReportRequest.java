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
 * Durable transaction-report request. Replaces the legacy {@code CORPT00C} behaviour of writing
 * 80-byte JCL lines to transient data queue {@code JOBS} (FR-RPT-003): no user supplied value ever
 * becomes executable job text.
 */
@Entity
@Table(name = "report_request")
@Getter
@Setter
@NoArgsConstructor
public class ReportRequest {

    public static final String TYPE_MONTHLY = "MONTHLY";
    public static final String TYPE_YEARLY = "YEARLY";
    public static final String TYPE_CUSTOM = "CUSTOM";

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "report_type", length = 10, nullable = false)
    private String reportType;

    @Column(name = "start_date", length = 10, nullable = false)
    private String startDate;

    @Column(name = "end_date", length = 10, nullable = false)
    private String endDate;

    @Column(name = "status", length = 12, nullable = false)
    private String status = STATUS_SUBMITTED;

    @Column(name = "requested_by", length = 8, nullable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    /** The rendered 133-column report produced by the {@code CBTRN03C} equivalent. */
    @Column(name = "content", columnDefinition = "text")
    private String content;
}
