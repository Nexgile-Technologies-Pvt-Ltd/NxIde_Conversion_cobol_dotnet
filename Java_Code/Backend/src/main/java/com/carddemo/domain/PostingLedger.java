package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Per-input posting ledger giving the unique {@code (run, record-number)} identity required by
 * FR-BATCH-005 so a restarted posting run never double-applies an already committed record.
 */
@Entity
@Table(name = "posting_ledger")
@Getter
@Setter
@NoArgsConstructor
public class PostingLedger {

    public static final String OUTCOME_POSTED = "POSTED";
    public static final String OUTCOME_REJECTED = "REJECTED";

    @EmbeddedId
    private Key id;

    @Column(name = "transaction_id", length = 16)
    private String transactionId;

    @Column(name = "outcome", length = 12, nullable = false)
    private String outcome;

    public PostingLedger(Long batchRunId, int recordNumber, String transactionId, String outcome) {
        this.id = new Key(batchRunId, recordNumber);
        this.transactionId = transactionId;
        this.outcome = outcome;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "batch_run_id", nullable = false)
        private Long batchRunId;

        @Column(name = "record_number", nullable = false)
        private Integer recordNumber;
    }
}
