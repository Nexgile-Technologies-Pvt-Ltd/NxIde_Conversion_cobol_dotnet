package com.carddemo;

import com.carddemo.validation.CobolDateValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the date rules of copybook {@code CSUTLDPY.cpy} and routine {@code CSUTLDTC.cbl}.
 * The clock is injected so the date-of-birth rule is deterministic (NFR-003).
 */
class CobolDateValidatorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);
    private final CobolDateValidator validator = new CobolDateValidator(clock);

    @Test
    @DisplayName("A well formed date passes every component edit")
    void acceptsValidDate() {
        assertTrue(validator.validateComponents("Open Date", "2014", "11", "20").valid());
    }

    @Test
    @DisplayName("Only centuries 19 and 20 are accepted")
    void rejectsOtherCenturies() {
        CobolDateValidator.Result result = validator.validateComponents("Open Date", "2114", "01", "01");
        assertFalse(result.valid());
        assertEquals("Open Date : Century is not valid.", result.message());
        assertTrue(validator.validateComponents("Open Date", "1999", "01", "01").valid());
    }

    @Test
    @DisplayName("A month outside 1-12 is rejected with the copybook message")
    void rejectsBadMonth() {
        CobolDateValidator.Result result = validator.validateComponents("Open Date", "2020", "13", "01");
        assertFalse(result.valid());
        assertEquals("Open Date: Month must be a number between 1 and 12.", result.message());
    }

    @Test
    @DisplayName("A 31st day is rejected for a month that has 30 days")
    void rejectsThirtyFirstInShortMonth() {
        CobolDateValidator.Result result = validator.validateComponents("Open Date", "2020", "04", "31");
        assertFalse(result.valid());
        assertEquals("Open Date:Cannot have 31 days in this month.", result.message());
    }

    @Test
    @DisplayName("February never has 30 days")
    void rejectsThirtiethOfFebruary() {
        CobolDateValidator.Result result = validator.validateComponents("Open Date", "2020", "02", "30");
        assertFalse(result.valid());
        assertEquals("Open Date:Cannot have 30 days in this month.", result.message());
    }

    @Test
    @DisplayName("The copybook leap rule divides by 4, or by 400 when the year part is 00")
    void appliesLeapRule() {
        assertTrue(validator.validateComponents("DOB", "2020", "02", "29").valid());
        assertFalse(validator.validateComponents("DOB", "2021", "02", "29").valid());
        // Year part 00 divides by 400: 2000 is a leap year, 1900 is not.
        assertTrue(validator.validateComponents("DOB", "2000", "02", "29").valid());
        assertFalse(validator.validateComponents("DOB", "1900", "02", "29").valid());
    }

    @Test
    @DisplayName("Date of birth must be strictly earlier than today")
    void rejectsFutureDateOfBirth() {
        assertTrue(validator.validateDateOfBirth("Date of Birth", "1961", "06", "08").valid());

        CobolDateValidator.Result today = validator.validateDateOfBirth("Date of Birth", "2022", "07", "18");
        assertFalse(today.valid());
        assertEquals("Date of Birth:cannot be in the future ", today.message());

        assertFalse(validator.validateDateOfBirth("Date of Birth", "2030", "01", "01").valid());
    }

    @Test
    @DisplayName("The CSUTLDTC equivalent accepts only real calendar dates")
    void checksCalendarDates() {
        assertTrue(validator.isRealCalendarDate("2022-07-18"));
        assertFalse(validator.isRealCalendarDate("2022-02-30"));
        assertFalse(validator.isRealCalendarDate("2022-7-18"));
        assertFalse(validator.isRealCalendarDate(""));
    }

    @Test
    @DisplayName("An ISO string must be exactly NNNN-NN-NN before the component edits run")
    void checksIsoStructure() {
        assertFalse(validator.validateIso("Orig Date", "2022/07/18").valid());
        assertEquals("Orig Date should be in format YYYY-MM-DD",
                validator.validateIso("Orig Date", "2022/07/18").message());
        assertTrue(validator.validateIso("Orig Date", "2022-07-18").valid());
    }
}
