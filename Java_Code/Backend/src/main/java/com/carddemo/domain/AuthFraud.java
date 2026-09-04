package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fraud report state for one authorization. COBOL source {@code AUTHFRDS.ddl} /
 * {@code AUTHFRDS.dcl}, Db2 table {@code CARDDEMO.AUTHFRDS}, written by {@code COPAUS2C}
 * when {@code COPAUS1C} marks or removes fraud with F5.
 *
 * <p>Column widths and the composite key {@code (CARD_NUM, AUTH_TS)} are the Db2 declarations
 * verbatim. On the mainframe this table lived in Db2 while the authorization itself lived in
 * IMS, so {@code COPAUS1C} had to co-ordinate two resource managers and roll IMS back when the
 * Db2 write failed. Both are ordinary tables here, so the pair is one local transaction and the
 * rollback path disappears.</p>
 *
 * <p>{@code reportedBy} is not in the Db2 declaration. The source recorded no actor, and the
 * conversion adds one so a fraud mark is attributable; the audit trail carries the same fact.</p>
 */
@Entity
@Table(name = "auth_fraud")
@Getter
@Setter
@NoArgsConstructor
public class AuthFraud {

    @EmbeddedId
    private Key id;

    @Column(name = "auth_type", length = 4, nullable = false)
    private String authType = "";

    @Column(name = "card_expiry_date", length = 4, nullable = false)
    private String cardExpiryDate = "";

    @Column(name = "message_type", length = 6, nullable = false)
    private String messageType = "";

    @Column(name = "message_source", length = 6, nullable = false)
    private String messageSource = "";

    @Column(name = "auth_id_code", length = 6, nullable = false)
    private String authIdCode = "";

    @Column(name = "auth_resp_code", length = 2, nullable = false)
    private String authRespCode = "";

    @Column(name = "auth_resp_reason", length = 4, nullable = false)
    private String authRespReason = "";

    @Column(name = "processing_code", length = 6, nullable = false)
    private String processingCode = "";

    /** {@code TRANSACTION_AMT} DECIMAL(12,2). */
    @Column(name = "transaction_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal transactionAmt = BigDecimal.ZERO;

    /** {@code APPROVED_AMT} DECIMAL(12,2). */
    @Column(name = "approved_amt", nullable = false, precision = 12, scale = 2)
    private BigDecimal approvedAmt = BigDecimal.ZERO;

    /** {@code MERCHANT_CATAGORY_CODE} CHAR(4), the source spelling. */
    @Column(name = "mcc_code", length = 4, nullable = false)
    private String mccCode = "";

    @Column(name = "acqr_country_code", length = 3, nullable = false)
    private String acqrCountryCode = "";

    /** {@code POS_ENTRY_MODE} SMALLINT, the one numeric column of the Db2 table. */
    @Column(name = "pos_entry_mode", nullable = false)
    private short posEntryMode;

    @Column(name = "merchant_id", length = 15, nullable = false)
    private String merchantId = "";

    @Column(name = "merchant_name", length = 22, nullable = false)
    private String merchantName = "";

    @Column(name = "merchant_city", length = 13, nullable = false)
    private String merchantCity = "";

    @Column(name = "merchant_state", length = 2, nullable = false)
    private String merchantState = "";

    @Column(name = "merchant_zip", length = 9, nullable = false)
    private String merchantZip = "";

    @Column(name = "transaction_id", length = 15, nullable = false)
    private String transactionId = "";

    @Column(name = "match_status", length = 1, nullable = false)
    private String matchStatus = "";

    /** {@code AUTH_FRAUD} CHAR(1): F confirmed, R removed. */
    @Column(name = "auth_fraud", length = 1, nullable = false)
    private String authFraud = "";

    /** {@code FRAUD_RPT_DATE} DATE, kept as the ISO text the screens render. */
    @Column(name = "fraud_rpt_date", length = 10, nullable = false)
    private String fraudRptDate = "";

    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId = "";

    @Column(name = "customer_id", length = 9, nullable = false)
    private String customerId = "";

    /** The signed-on user who marked or removed the report; not part of the Db2 declaration. */
    @Column(name = "reported_by", length = 8)
    private String reportedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public AuthFraud(String cardNumber, String authTs) {
        this.id = new Key(cardNumber, authTs);
    }

    public String getCardNumber() {
        return id == null ? null : id.getCardNumber();
    }

    public String getAuthTs() {
        return id == null ? null : id.getAuthTs();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "card_number", length = 16, nullable = false)
        private String cardNumber;

        /** {@code AUTH_TS} TIMESTAMP, held as the 26 character COBOL host variable text. */
        @Column(name = "auth_ts", length = 26, nullable = false)
        private String authTs;
    }
}
