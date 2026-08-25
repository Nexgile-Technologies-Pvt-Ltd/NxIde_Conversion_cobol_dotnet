package com.carddemo.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterises the fixed 133-column report layout declared by {@code CVTRA07Y.cpy} and written by
 * {@code CBTRN03C.cbl}.
 */
class ReportFormatterTest {

    @Test
    @DisplayName("Every report record is exactly 133 characters")
    void formatsFixedWidthLines() {
        assertEquals(133, ReportFormatter.nameHeader("2022-01-01", "2022-07-06").length());
        assertEquals(133, ReportFormatter.columnHeader().length());
        assertEquals(133, ReportFormatter.separator().length());
        assertEquals(133, ReportFormatter.detail("0000000000683580", "00000000020", "01",
                "Purchase", "0001", "Regular Sales Draft", "POS TERM", new BigDecimal("504.77")).length());
        assertEquals(133, ReportFormatter.pageTotal(new BigDecimal("1234.56")).length());
        assertEquals(133, ReportFormatter.accountTotal(new BigDecimal("-99.99")).length());
        assertEquals(133, ReportFormatter.grandTotal(new BigDecimal("77954.70")).length());
    }

    @Test
    @DisplayName("PIC -ZZZ,ZZZ,ZZZ.ZZ shows a leading minus only for a negative value")
    void formatsSignedAmount() {
        assertEquals("       1,234.56", ReportFormatter.signedAmount(new BigDecimal("1234.56")));
        assertEquals("      -1,234.56", ReportFormatter.signedAmount(new BigDecimal("-1234.56")));
        assertEquals(15, ReportFormatter.signedAmount(BigDecimal.ZERO).length());
    }

    @Test
    @DisplayName("PIC +ZZZ,ZZZ,ZZZ.ZZ always carries the sign")
    void formatsPlusAmount() {
        assertEquals("     +77,954.70", ReportFormatter.plusAmount(new BigDecimal("77954.70")));
        assertEquals("     -24,399.29", ReportFormatter.plusAmount(new BigDecimal("-24399.29")));
    }

    @Test
    @DisplayName("The name header carries the short name, long name and the date range")
    void buildsNameHeader() {
        String header = ReportFormatter.nameHeader("2022-01-01", "2022-07-06");
        assertTrue(header.startsWith("DALYREPT"));
        assertTrue(header.contains("Daily Transaction Report"));
        assertTrue(header.contains("Date Range: 2022-01-01 to 2022-07-06"));
    }

    @Test
    @DisplayName("The separator line is 133 hyphens, as TRANSACTION-HEADER-2 declares")
    void buildsSeparator() {
        assertEquals("-".repeat(133), ReportFormatter.separator());
    }

    @Test
    @DisplayName("HTML statement content is escaped, which the source never did")
    void escapesHtml() {
        assertEquals("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;",
                StatementService.escape("<script>alert('x')</script>"));
        assertEquals("Nitzsche, Nicolas &amp; Lowe", StatementService.escape("Nitzsche, Nicolas & Lowe"));
    }
}
