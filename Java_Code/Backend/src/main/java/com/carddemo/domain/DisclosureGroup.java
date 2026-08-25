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
 * Disclosure group interest rate. COBOL source {@code CVTRA02Y.cpy} / VSAM {@code DISCGRP}
 * (50 bytes). Composite key {@code (group, type, category)}.
 *
 * <p>{@code CBACT04C} looks up the account's own group first and falls back to the literal group
 * {@code DEFAULT} when the group-specific row is absent (legacy VSAM status 23).</p>
 */
@Entity
@Table(name = "disclosure_group")
@Getter
@Setter
@NoArgsConstructor
public class DisclosureGroup {

    /** Fallback group id used when the account group has no specific rate row. */
    public static final String DEFAULT_GROUP = "DEFAULT";

    @EmbeddedId
    private Key id;

    /** {@code DIS-INT-RATE} S9(4)V99, an annual percentage divided by 1200 per month. */
    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 2)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public DisclosureGroup(String groupId, String typeCode, String categoryCode, BigDecimal interestRate) {
        this.id = new Key(groupId, typeCode, categoryCode);
        this.interestRate = interestRate;
    }

    public String getGroupId() {
        return id == null ? null : id.getGroupId();
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

        @Column(name = "group_id", length = 10, nullable = false)
        private String groupId;

        @Column(name = "type_code", length = 2, nullable = false)
        private String typeCode;

        @Column(name = "category_code", length = 4, nullable = false)
        private String categoryCode;
    }
}
