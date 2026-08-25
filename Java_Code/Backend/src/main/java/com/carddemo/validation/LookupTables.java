package com.carddemo.validation;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reference tables extracted verbatim from COBOL copybook {@code CSLKPCDY.cpy}.
 *
 * <p>The copybook declares three level-88 condition lists: North American phone area codes,
 * United States state codes and valid {@code state + first two ZIP digits} combinations. They were
 * extracted into {@code src/main/resources/cobol-lookup/*.txt} at conversion time and are loaded
 * here, so no lookup value is hardcoded in Java.</p>
 */
@Component
public class LookupTables {

    private static final Logger log = LoggerFactory.getLogger(LookupTables.class);

    private static final String BASE = "cobol-lookup/";

    private Set<String> phoneAreaCodes = Set.of();
    private Set<String> generalPurposeAreaCodes = Set.of();
    private Set<String> stateCodes = Set.of();
    private Set<String> stateZipPrefixes = Set.of();

    @PostConstruct
    void load() {
        phoneAreaCodes = read("phone-area-codes.txt");
        generalPurposeAreaCodes = read("phone-general-purpose-codes.txt");
        stateCodes = read("us-state-codes.txt");
        stateZipPrefixes = read("us-state-zip-prefixes.txt");
        log.info("Loaded CSLKPCDY lookup tables: {} area codes, {} general purpose codes, "
                        + "{} state codes, {} state/ZIP combinations",
                phoneAreaCodes.size(), generalPurposeAreaCodes.size(), stateCodes.size(), stateZipPrefixes.size());
    }

    private Set<String> read(String name) {
        ClassPathResource resource = new ClassPathResource(BASE + name);
        Set<String> values = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    values.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read COBOL lookup table " + name, e);
        }
        return Collections.unmodifiableSet(values);
    }

    /** {@code 88 VALID-PHONE-AREA-CODE} in {@code CSLKPCDY.cpy}. */
    public boolean isPhoneAreaCode(String value) {
        return value != null && phoneAreaCodes.contains(value.trim());
    }

    /**
     * {@code 88 VALID-GENERAL-PURP-CODE} in {@code CSLKPCDY.cpy}. This is the condition the account
     * update program actually tests for phone parts A of phone 1 and phone 2.
     */
    public boolean isGeneralPurposeAreaCode(String value) {
        return value != null && generalPurposeAreaCodes.contains(value.trim());
    }

    /** {@code 88 VALID-US-STATE-CODE} in {@code CSLKPCDY.cpy}. */
    public boolean isStateCode(String value) {
        return value != null && stateCodes.contains(value.trim().toUpperCase());
    }

    /**
     * {@code 88 VALID-US-STATE-ZIP-CD2-COMBO}: the two-character state code concatenated with the
     * first two ZIP digits must appear in the copybook list.
     */
    public boolean isStateZipCombination(String state, String zip) {
        if (state == null || zip == null) {
            return false;
        }
        String st = state.trim().toUpperCase();
        String zp = zip.trim();
        if (st.length() != 2 || zp.length() < 2) {
            return false;
        }
        return stateZipPrefixes.contains(st + zp.substring(0, 2));
    }

    public Set<String> getStateCodes() {
        return stateCodes;
    }

    public Set<String> getPhoneAreaCodes() {
        return phoneAreaCodes;
    }
}
