package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Transaction category balance. COBOL source {@code CVTRA01Y.cpy} / VSAM {@code TCATBALF}
 * (50 bytes). Composite key {@code (account, type, category)}.
 *
 * <p>Posting ({@code CBTRN02C}) creates or increments this row; interest ({@code CBACT04C})
 * reads it in composite-key order.</p>
 */
@Entity
@Table(name = "category_balance")
@Getter
@Setter
@NoArgsConstructor
public class CategoryBalance {

    @EmbeddedId
    private Key id;

    /** {@code TRAN-CAT-BAL} S9(9)V99. */
    @Column(name = "balance", nullable = false, precision = 13, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public CategoryBalance(String accountId, String typeCode, String categoryCode, BigDecimal balance) {
        this.id = new Key(accountId, typeCode, categoryCode);
        this.balance = balance;
    }

    public String getAccountId() {
        return id == null ? null : id.getAccountId();
    }

    public String getTypeCode() {
        return id == null ? null : id.getTypeCode();
    }

    public String getCategoryCode() {
        return id == null ? null : id.getCategoryCode();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "account_id", length = 11, nullable = false)
        private String accountId;

        @Column(name = "type_code", length = 2, nullable = false)
        private String typeCode;

        @Column(name = "category_code", length = 4, nullable = false)
        private String categoryCode;
    }
}
