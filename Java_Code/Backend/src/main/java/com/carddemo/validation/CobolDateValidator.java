package com.carddemo.validation;

import com.carddemo.common.CobolText;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Date validation reproducing COBOL copybook {@code CSUTLDPY.cpy} and callable routine
 * {@code CSUTLDTC.cbl}.
 *
 * <p>The COBOL rules, in the order the copybook applies them:</p>
 * <ol>
 *   <li>year supplied, four numeric digits, century must be 19 or 20 ("We code only 19 and 20 as
 *       valid century values");</li>
 *   <li>month supplied, numeric, between 1 and 12;</li>
 *   <li>day supplied, numeric, between 1 and 31;</li>
 *   <li>a 31st day is rejected for a month that does not have 31 days;</li>
 *   <li>a 30th day is rejected for February;</li>
 *   <li>a 29th of February is accepted only when the year divides by 4, or by 400 when the
 *       two-digit year part is {@code 00};</li>
 *   <li>date of birth must be strictly earlier than the current date.</li>
 * </ol>
 *
 * <p>The messages below are the literals the copybook builds into {@code WS-RETURN-MSG}.</p>
 */
@Component
public class CobolDateValidator {

    /** Months that have 31 days; {@code 88 WS-31-DAY-MONTH} in {@code CSUTLDWY.cpy}. */
    private static final int[] MONTHS_WITH_31_DAYS = {1, 3, 5, 7, 8, 10, 12};

    private final Clock clock;

    public CobolDateValidator(Clock clock) {
        this.clock = clock;
    }

    /** Outcome of one date edit: {@code null} message means valid. */
    public record Result(boolean valid, String message) {

        public static Result ok() {
            return new Result(true, null);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /**
     * Validates the {@code CCYY-MM-DD} components exactly as {@code EDIT-DATE-CCYYMMDD} does.
     *
     * @param label the field name the COBOL program moved into {@code WS-EDIT-VARIABLE-NAME}
     */
    public Result validateComponents(String label, String ccyy, String mm, String dd) {
        String year = CobolText.trim(ccyy);
        String month = CobolText.trim(mm);
        String day = CobolText.trim(dd);

        if (year.isEmpty()) {
            return Result.fail(label + " : Year must be supplied.");
        }
        if (!CobolText.isAllDigits(year) || year.length() != 4) {
            return Result.fail(label + " must be 4 digit number.");
        }
        int century = Integer.parseInt(year.substring(0, 2));
        if (century != 19 && century != 20) {
            return Result.fail(label + " : Century is not valid.");
        }

        if (month.isEmpty()) {
            return Result.fail(label + " : Month must be supplied.");
        }
        if (!CobolText.isAllDigits(month)) {
            return Result.fail(label + ": Month must be a number between 1 and 12.");
        }
        int monthValue = Integer.parseInt(month);
        if (monthValue < 1 || monthValue > 12) {
            return Result.fail(label + ": Month must be a number between 1 and 12.");
        }

        if (day.isEmpty()) {
            return Result.fail(label + " : Day must be supplied.");
        }
        if (!CobolText.isAllDigits(day)) {
            return Result.fail(label + ": Day must be a number between 1 and 31.");
        }
        int dayValue = Integer.parseInt(day);
        if (dayValue < 1 || dayValue > 31) {
            return Result.fail(label + ": Day must be a number between 1 and 31.");
        }

        if (dayValue == 31 && !hasThirtyOneDays(monthValue)) {
            return Result.fail(label + ":Cannot have 31 days in this month.");
        }
        if (monthValue == 2 && dayValue == 30) {
            return Result.fail(label + ":Cannot have 30 days in this month.");
        }
        if (monthValue == 2 && dayValue == 29 && !isLeapYear(year)) {
            return Result.fail(label + ":Not a leap year.Cannot have 29 days in this month.");
        }
        return Result.ok();
    }

    /** Validates an ISO {@code yyyy-MM-dd} string using the same component rules. */
    public Result validateIso(String label, String isoDate) {
        String value = CobolText.trim(isoDate);
        if (value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') {
            return Result.fail(label + " should be in format YYYY-MM-DD");
        }
        return validateComponents(label, value.substring(0, 4), value.substring(5, 7), value.substring(8, 10));
    }

    /**
     * {@code EDIT-DATE-OF-BIRTH}: the supplied date must be strictly in the past.
     * The current date is injected through {@link Clock} so behaviour is testable (NFR-003).
     */
    public Result validateDateOfBirth(String label, String ccyy, String mm, String dd) {
        Result components = validateComponents(label, ccyy, mm, dd);
        if (!components.valid()) {
            return components;
        }
        LocalDate value = LocalDate.of(Integer.parseInt(CobolText.trim(ccyy)),
                Integer.parseInt(CobolText.trim(mm)), Integer.parseInt(CobolText.trim(dd)));
        LocalDate today = LocalDate.now(clock);
        if (!today.isAfter(value)) {
            return Result.fail(label + ":cannot be in the future ");
        }
        return Result.ok();
    }

    /**
     * The {@code CSUTLDTC} callable date check: severity {@code 0000} means the date is a real
     * calendar date in the requested format.
     */
    public boolean isRealCalendarDate(String isoDate) {
        String value = CobolText.trim(isoDate);
        if (value.length() != 10) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** Parses an ISO date, returning {@code null} when it is not a real calendar date. */
    public LocalDate parseOrNull(String isoDate) {
        String value = CobolText.trim(isoDate);
        if (value.length() != 10) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean hasThirtyOneDays(int month) {
        for (int m : MONTHS_WITH_31_DAYS) {
            if (m == month) {
                return true;
            }
        }
        return false;
    }

    /**
     * Leap rule as coded in {@code CSUTLDPY}: divide by 400 when the two-digit year part is zero,
     * otherwise divide by 4. Remainder zero means leap.
     */
    private static boolean isLeapYear(String ccyy) {
        int fullYear = Integer.parseInt(ccyy);
        int twoDigitYear = Integer.parseInt(ccyy.substring(2, 4));
        int divisor = twoDigitYear == 0 ? 400 : 4;
        return fullYear % divisor == 0;
    }
}
