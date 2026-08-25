package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Atomic identifier allocator. FR-TRAN-009: replaces the legacy "browse to the highest transaction
 * id and add one" pattern in {@code COTRN02C} and {@code COBIL00C}, which could collide under
 * concurrency while still producing the same 16 character zero padded presentation.
 */
@Entity
@Table(name = "id_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdSequence {

    public static final String TRANSACTION_ID = "TRANSACTION_ID";

    @Id
    @Column(name = "sequence_name", length = 40, nullable = false)
    private String sequenceName;

    @Column(name = "next_value", nullable = false)
    private long nextValue;
}
