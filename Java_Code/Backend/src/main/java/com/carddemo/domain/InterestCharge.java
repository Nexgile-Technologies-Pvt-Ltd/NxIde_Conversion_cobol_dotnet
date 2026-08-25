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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Interest charge identity {@code (cycle, account, type, category)} required by FR-BATCH-005 so an
 * interest run cannot double charge an account after a restart.
 */
@Entity
@Table(name = "interest_charge")
@Getter
@Setter
@NoArgsConstructor
public class InterestCharge {

    @EmbeddedId
    private Key id;

    @Column(name = "interest_amt", nullable = false, precision = 13, scale = 2)
    private BigDecimal interestAmt = BigDecimal.ZERO;

    @Column(name = "transaction_id", length = 16, nullable = false)
    private String transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public InterestCharge(String cycleId, String accountId, String typeCode, String categoryCode,
                          BigDecimal interestAmt, String transactionId) {
        this.id = new Key(cycleId, accountId, typeCode, categoryCode);
        this.interestAmt = interestAmt;
        this.transactionId = transactionId;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "cycle_id", length = 10, nullable = false)
        private String cycleId;

        @Column(name = "account_id", length = 11, nullable = false)
        private String accountId;

        @Column(name = "type_code", length = 2, nullable = false)
        private String typeCode;

        @Column(name = "category_code", length = 4, nullable = false)
        private String categoryCode;
    }
}
