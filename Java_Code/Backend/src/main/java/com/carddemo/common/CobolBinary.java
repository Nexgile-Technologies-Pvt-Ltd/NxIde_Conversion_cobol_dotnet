package com.carddemo.common;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Byte level helpers for the COBOL computational storage forms.
 *
 * <p>{@link CobolText} covers {@code DISPLAY} fields, which survive a character decode. The
 * IMS segments of the authorization module also carry {@code COMP-3} (packed decimal) and
 * {@code COMP} (binary) fields, and those are numeric encodings of the raw bytes: decoding
 * them as EBCDIC text first would destroy them. Everything here therefore works on the
 * undecoded {@code byte[]} of a record.</p>
 *
 * <p>{@code COMP-3} stores two digits per byte with a sign nibble last: {@code C} and
 * {@code F} are positive, {@code D} is negative. {@code S9(n) COMP-3} therefore occupies
 * {@code n / 2 + 1} bytes. {@code S9(4) COMP} is a big endian signed halfword.</p>
 */
public final class CobolBinary {

    private CobolBinary() {
    }

    /** Bytes a {@code S9(digits) COMP-3} field occupies. */
    public static int packedLength(int digits) {
        return digits / 2 + 1;
    }

    /**
     * Reads a {@code COMP-3} field as its digit string, ignoring the sign. Used for key fields
     * that are compared and ordered as text rather than arithmetic.
     */
    public static String packedDigits(byte[] record, int offset, int length) {
        StringBuilder digits = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int b = record[offset + i] & 0xFF;
            int high = b >> 4;
            int low = b & 0x0F;
            if (high > 9) {
                throw new IllegalArgumentException(
                        "not a packed decimal digit at byte " + (offset + i) + ": " + Integer.toHexString(high));
            }
            digits.append((char) ('0' + high));
            if (i < length - 1) {
                if (low > 9) {
                    throw new IllegalArgumentException(
                            "not a packed decimal digit at byte " + (offset + i) + ": " + Integer.toHexString(low));
                }
                digits.append((char) ('0' + low));
            } else if (low < 0x0A) {
                throw new IllegalArgumentException(
                        "not a packed decimal sign at byte " + (offset + i) + ": " + Integer.toHexString(low));
            }
        }
        return digits.toString();
    }

    /** True when the field holds a well formed {@code COMP-3} value. */
    public static boolean isPacked(byte[] record, int offset, int length) {
        if (offset < 0 || offset + length > record.length) {
            return false;
        }
        try {
            packedDigits(record, offset, length);
            return true;
        } catch (IllegalArgumentException notPacked) {
            return false;
        }
    }

    /** Reads a {@code COMP-3} field as a whole number. */
    public static long packedLong(byte[] record, int offset, int length) {
        String digits = packedDigits(record, offset, length);
        long value = Long.parseLong(digits);
        return isNegative(record[offset + length - 1]) ? -value : value;
    }

    /** Reads a {@code COMP-3} field with the given number of implied decimal places. */
    public static BigDecimal packedDecimal(byte[] record, int offset, int length, int decimals) {
        String digits = packedDigits(record, offset, length);
        BigDecimal value = new BigDecimal(new BigInteger(digits), decimals);
        return isNegative(record[offset + length - 1]) ? value.negate() : value;
    }

    /** Reads a {@code S9(4) COMP} big endian signed halfword. */
    public static int binaryHalfword(byte[] record, int offset) {
        return (short) (((record[offset] & 0xFF) << 8) | (record[offset + 1] & 0xFF));
    }

    /** Reads an unsigned big endian halfword, used for the segment lengths in an IMS unload. */
    public static int unsignedHalfword(byte[] record, int offset) {
        return ((record[offset] & 0xFF) << 8) | (record[offset + 1] & 0xFF);
    }

    private static boolean isNegative(byte last) {
        return (last & 0x0F) == 0x0D;
    }
}
