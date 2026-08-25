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
 * Card cross-reference. COBOL source {@code CVACT03Y.cpy} / VSAM {@code CCXREF} (50 bytes),
 * alternate index {@code CXACAIX} on account id.
 *
 * <p>This is the bridge between card, customer and account. Transactions carry no account or
 * customer id, so every account resolution goes through this table.</p>
 */
@Entity
@Table(name = "card_xref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CardXref {

    /** {@code XREF-CARD-NUM} X(16), primary key. */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber;

    /** {@code XREF-CUST-ID} 9(9). */
    @Column(name = "customer_id", length = 9, nullable = false)
    private String customerId;

    /** {@code XREF-ACCT-ID} 9(11); non-unique alternate key. */
    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;
}
