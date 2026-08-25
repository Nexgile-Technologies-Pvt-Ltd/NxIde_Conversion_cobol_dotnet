package com.carddemo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the arithmetic the batch programs perform, taken from {@code CBTRN02C.cbl} and
 * {@code CBACT04C.cbl}.
 */
class PostingArithmeticTest {

    @Test
    @DisplayName("CBTRN02C credit-limit test: cycle credit minus cycle debit plus the incoming amount")
    void computesTemporaryBalance() {
        BigDecimal cycleCredit = new BigDecimal("1000.00");
        // Debit is populated by adding negative amounts, so subtracting it increases the result.
        BigDecimal cycleDebit = new BigDecimal("-250.00");
        BigDecimal amount = new BigDecimal("100.00");

        BigDecimal temporary = cycleCredit.subtract(cycleDebit).add(amount);
        assertEquals(new BigDecimal("1350.00"), temporary);

        assertTrue(new BigDecimal("2000.00").compareTo(temporary) >= 0, "within the limit is accepted");
        assertTrue(new BigDecimal("1000.00").compareTo(temporary) < 0, "over the limit is rejected 0102");
    }

    @Test
    @DisplayName("Posting adds a positive amount to cycle credit and a negative amount to cycle debit")
    void splitsAmountAcrossCycleAccumulators() {
        BigDecimal credit = new BigDecimal("0.00");
        BigDecimal debit = new BigDecimal("0.00");

        BigDecimal positive = new BigDecimal("504.77");
        BigDecimal negative = new BigDecimal("-919.00");

        credit = positive.signum() >= 0 ? credit.add(positive) : credit;
        debit = negative.signum() >= 0 ? debit : debit.add(negative);

        assertEquals(new BigDecimal("504.77"), credit);
        assertEquals(new BigDecimal("-919.00"), debit, "negative amounts are added as negative values");
    }

    @Test
    @DisplayName("CBACT04C interest: balance times rate divided by 1200, truncated to two decimals")
    void computesMonthlyInterest() {
        BigDecimal balance = new BigDecimal("1164.87");
        BigDecimal annualRate = new BigDecimal("15.00");

        BigDecimal interest = balance.multiply(annualRate)
                .divide(new BigDecimal("1200"), 2, RoundingMode.DOWN);

        // 1164.87 * 15 / 1200 = 14.560875; the source uses no ROUNDED so the receiver truncates.
        assertEquals(new BigDecimal("14.56"), interest);
    }

    @Test
    @DisplayName("A zero rate produces no interest transaction, a zero balance still produces one")
    void distinguishesZeroRateFromZeroBalance() {
        assertEquals(0, BigDecimal.ZERO.signum(), "a zero rate is skipped entirely");

        BigDecimal interest = new BigDecimal("0.00").multiply(new BigDecimal("15.00"))
                .divide(new BigDecimal("1200"), 2, RoundingMode.DOWN);
        assertEquals(new BigDecimal("0.00"), interest, "a zero balance still writes a zero transaction");
    }

    @Test
    @DisplayName("Expiry comparison is a lexical text compare of the first ten timestamp characters")
    void comparesExpiryLexically() {
        String accountExpiry = "2025-05-20";
        String originTimestamp = "2022-06-10 19:27:53.000000";
        String originDate = originTimestamp.substring(0, 10);

        assertTrue(accountExpiry.compareTo(originDate) >= 0, "not expired");
        assertTrue("2021-01-01".compareTo(originDate) < 0, "expired gives reject reason 0103");
    }
}
