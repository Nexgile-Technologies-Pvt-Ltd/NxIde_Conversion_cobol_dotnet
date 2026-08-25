package com.carddemo;

import com.carddemo.common.CobolText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the COBOL {@code DISPLAY} codec against values taken from the supplied ASCII
 * fixtures in {@code Cobol_Code/.../app/data/ASCII}.
 */
class CobolTextTest {

    @Test
    @DisplayName("Positive overpunch: 0000005047G is +504.77 (dailytran.txt record 1)")
    void parsesPositiveOverpunch() {
        assertEquals(new BigDecimal("504.77"), CobolText.parseSignedDisplay("0000005047G", 2));
    }

    @Test
    @DisplayName("Negative overpunch: 0000009190} is -919.00 (dailytran.txt record 2)")
    void parsesNegativeOverpunch() {
        assertEquals(new BigDecimal("-919.00"), CobolText.parseSignedDisplay("0000009190}", 2));
    }

    @Test
    @DisplayName("Brace overpunch encodes a final digit of zero")
    void parsesBraceAsZero() {
        assertEquals(new BigDecimal("194.00"), CobolText.parseSignedDisplay("00000001940{", 2));
        assertEquals(new BigDecimal("0.00"), CobolText.parseSignedDisplay("00000000000{", 2));
    }

    @Test
    @DisplayName("Signed display round trip keeps the overpunch character")
    void roundTripsSignedDisplay() {
        assertEquals("0000005047G", CobolText.toSignedDisplay(new BigDecimal("504.77"), 11, 2));
        assertEquals("0000009190}", CobolText.toSignedDisplay(new BigDecimal("-919.00"), 11, 2));
        assertEquals("00000001940{", CobolText.toSignedDisplay(new BigDecimal("194.00"), 12, 2));
    }

    @Test
    @DisplayName("Identifiers keep their leading zeroes (DATA-002)")
    void keepsLeadingZeroes() {
        String record = "00000000001Y";
        assertEquals("00000000001", CobolText.digits(record, 1, 11));
        assertEquals("0000000001", CobolText.padLeftZero("1", 10));
    }

    @Test
    @DisplayName("Slicing a short record behaves as if the record were space padded")
    void slicesShortRecords() {
        // The ASCII cross-reference fixture omits the 14-byte filler, so records are 36 characters.
        String xref = "0500024453765740" + "000000050" + "00000000050";
        assertEquals("0500024453765740", CobolText.text(xref, 1, 16));
        assertEquals("000000050", CobolText.digits(xref, 17, 9));
        assertEquals("00000000050", CobolText.digits(xref, 26, 11));
        assertEquals("", CobolText.text(xref, 37, 14));
    }

    @Test
    @DisplayName("Fixed-width padding clips and pads to the exact BMS width")
    void padsToWidth() {
        assertEquals("AB   ", CobolText.padRight("AB", 5));
        assertEquals("ABCDE", CobolText.padRight("ABCDEFG", 5));
        assertEquals("  AB", CobolText.padLeft("AB", 4));
    }

    @Test
    @DisplayName("Digit and blank helpers behave like the COBOL class tests")
    void classifiesText() {
        assertTrue(CobolText.isAllDigits("0123"));
        assertFalse(CobolText.isAllDigits("01A3"));
        assertFalse(CobolText.isAllDigits(""));
        assertTrue(CobolText.isBlank("    "));
        assertFalse(CobolText.isBlank(" X  "));
    }
}
