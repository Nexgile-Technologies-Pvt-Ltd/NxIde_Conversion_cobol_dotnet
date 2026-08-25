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
import java.time.LocalDateTime;

/**
 * Generated card statement. COBOL source {@code CBSTM03A.CBL} which produced a fixed 80-byte text
 * file and a 100-byte HTML file.
 *
 * <p>FR-BATCH-010: the target has no 51 card x 10 transaction in-memory cap and HTML content is
 * escaped.</p>
 */
@Entity
@Table(name = "account_statement")
@Getter
@Setter
@NoArgsConstructor
public class AccountStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_run_id")
    private Long batchRunId;

    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber;

    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;

    @Column(name = "customer_id", length = 9, nullable = false)
    private String customerId;

    @Column(name = "tran_count", nullable = false)
    private int tranCount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "text_content", columnDefinition = "text", nullable = false)
    private String textContent = "";

    @Column(name = "html_content", columnDefinition = "text", nullable = false)
    private String htmlContent = "";

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt = LocalDateTime.now();
}
