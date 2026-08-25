package com.carddemo.validation;

import com.carddemo.common.CobolText;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Field edits reproducing the reusable validation paragraphs of {@code COACTUPC.cbl}
 * ({@code 1215-EDIT-MANDATORY} through {@code 1265-EDIT-US-SSN}).
 *
 * <p>Each method returns the exact message the COBOL program assembled into
 * {@code WS-RETURN-MSG}, or {@code null} when the value passes.</p>
 */
@Component
public class CobolFieldValidator {

    private final LookupTables lookups;

    public CobolFieldValidator(LookupTables lookups) {
        this.lookups = lookups;
    }

    /** {@code 1215-EDIT-MANDATORY}: value must not be blank. */
    public String mandatory(String label, String value) {
        if (CobolText.isBlank(value)) {
            return label + " must be supplied.";
        }
        return null;
    }

    /** {@code 1220-EDIT-YESNO}: value must be supplied and must be Y or N. */
    public String yesNo(String label, String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty() || "0".equals(v)) {
            return label + " must be supplied.";
        }
        if (!"Y".equals(v) && !"N".equals(v)) {
            return label + " must be Y or N.";
        }
        return null;
    }

    /** {@code 1225-EDIT-ALPHA-REQD}: required, letters and spaces only. */
    public String alphaRequired(String label, String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return label + " must be supplied.";
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (!Character.isLetter(c) && c != ' ') {
                return label + " can have alphabets only.";
            }
        }
        return null;
    }

    /** {@code 1230-EDIT-ALPHANUM-REQD}: required, letters, digits and spaces only. */
    public String alphanumericRequired(String label, String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return label + " must be supplied.";
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != ' ') {
                return label + " can have numbers or alphabets only.";
            }
        }
        return null;
    }

    /** {@code 1245-EDIT-NUM-REQD}: required and entirely numeric. */
    public String numericRequired(String label, String value, int expectedLength) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return label + " must be supplied.";
        }
        if (!CobolText.isAllDigits(v)) {
            return label + " must be a " + expectedLength + " digit number.";
        }
        return null;
    }

    /**
     * {@code 1250-EDIT-SIGNED-9V2}: required, and must satisfy {@code FUNCTION TEST-NUMVAL-C}.
     * The COBOL check has no business range; only the grammar is enforced.
     */
    public String signedAmount(String label, String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return label + " must be supplied.";
        }
        if (parseNumvalC(v) == null) {
            return label + " is not valid";
        }
        return null;
    }

    /**
     * Invariant {@code NUMVAL-C} compatible parser (FR-ACCT-010). Accepts an optional leading or
     * trailing sign, an optional currency symbol, thousands separators and at most one decimal
     * point. Returns {@code null} when the text is not a valid numeric literal.
     */
    public static BigDecimal parseNumvalC(String raw) {
        String v = CobolText.trim(raw);
        if (v.isEmpty()) {
            return null;
        }
        boolean negative = false;
        if (v.endsWith("-")) {
            negative = true;
            v = v.substring(0, v.length() - 1).trim();
        } else if (v.endsWith("+")) {
            v = v.substring(0, v.length() - 1).trim();
        }
        if (v.startsWith("-")) {
            negative = true;
            v = v.substring(1).trim();
        } else if (v.startsWith("+")) {
            v = v.substring(1).trim();
        }
        if (v.startsWith("$")) {
            v = v.substring(1).trim();
        }
        if (v.isEmpty()) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        boolean seenDot = false;
        boolean seenDigit = false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
                seenDigit = true;
            } else if (c == ',') {
                continue;
            } else if (c == '.') {
                if (seenDot) {
                    return null;
                }
                seenDot = true;
                digits.append('.');
            } else {
                return null;
            }
        }
        if (!seenDigit) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(digits.toString());
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * {@code 1260-EDIT-US-PHONE-NUM}. A completely blank phone is acceptable; otherwise every part
     * must be supplied, numeric and non-zero, and the area code must be a valid North American
     * general purpose code from {@code CSLKPCDY}.
     *
     * <p>FR-ACCT-006 / defect note: the COBOL "all blank" test referenced part A twice where part C
     * was intended. All three parts are checked explicitly here.</p>
     */
    public String usPhone(String label, String areaCode, String prefix, String lineNumber) {
        String a = CobolText.trim(areaCode);
        String b = CobolText.trim(prefix);
        String c = CobolText.trim(lineNumber);

        if (a.isEmpty() && b.isEmpty() && c.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            return label + ": Area code must be supplied.";
        }
        if (!CobolText.isAllDigits(a) || a.length() != 3) {
            return label + ": Area code must be A 3 digit number.";
        }
        if (Integer.parseInt(a) == 0) {
            return label + ": Area code cannot be zero";
        }
        if (!lookups.isGeneralPurposeAreaCode(a)) {
            return label + ": Not valid North America general purpose area code";
        }
        if (b.isEmpty()) {
            return label + ": Prefix code must be supplied.";
        }
        if (!CobolText.isAllDigits(b) || b.length() != 3) {
            return label + ": Prefix code must be A 3 digit number.";
        }
        if (Integer.parseInt(b) == 0) {
            return label + ": Prefix code cannot be zero";
        }
        if (c.isEmpty()) {
            return label + ": Line number code must be supplied.";
        }
        if (!CobolText.isAllDigits(c) || c.length() != 4) {
            return label + ": Line number code must be A 4 digit number.";
        }
        if (Integer.parseInt(c) == 0) {
            return label + ": Line number code cannot be zero";
        }
        return null;
    }

    /**
     * {@code 1265-EDIT-US-SSN}: part 1 three digits and not {@code 000}, {@code 666} or 900-999;
     * part 2 two digits 01-99; part 3 four digits 0001-9999.
     */
    public String ssn(String part1, String part2, String part3) {
        String p1 = CobolText.trim(part1);
        String p2 = CobolText.trim(part2);
        String p3 = CobolText.trim(part3);

        String error = numericRequired("SSN: First 3 chars", p1, 3);
        if (error != null) {
            return error;
        }
        if (p1.length() != 3) {
            return "SSN: First 3 chars must be a 3 digit number.";
        }
        int first = Integer.parseInt(p1);
        if (first == 0 || first == 666 || (first >= 900 && first <= 999)) {
            return "SSN: Invalid. First 3 chars cannot be 000, 666 or in the 900 series.";
        }
        error = numericRequired("SSN: 4th and 5th chars", p2, 2);
        if (error != null) {
            return error;
        }
        if (p2.length() != 2 || Integer.parseInt(p2) < 1) {
            return "SSN: 4th and 5th chars must be a 2 digit number from 01 to 99.";
        }
        error = numericRequired("SSN: Last 4 chars", p3, 4);
        if (error != null) {
            return error;
        }
        if (p3.length() != 4 || Integer.parseInt(p3) < 1) {
            return "SSN: Last 4 chars must be a 4 digit number from 0001 to 9999.";
        }
        return null;
    }

    /** FICO score must be in the inclusive range 300-850 ({@code COACTUPC} lines 2431-2558). */
    public String fico(String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return "FICO Score must be supplied.";
        }
        if (!CobolText.isAllDigits(v)) {
            return "FICO Score must be a 3 digit number.";
        }
        int score = Integer.parseInt(v);
        if (score < 300 || score > 850) {
            return "FICO Score should be between 300 and 850.";
        }
        return null;
    }

    /** United States state code must appear in the {@code CSLKPCDY} state list. */
    public String stateCode(String value) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            return "State must be supplied.";
        }
        if (!lookups.isStateCode(v)) {
            return "Invalid State Code.";
        }
        return null;
    }

    /** State and the first two ZIP digits must be a combination listed in {@code CSLKPCDY}. */
    public String stateZipCombination(String state, String zip) {
        if (!lookups.isStateZipCombination(state, zip)) {
            return "Invalid zip code for the state.";
        }
        return null;
    }
}
