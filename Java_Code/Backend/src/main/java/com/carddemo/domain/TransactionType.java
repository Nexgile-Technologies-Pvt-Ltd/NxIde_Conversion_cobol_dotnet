package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Transaction type reference row. COBOL source {@code CVTRA03Y.cpy} / VSAM {@code TRANTYPE}
 * (60 bytes). Also the entity maintained by the optional Db2 module {@code COTRTLIC}/{@code COTRTUPC}.
 */
@Entity
@Table(name = "transaction_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionType {

    /** {@code TRAN-TYPE} X(2). */
    @Id
    @Column(name = "type_code", length = 2, nullable = false)
    private String typeCode;

    /** {@code TRAN-TYPE-DESC} X(50). */
    @Column(name = "description", length = 50, nullable = false)
    private String description = "";

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public TransactionType(String typeCode, String description) {
        this.typeCode = typeCode;
        this.description = description;
    }
}
