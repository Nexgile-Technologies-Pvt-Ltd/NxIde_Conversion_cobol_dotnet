package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pending authorization summary, one row per account. COBOL source {@code CIPAUSMY.cpy} /
 * IMS segment {@code PAUTSUM0} (root, 100 bytes) of database {@code DBPAUTP0}.
 *
 * <p>Byte offsets within the segment: account id 1-6 ({@code S9(11) COMP-3}), customer id
 * 7-15, authorization status 16, account status 17-26 ({@code X(2) OCCURS 5}), credit limit
 * 27-32, cash limit 33-38, credit balance 39-44, cash balance 45-50, approved count 51-52
 * ({@code S9(4) COMP}), declined count 53-54, approved amount 55-60, declined amount 61-66,
 * filler 67-100. Every amount is {@code S9(9)V99 COMP-3}.</p>
 *
 * <p>The IMS sequence field {@code ACCNTID} is the account id alone, so the account id is
 * the whole primary key here as well ({@code DBPAUTP0.dbd} line 29).</p>
 */
@Entity
@Table(name = "pending_auth_summary")
@Getter
@Setter
@NoArgsConstructor
public class PendingAuthSummary {

    @Id
    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;

    /** {@code PA-CUST-ID} 9(9). */
    @Column(name = "customer_id", length = 9, nullable = false)
    private String customerId = "";

    /** {@code PA-AUTH-STATUS} X(1). */
    @Column(name = "auth_status", length = 1, nullable = false)
    private String authStatus = "";

    /** {@code PA-ACCOUNT-STATUS} X(2) OCCURS 5, kept as the whole ten character block. */
    @Column(name = "account_status", length = 10, nullable = false)
    private String accountStatus = "";

    /** {@code PA-CREDIT-LIMIT} S9(9)V99 COMP-3. */
    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    /** {@code PA-CASH-LIMIT} S9(9)V99 COMP-3. */
    @Column(name = "cash_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashLimit = BigDecimal.ZERO;

    /** {@code PA-CREDIT-BALANCE} S9(9)V99 COMP-3. */
    @Column(name = "credit_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditBalance = BigDecimal.ZERO;

    /** {@code PA-CASH-BALANCE} S9(9)V99 COMP-3. */
    @Column(name = "cash_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    /** {@code PA-APPROVED-AUTH-CNT} S9(4) COMP. */
    @Column(name = "approved_auth_count", nullable = false)
    private int approvedAuthCount;

    /** {@code PA-DECLINED-AUTH-CNT} S9(4) COMP. */
    @Column(name = "declined_auth_count", nullable = false)
    private int declinedAuthCount;

    /** {@code PA-APPROVED-AUTH-AMT} S9(9)V99 COMP-3. */
    @Column(name = "approved_auth_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedAuthAmount = BigDecimal.ZERO;

    /** {@code PA-DECLINED-AUTH-AMT} S9(9)V99 COMP-3. */
    @Column(name = "declined_auth_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal declinedAuthAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
