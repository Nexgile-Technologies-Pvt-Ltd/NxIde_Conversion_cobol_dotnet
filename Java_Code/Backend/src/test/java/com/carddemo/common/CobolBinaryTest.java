package com.carddemo.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the computational storage forms of the IMS authorization segments:
 * {@code COMP-3} packed decimal, the {@code S9(4) COMP} halfword, and the nine's complement
 * authorization key of {@code CIPAUDTY.cpy}.
 *
 * <p>The byte fixtures are taken from the shipped unload
 * {@code AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat}, so they are real records rather than invented
 * ones.</p>
 */
class CobolBinaryTest {

    @Test
    @DisplayName("S9(11) COMP-3 occupies six bytes and keeps its leading zeroes")
    void readsThePackedAccountKey() {
        // PA-ACCT-ID of the first PAUTSUM0 segment: account 1, positive sign nibble C.
        byte[] segment = {0x00, 0x00, 0x00, 0x00, 0x00, 0x1C};

        assertEquals(6, CobolBinary.packedLength(11));
        assertEquals("00000000001", CobolBinary.packedDigits(segment, 0, 6));
        assertEquals(1L, CobolBinary.packedLong(segment, 0, 6));
    }

    @Test
    @DisplayName("A packed amount applies the implied decimal places")
    void readsAPackedAmount() {
        // PA-TRANSACTION-AMT S9(10)V99: 1.24 over seven bytes.
        byte[] segment = {0x00, 0x00, 0x00, 0x00, 0x00, 0x12, 0x4C};

        assertEquals(7, CobolBinary.packedLength(12));
        assertEquals(new BigDecimal("1.24"), CobolBinary.packedDecimal(segment, 0, 7, 2));
    }

    @Test
    @DisplayName("Sign nibble D is negative and C and F are positive")
    void readsThePackedSign() {
        // Two bytes hold three digits and the sign, so 131 with two decimals is 1.31.
        assertEquals(new BigDecimal("-1.31"), CobolBinary.packedDecimal(new byte[]{0x13, 0x1D}, 0, 2, 2));
        assertEquals(new BigDecimal("1.31"), CobolBinary.packedDecimal(new byte[]{0x13, 0x1C}, 0, 2, 2));
        assertEquals(new BigDecimal("1.31"), CobolBinary.packedDecimal(new byte[]{0x13, 0x1F}, 0, 2, 2));
    }

    @Test
    @DisplayName("A byte that is not packed decimal is rejected rather than silently decoded")
    void rejectsMalformedPackedData() {
        // The unload trailer holds a run of zero bytes, whose final nibble is not a sign.
        byte[] trailer = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        assertFalse(CobolBinary.isPacked(trailer, 0, 6));
        assertThrows(IllegalArgumentException.class, () -> CobolBinary.packedDigits(trailer, 0, 6));
        // A digit nibble above nine is equally invalid.
        assertFalse(CobolBinary.isPacked(new byte[]{(byte) 0xAB, 0x1C}, 0, 2));
    }

    @Test
    @DisplayName("S9(4) COMP is a signed big endian halfword")
    void readsTheBinaryCounts() {
        // PA-APPROVED-AUTH-CNT of the account with fifty pending authorizations.
        assertEquals(50, CobolBinary.binaryHalfword(new byte[]{0x00, 0x32}, 0));
        assertEquals(-1, CobolBinary.binaryHalfword(new byte[]{(byte) 0xFF, (byte) 0xFF}, 0));
        assertEquals(200, CobolBinary.unsignedHalfword(new byte[]{0x00, (byte) 0xC8}, 0));
    }

    @Test
    @DisplayName("The authorization key is a nine's complement, so ascending order is newest first")
    void decodesTheNinesComplementKey() {
        // Two PAUTDTL1 keys of account 1, one second apart on Julian date 23300.
        byte[] newer = {0x76, 0x69, (byte) 0x9C, (byte) 0x99, (byte) 0x87, 0x47, 0x44, 0x4C};
        byte[] older = {0x76, 0x69, (byte) 0x9C, (byte) 0x99, (byte) 0x87, 0x48, 0x38, (byte) 0x8C};

        String newerKey = CobolBinary.packedDigits(newer, 0, 3) + CobolBinary.packedDigits(newer, 3, 5);
        String olderKey = CobolBinary.packedDigits(older, 0, 3) + CobolBinary.packedDigits(older, 3, 5);

        assertEquals(14, newerKey.length());
        assertEquals("76699998747444", newerKey);
        // Both decode to Julian 23300, and the smaller complement is the later authorization.
        assertEquals(23300, 99999 - Integer.parseInt(newerKey.substring(0, 5)));
        assertEquals(23300, 99999 - Integer.parseInt(olderKey.substring(0, 5)));
        assertTrue(newerKey.compareTo(olderKey) < 0);
    }
}
