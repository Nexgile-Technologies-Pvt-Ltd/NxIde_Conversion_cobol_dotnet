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
 * Daily reject record. COBOL source {@code CBTRN02C.cbl} lines 81-84 / sequential {@code DALYREJS}
 * (430 bytes = original 350-byte record + 4-byte reason code + 76-byte reason text).
 *
 * <p>Reason codes observed in the source: {@code 0100} invalid card number, {@code 0101} account
 * record not found, {@code 0102} overlimit transaction, {@code 0103} transaction received after
 * account expiration.</p>
 */
@Entity
@Table(name = "batch_reject")
@Getter
@Setter
@NoArgsConstructor
public class BatchReject {

    public static final String REASON_INVALID_CARD = "0100";
    public static final String REASON_ACCOUNT_NOT_FOUND = "0101";
    public static final String REASON_OVERLIMIT = "0102";
    public static final String REASON_EXPIRED = "0103";

    public static final String TEXT_INVALID_CARD = "INVALID CARD NUMBER FOUND";
    public static final String TEXT_ACCOUNT_NOT_FOUND = "ACCOUNT RECORD NOT FOUND";
    public static final String TEXT_OVERLIMIT = "OVERLIMIT TRANSACTION.";
    public static final String TEXT_EXPIRED = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_run_id", nullable = false)
    private Long batchRunId;

    @Column(name = "record_number", nullable = false)
    private int recordNumber;

    /** The unmodified 350-byte daily record, so a byte-compatible reject file can be re-emitted. */
    @Column(name = "raw_record", columnDefinition = "text", nullable = false)
    private String rawRecord;

    @Column(name = "reason_code", length = 4, nullable = false)
    private String reasonCode;

    @Column(name = "reason_text", length = 76, nullable = false)
    private String reasonText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
