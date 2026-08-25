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
 * Account. COBOL source {@code CVACT01Y.cpy} / VSAM {@code ACCTDAT} (300 bytes).
 *
 * <p>Byte offsets: id 1-11, status 12, current balance 13-24, credit limit 25-36,
 * cash limit 37-48, open date 49-58, expiration 59-68, reissue 69-78, cycle credit 79-90,
 * cycle debit 91-102, ZIP 103-112, group 113-122.</p>
 *
 * <p>FR-ACCT-009: ZIP and disclosure group stay in their own columns. The
 * {@code COACTUPC} output layout that shifted group into the ZIP bytes is never reproduced.</p>
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;

    /** {@code ACCT-ACTIVE-STATUS} X; the update screen validates Y or N. */
    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus = "Y";

    @Column(name = "curr_bal", nullable = false, precision = 14, scale = 2)
    private BigDecimal currBal = BigDecimal.ZERO;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "cash_credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal cashCreditLimit = BigDecimal.ZERO;

    @Column(name = "open_date", length = 10, nullable = false)
    private String openDate = "";

    /** Source spelling was {@code ACCT-EXPIRAION-DATE}; kept as a correct name here. */
    @Column(name = "expiration_date", length = 10, nullable = false)
    private String expirationDate = "";

    @Column(name = "reissue_date", length = 10, nullable = false)
    private String reissueDate = "";

    @Column(name = "curr_cyc_credit", nullable = false, precision = 14, scale = 2)
    private BigDecimal currCycCredit = BigDecimal.ZERO;

    @Column(name = "curr_cyc_debit", nullable = false, precision = 14, scale = 2)
    private BigDecimal currCycDebit = BigDecimal.ZERO;

    @Column(name = "addr_zip", length = 10, nullable = false)
    private String addrZip = "";

    /** Joins to {@code disclosure_group} for interest pricing. */
    @Column(name = "group_id", length = 10, nullable = false)
    private String groupId = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
