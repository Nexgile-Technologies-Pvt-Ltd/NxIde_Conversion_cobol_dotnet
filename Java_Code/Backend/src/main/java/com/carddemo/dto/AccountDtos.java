package com.carddemo.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** Payloads for account view ({@code COACTVWC}) and account update ({@code COACTUPC}). */
public final class AccountDtos {

    private AccountDtos() {
    }

    /**
     * Combined account plus customer view. This is the whole {@code CACTVWA} map: the program
     * resolves cross-reference then account then customer, and renders both records together.
     */
    public record AccountDetail(
            String accountId,
            String activeStatus,
            BigDecimal currentBalance,
            BigDecimal creditLimit,
            BigDecimal cashCreditLimit,
            String openDate,
            String expirationDate,
            String reissueDate,
            BigDecimal currentCycleCredit,
            BigDecimal currentCycleDebit,
            String accountZip,
            String groupId,
            long accountVersion,

            String customerId,
            String firstName,
            String middleName,
            String lastName,
            String ssn,
            String dateOfBirth,
            int ficoScore,
            String addressLine1,
            String addressLine2,
            String city,
            String stateCode,
            String zipCode,
            String countryCode,
            String phone1,
            String phone2,
            String governmentIssuedId,
            String eftAccountId,
            String primaryCardHolderIndicator,
            long customerVersion,

            String cardNumber) {
    }

    /**
     * Account update request. Every editable field of the {@code CACTUPA} map is present; the
     * service applies the validations in the exact source order documented for {@code COACTUPC}.
     *
     * <p>Versions carry the optimistic concurrency tokens (FR-ACCT-007): the update fails with a
     * conflict rather than overwriting a later change.</p>
     */
    public record AccountUpdateRequest(
            @NotBlank(message = "Account Number must be supplied.")
            String accountId,

            String activeStatus,
            String openDate,
            String creditLimit,
            String expirationDate,
            String cashCreditLimit,
            String reissueDate,
            String currentBalance,
            String currentCycleCredit,
            String currentCycleDebit,
            String groupId,

            String ssn,
            String dateOfBirth,
            String ficoScore,
            String firstName,
            String middleName,
            String lastName,
            String addressLine1,
            String stateCode,
            String addressLine2,
            String zipCode,
            String city,
            String countryCode,
            String phone1,
            String phone2,
            String governmentIssuedId,
            String eftAccountId,
            String primaryCardHolderIndicator,

            long accountVersion,
            long customerVersion) {
    }

    /** Account summary used by dashboards and pickers. */
    public record AccountSummary(
            String accountId,
            String activeStatus,
            BigDecimal currentBalance,
            BigDecimal creditLimit,
            String openDate,
            String expirationDate,
            String groupId,
            String customerName) {
    }
}
