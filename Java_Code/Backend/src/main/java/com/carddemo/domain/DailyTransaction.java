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

import java.math.BigDecimal;

/**
 * Daily transaction posting input. COBOL source {@code CVTRA06Y.cpy} / sequential {@code DALYTRAN}
 * (350 bytes, field-for-field identical to the transaction master).
 *
 * <p>Kept as its own table exactly like the legacy split between {@code DALYTRAN} and
 * {@code TRANSACT}: posting ({@code CBTRN02C}) reads here and writes accepted rows to
 * {@link Transaction}.</p>
 */
@Entity
@Table(name = "daily_transaction")
@Getter
@Setter
@NoArgsConstructor
public class DailyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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

    @Column(name = "orig_ts", length = 26, nullable = false)
    private String origTs = "";

    @Column(name = "proc_ts", length = 26, nullable = false)
    private String procTs = "";

    /** One-based position in the daily input file; part of the posting idempotency key. */
    @Column(name = "record_number", nullable = false)
    private int recordNumber;

    @Column(name = "processed", nullable = false)
    private boolean processed;
}
