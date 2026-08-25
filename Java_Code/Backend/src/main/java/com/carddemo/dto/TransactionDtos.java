package com.carddemo.dto;

import java.math.BigDecimal;

/**
 * Payloads for transaction list ({@code COTRN00C}), view ({@code COTRN01C}) and add
 * ({@code COTRN02C}), plus bill payment ({@code COBIL00C}).
 */
public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** One of the ten rows of the {@code COTRN0A} map: id, date, description, amount. */
    public record TransactionRow(
            String transactionId,
            String date,
            String description,
            BigDecimal amount) {
    }

    /** Full transaction detail as rendered by the {@code COTRN1A} map. */
    public record TransactionDetail(
            String transactionId,
            String cardNumber,
            String typeCode,
            String typeDescription,
            String categoryCode,
            String categoryDescription,
            String source,
            String description,
            BigDecimal amount,
            String originDate,
            String processDate,
            String merchantId,
            String merchantName,
            String merchantCity,
            String merchantZip,
            String accountId) {
    }

    /**
     * Transaction add request.
     *
     * <p>Key resolution follows {@code COTRN02C}: when the account is supplied it wins and its
     * cross-reference supplies the card; otherwise the card is resolved to an account; both blank
     * is an error.</p>
     */
    public record TransactionAddRequest(
            String accountId,
            String cardNumber,
            String typeCode,
            String categoryCode,
            String source,
            String description,
            String amount,
            String originDate,
            String processDate,
            String merchantId,
            String merchantName,
            String merchantCity,
            String merchantZip,
            boolean confirmed) {
    }

    /** Prefill returned by the F5 "copy last transaction" action of the add screen. */
    public record TransactionPrefill(
            String typeCode,
            String categoryCode,
            String source,
            String description,
            String amount,
            String originDate,
            String processDate,
            String merchantId,
            String merchantName,
            String merchantCity,
            String merchantZip) {
    }

    /** Bill payment enquiry result: the account balance the {@code COBIL0A} map displays. */
    public record BillPaymentView(
            String accountId,
            BigDecimal currentBalance,
            String cardNumber,
            boolean payable,
            String message) {
    }

    /** Bill payment confirmation. The legacy screen accepts only Y or N. */
    public record BillPaymentRequest(
            String accountId,
            boolean confirmed) {
    }

    /** Result of a completed payment or add. */
    public record TransactionWriteResult(
            String transactionId,
            String message,
            BigDecimal newBalance) {
    }
}
