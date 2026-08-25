package com.carddemo.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fixed-width text helpers reproducing COBOL {@code DISPLAY} storage semantics.
 *
 * <p>COBOL {@code PIC X(n)} fields are space padded, {@code PIC 9(n)} fields are zero padded and
 * signed {@code S9(m)V99} display fields carry the sign as an overpunch on the final digit. The
 * ASCII fixtures under {@code Cobol_Code/.../app/data/ASCII} use exactly those rules, so the
 * migration readers and the byte-compatible exporters both go through this class.</p>
 */
public final class CobolText {

    /** Overpunch characters for a positive final digit 0-9. */
    private static final String POSITIVE_OVERPUNCH = "{ABCDEFGHI";
    /** Overpunch characters for a negative final digit 0-9. */
    private static final String NEGATIVE_OVERPUNCH = "}JKLMNOPQR";

    private CobolText() {
    }

    /** Returns the value without trailing spaces or NUL padding; never null. */
    public static String trim(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c == ' ' || c == '\0' || c == '\r' || c == '\n') {
                end--;
            } else {
                break;
            }
        }
        int start = 0;
        while (start < end && value.charAt(start) == '\0') {
            start++;
        }
        return value.substring(start, end);
    }

    /** One-based inclusive COBOL substring; short records behave as if space padded. */
    public static String slice(String record, int startOneBased, int length) {
        int from = startOneBased - 1;
        if (record == null || from >= record.length()) {
            return " ".repeat(length);
        }
        int to = Math.min(record.length(), from + length);
        String raw = record.substring(from, to);
        return raw.length() < length ? raw + " ".repeat(length - raw.length()) : raw;
    }

    /** {@link #slice} followed by {@link #trim}. */
    public static String text(String record, int startOneBased, int length) {
        return trim(slice(record, startOneBased, length));
    }

    /** Right pads with spaces to the given width, clipping anything longer. */
    public static String padRight(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }

    /** Left pads with zeroes to the given width, keeping right-most characters when longer. */
    public static String padLeftZero(String value, int width) {
        String v = value == null ? "" : trim(value);
        if (v.length() >= width) {
            return v.substring(v.length() - width);
        }
        return "0".repeat(width - v.length()) + v;
    }

    /** Left pads with spaces to the given width. */
    public static String padLeft(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return " ".repeat(width - v.length()) + v;
    }

    /**
     * Reads an unsigned COBOL {@code PIC 9(n)} display field and keeps it as a zero padded string
     * so leading zeroes are never lost (requirement DATA-002).
     */
    public static String digits(String record, int startOneBased, int length) {
        String raw = slice(record, startOneBased, length);
        String cleaned = raw.replace('\0', ' ').trim();
        if (cleaned.isEmpty()) {
            return "0".repeat(length);
        }
        return padLeftZero(cleaned, length);
    }

    /**
     * Reads a signed COBOL display amount such as {@code S9(10)V99} whose last character may carry
     * an overpunch sign. The given number of implied decimal places is applied.
     */
    public static BigDecimal signedAmount(String record, int startOneBased, int length, int decimals) {
        return parseSignedDisplay(slice(record, startOneBased, length), decimals);
    }

    /** Parses a standalone signed COBOL display amount, overpunch aware. */
    public static BigDecimal parseSignedDisplay(String raw, int decimals) {
        String value = raw == null ? "" : raw.replace('\0', ' ').trim();
        if (value.isEmpty()) {
            return BigDecimal.ZERO.setScale(decimals);
        }
        boolean negative = false;
        if (value.startsWith("-")) {
            negative = true;
            value = value.substring(1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }
        if (!value.isEmpty()) {
            char last = value.charAt(value.length() - 1);
            int positiveIndex = POSITIVE_OVERPUNCH.indexOf(last);
            int negativeIndex = NEGATIVE_OVERPUNCH.indexOf(last);
            if (positiveIndex >= 0) {
                value = value.substring(0, value.length() - 1) + positiveIndex;
            } else if (negativeIndex >= 0) {
                negative = true;
                value = value.substring(0, value.length() - 1) + negativeIndex;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        if (sb.length() == 0) {
            return BigDecimal.ZERO.setScale(decimals);
        }
        BigDecimal scaled = new BigDecimal(sb.toString()).movePointLeft(decimals).setScale(decimals, RoundingMode.DOWN);
        return negative ? scaled.negate() : scaled;
    }

    /** Formats a decimal back to signed COBOL display storage with an overpunch final digit. */
    public static String toSignedDisplay(BigDecimal amount, int length, int decimals) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(decimals, RoundingMode.DOWN);
        boolean negative = value.signum() < 0;
        String digits = value.abs().movePointRight(decimals).toBigInteger().toString();
        digits = padLeftZero(digits, length);
        char last = digits.charAt(digits.length() - 1);
        int digit = last - '0';
        char overpunch = negative ? NEGATIVE_OVERPUNCH.charAt(digit) : POSITIVE_OVERPUNCH.charAt(digit);
        return digits.substring(0, digits.length() - 1) + overpunch;
    }

    /** True when every character is an ASCII digit and the value is non-empty. */
    public static boolean isAllDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** True when the value is null, empty or entirely whitespace/NUL. */
    public static boolean isBlank(String value) {
        return trim(value).isEmpty();
    }
}
