package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction master record. COBOL source {@code CVTRA05Y.cpy} / VSAM {@code TRANSACT}
 * (350 bytes).
 *
 * <p>Byte offsets: id 1-16, type 17-18, category 19-22, source 23-32, description 33-132,
 * amount 133-143, merchant id 144-152, merchant name 153-202, merchant city 203-252,
 * merchant ZIP 253-262, card 263-278, origin timestamp 279-304, processing timestamp
 * 305-330.</p>
 */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @Column(name = "transaction_id", length = 16, nullable = false)
    private String transactionId;

    @Column(name = "type_code", length = 2, nullable = false)
    private String typeCode = "";

    @Column(name = "category_code", length = 4, nullable = false)
    private String categoryCode = "";

    @Column(name = "source", length = 10, nullable = false)
    private String source = "";

    @Column(name = "description", length = 100, nullable = false)
    private String description = "";

    /** {@code TRAN-AMT} S9(9)V99. */
    @Column(name = "amount", nullable = false, precision = 13, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "merchant_id", length = 9, nullable = false)
    private String merchantId = "";

    @Column(name = "merchant_name", length = 50, nullable = false)
    private String merchantName = "";

    @Column(name = "merchant_city", length = 50, nullable = false)
    private String merchantCity = "";

    @Column(name = "merchant_zip", length = 10, nullable = false)
    private String merchantZip = "";

    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber = "";

    /** {@code TRAN-ORIG-TS} X(26). */
    @Column(name = "orig_ts", length = 26, nullable = false)
    private String origTs = "";

    /** {@code TRAN-PROC-TS} X(26). */
    @Column(name = "proc_ts", length = 26, nullable = false)
    private String procTs = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
