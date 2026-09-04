package com.carddemo.dto;

import java.math.BigDecimal;

/**
 * Payloads for the pending authorization summary ({@code COPAUS0C}, {@code CPVS}) and the
 * authorization detail ({@code COPAUS1C}, {@code CPVD}) screens of the optional authorization
 * module.
 *
 * <p>Every coded field of the {@code COPAU00} and {@code COPAU01} maps travels as both the raw
 * COBOL code and the text the source resolved it to, so a screen can show a readable state
 * without reimplementing the lookup tables.</p>
 */
public final class PendingAuthDtos {

    private PendingAuthDtos() {
    }

    /**
     * The account level block at the head of the {@code COPAU00} map: the pending authorization
     * root segment {@code PAUTSUM0} with the two counts derived from its children.
     */
    public record PendingAuthSummaryView(
            String accountId,
            String customerId,
            /** {@code CNAME} of the {@code COPAU00} map, assembled from the customer record. */
            String customerName,
            /**
             * {@code ACCSTAT}. The source declared and labelled the field and then never moved
             * anything into it, so the screen always showed a blank; it is filled here from the
             * account record the label promised.
             */
            String accountActiveStatus,
            String authStatus,
            String accountStatus,
            BigDecimal creditLimit,
            BigDecimal cashLimit,
            BigDecimal creditBalance,
            BigDecimal cashBalance,
            int approvedAuthCount,
            int declinedAuthCount,
            BigDecimal approvedAuthAmount,
            BigDecimal declinedAuthAmount,
            long pendingCount,
            long fraudCount) {
    }

    /** One row of the authorization list, newest first, as the {@code COPAU00} map ordered them. */
    public record PendingAuthRow(
            String authKey,
            String authDate,
            String authTime,
            String cardNumber,
            BigDecimal transactionAmt,
            String authRespCode,
            String authRespText,
            String matchStatus,
            String matchStatusText,
            String authFraud,
            String fraudStatusText,
            String merchantName) {
    }

    /** Full authorization detail as rendered by the {@code COPAU01} map. */
    public record PendingAuthDetailView(
            String accountId,
            String authKey,
            String cardNumber,
            String authDate,
            String authTime,
            String authOrigDate,
            String authOrigTime,
            String authType,
            String cardExpiryDate,
            String messageType,
            String messageSource,
            String authIdCode,
            String authRespCode,
            String authRespText,
            String authRespReason,
            String authRespReasonText,
            String processingCode,
            BigDecimal transactionAmt,
            BigDecimal approvedAmt,
            String mccCode,
            String acqrCountryCode,
            String posEntryMode,
            String posEntryModeText,
            String merchantId,
            String merchantName,
            String merchantCity,
            String merchantState,
            String merchantZip,
            String transactionId,
            String matchStatus,
            String matchStatusText,
            String authFraud,
            String fraudStatusText,
            String fraudRptDate,
            /** Key of the next authorization under the same account, or null at the end (F8). */
            String nextAuthKey,
            /** Key of the previous authorization under the same account, or null at the start. */
            String previousAuthKey) {
    }

    /**
     * F5 on the detail screen. The source toggled the flag with no confirmation and no reason;
     * the conversion asks for the intended state explicitly so a double submission is not a
     * silent reversal, and carries an optional note into the audit trail.
     */
    public record FraudMarkRequest(
            boolean confirmed,
            String note) {
    }

    /** Result of a fraud mark or removal: the message line plus the refreshed detail. */
    public record FraudMarkResult(
            String message,
            PendingAuthDetailView detail) {
    }
}
