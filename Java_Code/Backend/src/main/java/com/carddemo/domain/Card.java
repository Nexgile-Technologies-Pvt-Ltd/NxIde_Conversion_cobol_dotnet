package com.carddemo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Credit card. COBOL source {@code CVACT02Y.cpy} / VSAM {@code CARDDAT} (150 bytes).
 *
 * <p>Byte offsets: card number 1-16, account 17-27, CVV 28-30, embossed name 31-80,
 * expiration 81-90, active status 91.</p>
 *
 * <p>FR-CARD-007: CVV is never exposed by an API response and is preserved on update because the
 * legacy {@code COCRDUPC} "new CVV" field was declared, used, but never assigned.</p>
 */
@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
public class Card {

    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    private String cardNumber;

    @Column(name = "account_id", length = 11, nullable = false)
    private String accountId;

    @Column(name = "cvv_code", length = 3, nullable = false)
    private String cvvCode = "000";

    @Column(name = "embossed_name", length = 50, nullable = false)
    private String embossedName = "";

    /** {@code CARD-EXPIRAION-DATE} X(10) stored as {@code yyyy-MM-dd}. */
    @Column(name = "expiration_date", length = 10, nullable = false)
    private String expirationDate = "";

    @Column(name = "active_status", length = 1, nullable = false)
    private String activeStatus = "Y";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
