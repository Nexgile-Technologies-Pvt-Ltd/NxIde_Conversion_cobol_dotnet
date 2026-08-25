package com.carddemo.dto;

import jakarta.validation.constraints.NotBlank;

/** Payloads for card list ({@code COCRDLIC}), card view ({@code COCRDSLC}) and card update ({@code COCRDUPC}). */
public final class CardDtos {

    private CardDtos() {
    }

    /** One of the seven rows of the {@code CCRDLIA} map. */
    public record CardRow(
            String cardNumber,
            String accountId,
            String activeStatus,
            String embossedName,
            String expirationDate) {
    }

    /**
     * Card detail. CVV is deliberately absent from every response (FR-CARD-007, NFR-006).
     * {@code expirationMonth}/{@code expirationYear} are the two editable parts of the update map;
     * {@code expirationDay} is the part the BMS map hid and the program preserved.
     */
    public record CardDetail(
            String cardNumber,
            String accountId,
            String embossedName,
            String activeStatus,
            String expirationDate,
            String expirationMonth,
            String expirationDay,
            String expirationYear,
            long version) {
    }

    /**
     * Card update request. Source validation order: embossed name required and letters/spaces only,
     * status Y or N, month 1-12, year 1950-2099.
     *
     * <p>FR-CARD-004: the account id is verified against the card record rather than ignored the way
     * the legacy programs did after validating it.</p>
     */
    public record CardUpdateRequest(
            @NotBlank(message = "Account number must be supplied.")
            String accountId,

            @NotBlank(message = "Card number must be supplied.")
            String cardNumber,

            String embossedName,
            String activeStatus,
            String expirationMonth,
            String expirationYear,
            long version) {
    }
}
