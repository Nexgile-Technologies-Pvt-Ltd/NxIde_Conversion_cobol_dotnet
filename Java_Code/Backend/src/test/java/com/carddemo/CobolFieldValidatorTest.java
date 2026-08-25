package com.carddemo;

import com.carddemo.validation.CobolFieldValidator;
import com.carddemo.validation.LookupTables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the reusable edits of {@code COACTUPC.cbl} and the lookup tables extracted from
 * {@code CSLKPCDY.cpy}.
 */
class CobolFieldValidatorTest {

    private LookupTables lookups;
    private CobolFieldValidator validator;

    @BeforeEach
    void setUp() {
        lookups = new LookupTables();
        ReflectionTestUtils.invokeMethod(lookups, "load");
        validator = new CobolFieldValidator(lookups);
    }

    @Test
    @DisplayName("The copybook lookup tables load with the expected cardinalities")
    void loadsLookupTables() {
        assertEquals(56, lookups.getStateCodes().size());
        assertEquals(490, lookups.getPhoneAreaCodes().size());
        assertTrue(lookups.isStateCode("NC"));
        assertFalse(lookups.isStateCode("ZZ"));
    }

    @Test
    @DisplayName("Y/N edit reports the copybook messages")
    void validatesYesNo() {
        assertNull(validator.yesNo("Account Status", "Y"));
        assertEquals("Account Status must be supplied.", validator.yesNo("Account Status", "  "));
        assertEquals("Account Status must be Y or N.", validator.yesNo("Account Status", "X"));
    }

    @Test
    @DisplayName("Alphabetic edit allows letters and spaces only")
    void validatesAlpha() {
        assertNull(validator.alphaRequired("First Name", "Mary Jane"));
        assertEquals("First Name must be supplied.", validator.alphaRequired("First Name", ""));
        assertEquals("First Name can have alphabets only.", validator.alphaRequired("First Name", "Jane3"));
    }

    @Test
    @DisplayName("FICO score must be 300 to 850")
    void validatesFico() {
        assertEquals("FICO Score should be between 300 and 850.", validator.fico("274"));
        assertNull(validator.fico("700"));
        assertEquals("FICO Score should be between 300 and 850.", validator.fico("851"));
        assertEquals("FICO Score must be a 3 digit number.", validator.fico("7A0"));
    }

    @Test
    @DisplayName("SSN part 1 cannot be 000, 666 or in the 900 series")
    void validatesSsn() {
        assertNull(validator.ssn("020", "97", "3888"));
        assertNotNull(validator.ssn("000", "97", "3888"));
        assertNotNull(validator.ssn("666", "97", "3888"));
        assertNotNull(validator.ssn("912", "97", "3888"));
        assertNotNull(validator.ssn("020", "00", "3888"));
        assertNotNull(validator.ssn("020", "97", "0000"));
    }

    @Test
    @DisplayName("A completely blank phone is accepted; a partial one is not")
    void validatesPhone() {
        assertNull(validator.usPhone("Phone 1", "", "", ""));
        assertNull(validator.usPhone("Phone 1", "908", "119", "8310"));
        assertEquals("Phone 1: Prefix code must be supplied.",
                validator.usPhone("Phone 1", "908", "", "8310"));
        assertEquals("Phone 1: Line number code must be supplied.",
                validator.usPhone("Phone 1", "908", "119", ""));
        assertEquals("Phone 1: Not valid North America general purpose area code",
                validator.usPhone("Phone 1", "999", "119", "8310"));
    }

    @Test
    @DisplayName("State and the first two ZIP digits must be a listed combination")
    void validatesStateZip() {
        assertNull(validator.stateZipCombination("NC", "27510"));
        assertNotNull(validator.stateZipCombination("NC", "99999"));
    }

    @Test
    @DisplayName("NUMVAL-C accepts the documented decimal grammar and rejects anything else")
    void parsesNumvalC() {
        assertEquals(new BigDecimal("1234.56"), CobolFieldValidator.parseNumvalC("1,234.56"));
        assertEquals(new BigDecimal("-1234.56"), CobolFieldValidator.parseNumvalC("-1234.56"));
        assertEquals(new BigDecimal("-1234.56"), CobolFieldValidator.parseNumvalC("1234.56-"));
        assertEquals(new BigDecimal("99.00"), CobolFieldValidator.parseNumvalC("$99.00"));
        assertNull(CobolFieldValidator.parseNumvalC("12.34.56"));
        assertNull(CobolFieldValidator.parseNumvalC("abc"));
        assertNull(CobolFieldValidator.parseNumvalC(""));
    }

    @Test
    @DisplayName("Signed amount edit only checks the grammar, never a business range")
    void validatesSignedAmount() {
        assertNull(validator.signedAmount("Credit Limit", "2020.00"));
        assertNull(validator.signedAmount("Credit Limit", "-1.00"));
        assertEquals("Credit Limit must be supplied.", validator.signedAmount("Credit Limit", ""));
        assertEquals("Credit Limit is not valid", validator.signedAmount("Credit Limit", "12.3.4"));
    }
}
