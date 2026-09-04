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
 * One pending authorization. COBOL source {@code CIPAUDTY.cpy} / IMS segment
 * {@code PAUTDTL1} (child of {@code PAUTSUM0}, 200 bytes) of database {@code DBPAUTP0}.
 *
 * <p>Byte offsets within the segment: authorization key 1-8, original date 9-14, original
 * time 15-20, card number 21-36, authorization type 37-40, card expiry 41-44, message type
 * 45-50, message source 51-56, authorization id code 57-62, response code 63-64, response
 * reason 65-68, processing code 69-74, transaction amount 75-81 ({@code S9(10)V99 COMP-3}),
 * approved amount 82-88, merchant category code 89-92, acquirer country 93-95, POS entry
 * mode 96-97, merchant id 98-112, merchant name 113-134, merchant city 135-147, merchant
 * state 148-149, merchant ZIP 150-158, transaction id 159-173, match status 174, fraud flag
 * 175, fraud report date 176-183, filler 184-200.</p>
 *
 * <p><b>The key is a nine's complement.</b> {@code PA-AUTHORIZATION-KEY} is
 * {@code PA-AUTH-DATE-9C S9(5) COMP-3} followed by {@code PA-AUTH-TIME-9C S9(9) COMP-3},
 * and the {@code 9C} suffix is what it says: the stored digits are 99999 minus the Julian
 * date and 999999999 minus the time, so an ascending DL/I scan returns the newest
 * authorization first. {@link Key#authKey} keeps those complement digits verbatim as a
 * fixed width 14 character string, which makes {@code ORDER BY auth_key} reproduce the
 * physical IMS order that {@code COPAUS0C} and {@code COPAUS1C} walk with {@code GNP}.</p>
 */
@Entity
@Table(name = "pending_auth_detail")
@Getter
@Setter
@NoArgsConstructor
public class PendingAuthDetail {

    /** Values of {@code PA-MATCH-STATUS}. */
    public static final String MATCH_PENDING = "P";
    public static final String MATCH_AUTH_DECLINED = "D";
    public static final String MATCH_PENDING_EXPIRED = "E";
    public static final String MATCHED_WITH_TRAN = "M";

    /** Values of {@code PA-AUTH-FRAUD}. */
    public static final String FRAUD_CONFIRMED = "F";
    public static final String FRAUD_REMOVED = "R";

    @EmbeddedId
    private Key id;

    /** The Julian date the complement key decodes to, {@code yyddd}. */
    @Column(name = "auth_julian_date", nullable = false)
    private int authJulianDate;

    /** The time the complement key decodes to, as its nine digit numeric value. */
    @Column(name = "auth_time_value", nullable = false)
    private int authTimeValue;

    /** {@code PA-AUTH-ORIG-DATE} X(6), {@code yymmdd}. */
    @Column(name = "auth_orig_date", length = 6, nullable = false)
    private String authOrigDate = "";

    /** {@code PA-AUTH-ORIG-TIME} X(6), {@code hhmmss}. */
    @Column(name = "auth_orig_time", length = 6, nullable = false)
    private String authOrigTime = "";

    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber = "";

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

    /** {@code PA-AUTH-RESP-CODE} X(2); {@code 00} is the approved condition name. */
    @Column(name = "auth_resp_code", length = 2, nullable = false)
    private String authRespCode = "";

    @Column(name = "auth_resp_reason", length = 4, nullable = false)
    private String authRespReason = "";

    @Column(name = "processing_code", length = 6, nullable = false)
    private String processingCode = "";

    /** {@code PA-TRANSACTION-AMT} S9(10)V99 COMP-3. */
    @Column(name = "transaction_amt", nullable = false, precision = 14, scale = 2)
    private BigDecimal transactionAmt = BigDecimal.ZERO;

    /** {@code PA-APPROVED-AMT} S9(10)V99 COMP-3. */
    @Column(name = "approved_amt", nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedAmt = BigDecimal.ZERO;

    /** {@code PA-MERCHANT-CATAGORY-CODE} X(4), the source spelling. */
    @Column(name = "mcc_code", length = 4, nullable = false)
    private String mccCode = "";

    @Column(name = "acqr_country_code", length = 3, nullable = false)
    private String acqrCountryCode = "";

    /** {@code PA-POS-ENTRY-MODE} 9(2), kept as text so a leading zero survives. */
    @Column(name = "pos_entry_mode", length = 2, nullable = false)
    private String posEntryMode = "";

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

    /** {@code PA-MATCH-STATUS} X(1): P, D, E or M. */
    @Column(name = "match_status", length = 1, nullable = false)
    private String matchStatus = "";

    /** {@code PA-AUTH-FRAUD} X(1): F confirmed, R removed, blank never reported. */
    @Column(name = "auth_fraud", length = 1, nullable = false)
    private String authFraud = "";

    /** {@code PA-FRAUD-RPT-DATE} X(8). */
    @Column(name = "fraud_rpt_date", length = 8, nullable = false)
    private String fraudRptDate = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PendingAuthDetail(String accountId, String authKey) {
        this.id = new Key(accountId, authKey);
    }

    public String getAccountId() {
        return id == null ? null : id.getAccountId();
    }

    public String getAuthKey() {
        return id == null ? null : id.getAuthKey();
    }

    /** True when this authorization is currently reported as fraud. */
    public boolean isFraudConfirmed() {
        return FRAUD_CONFIRMED.equals(authFraud);
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

        /** The parent {@code PAUTSUM0} account id. */
        @Column(name = "account_id", length = 11, nullable = false)
        private String accountId;

        /** {@code PAUT9CTS}: five complement date digits then nine complement time digits. */
        @Column(name = "auth_key", length = 14, nullable = false)
        private String authKey;
    }
}
