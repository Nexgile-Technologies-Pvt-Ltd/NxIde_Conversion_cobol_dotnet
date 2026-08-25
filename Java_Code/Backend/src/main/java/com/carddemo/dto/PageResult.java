package com.carddemo.dto;

import java.util.List;

/**
 * Keyset page envelope replacing the CICS {@code STARTBR}/{@code READNEXT}/{@code READPREV} browse
 * state that the list screens kept in their work COMMAREA.
 *
 * <p>FR-CARD-002: {@code hasNext} is always computed from the next <em>matching</em> row, so F8
 * never leads to an empty page the way the legacy unfiltered look-ahead could.</p>
 *
 * @param rows        the rows on this page, at most the screen row count
 * @param firstKey    key of the first row, used by the previous-page request
 * @param lastKey     key of the last row, used by the next-page request
 * @param pageNumber  one-based page counter shown on the screen
 * @param hasNext     whether a further matching row exists after {@code lastKey}
 * @param hasPrevious whether a matching row exists before {@code firstKey}
 * @param message     informational text such as the legacy top/bottom-of-file messages
 */
public record PageResult<T>(
        List<T> rows,
        String firstKey,
        String lastKey,
        int pageNumber,
        boolean hasNext,
        boolean hasPrevious,
        String message) {

    public static <T> PageResult<T> of(List<T> rows, String firstKey, String lastKey, int pageNumber,
                                       boolean hasNext, boolean hasPrevious, String message) {
        return new PageResult<>(rows, firstKey, lastKey, pageNumber, hasNext, hasPrevious, message);
    }
}
