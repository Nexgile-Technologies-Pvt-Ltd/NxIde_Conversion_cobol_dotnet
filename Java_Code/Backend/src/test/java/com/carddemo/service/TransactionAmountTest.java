package com.carddemo.service;

import com.carddemo.common.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the transaction-add field grammar of {@code COTRN02C.cbl}: the signed amount
 * picture, the structural date check and the 26-character timestamp width.
 */
class TransactionAmountTest {

    @Test
    @DisplayName("Amount must be a sign, digits, a point and exactly two decimals")
    void parsesTransactionAmount() {
        assertEquals(new BigDecimal("504.77"), TransactionService.parseSignedAmount("504.77"));
        assertEquals(new BigDecimal("-919.00"), TransactionService.parseSignedAmount("-919.00"));
        assertEquals(new BigDecimal("919.00"), TransactionService.parseSignedAmount("+919.00"));
        assertEquals(new BigDecimal("0.00"), TransactionService.parseSignedAmount("0.00"));
    }

    @Test
    @DisplayName("Anything outside the picture is rejected with the source message")
    void rejectsMalformedAmount() {
        ApiException tooFewDecimals =
                assertThrows(ApiException.class, () -> TransactionService.parseSignedAmount("504.7"));
        assertEquals("Amount should be in format -99999999.99", tooFewDecimals.getMessage());

        assertThrows(ApiException.class, () -> TransactionService.parseSignedAmount("504"));
        assertThrows(ApiException.class, () -> TransactionService.parseSignedAmount("5O4.77"));
        assertThrows(ApiException.class, () -> TransactionService.parseSignedAmount(""));
        assertThrows(ApiException.class, () -> TransactionService.parseSignedAmount("1234567890.00"));
    }

    @Test
    @DisplayName("Structural date check runs before any calendar check")
    void checksDateStructure() {
        assertTrue(TransactionService.isStructuralIsoDate("2022-06-10"));
        assertFalse(TransactionService.isStructuralIsoDate("2022/06/10"));
        assertFalse(TransactionService.isStructuralIsoDate("2022-6-10"));
        assertFalse(TransactionService.isStructuralIsoDate("22-06-10"));
    }

    @Test
    @DisplayName("Timestamps keep the 26-character COBOL width")
    void formatsTimestamps() {
        assertEquals(26, TransactionService.currentTimestamp().length());
        assertEquals(26, TransactionService.timestampFromDate("2022-06-10").length());
        assertEquals("2022-06-10 00:00:00.000000", TransactionService.timestampFromDate("2022-06-10"));
    }

    @Test
    @DisplayName("List rows show the origin date as MM/DD/YY, as the BMS map did")
    void formatsDisplayDate() {
        assertEquals("06/10/22", TransactionService.displayDate("2022-06-10 19:27:53.000000"));
        assertEquals("2022-06-10", TransactionService.isoDate("2022-06-10 19:27:53.000000"));
    }

    @Test
    @DisplayName("Account and card identifiers keep their COBOL widths and non-zero rule")
    void validatesIdentifiers() {
        assertEquals("00000000001", AccountService.validateAccountId("1"));
        assertEquals("00000000001", AccountService.validateAccountId("00000000001"));
        assertThrows(ApiException.class, () -> AccountService.validateAccountId(""));
        assertThrows(ApiException.class, () -> AccountService.validateAccountId("0"));
        assertThrows(ApiException.class, () -> AccountService.validateAccountId("12A"));

        assertEquals("0500024453765740", CardService.validateCardNumber("0500024453765740"));
        assertThrows(ApiException.class, () -> CardService.validateCardNumber("0"));
        assertThrows(ApiException.class, () -> CardService.validateCardNumber("abc"));
    }

    @Test
    @DisplayName("Card numbers are masked before reaching an audit row")
    void masksCardNumbers() {
        assertEquals("************5740", CardService.maskCard("0500024453765740"));
    }
}
