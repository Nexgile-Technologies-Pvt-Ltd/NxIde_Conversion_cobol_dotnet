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

/**
 * Transaction category reference row. COBOL source {@code CVTRA04Y.cpy} / VSAM {@code TRANCATG}
 * (60 bytes).
 *
 * <p>The key is {@code (type code, category code)} and not category alone: category codes repeat
 * across types, so any lookup keyed only by category would be wrong.</p>
 */
@Entity
@Table(name = "transaction_category")
@Getter
@Setter
@NoArgsConstructor
public class TransactionCategory {

    @EmbeddedId
    private Key id;

    /** {@code TRAN-CAT-TYPE-DESC} X(50). */
    @Column(name = "description", length = 50, nullable = false)
    private String description = "";

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public TransactionCategory(String typeCode, String categoryCode, String description) {
        this.id = new Key(typeCode, categoryCode);
        this.description = description;
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

        @Column(name = "type_code", length = 2, nullable = false)
        private String typeCode;

        @Column(name = "category_code", length = 4, nullable = false)
        private String categoryCode;
    }
}
