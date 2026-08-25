package com.carddemo.batch;

import com.carddemo.common.CobolText;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The fixed 133-column transaction report layout. COBOL source {@code CVTRA07Y.cpy}, written by
 * {@code CBTRN03C.cbl}.
 *
 * <p>Line shapes taken from the copybook:</p>
 * <ul>
 *   <li>name header: short name {@code X(38)}, long name {@code X(41)}, {@code Date Range: }
 *       {@code X(12)}, start {@code X(10)}, {@code " to "}, end {@code X(10)};</li>
 *   <li>column header 1 with the fixed widths 17, 12, 19, 35, 14, 1, 16;</li>
 *   <li>column header 2: 133 hyphens;</li>
 *   <li>detail: id {@code X(16)}, account {@code X(11)}, type {@code X(2)-X(15)},
 *       category {@code 9(4)-X(29)}, source {@code X(10)}, amount {@code -ZZZ,ZZZ,ZZZ.ZZ};</li>
 *   <li>page / account / grand totals: label, dot fill, then {@code +ZZZ,ZZZ,ZZZ.ZZ}.</li>
 * </ul>
 *
 * <p>The formatting is deliberately locale independent (NFR-003): a fixed
 * {@link DecimalFormatSymbols} is used so a process default culture cannot change the output.</p>
 */
public final class ReportFormatter {

    /** Every report record is 133 characters wide. */
    public static final int RECORD_LENGTH = 133;

    /** Page totals are emitted every 20 line-counter units, as in the source. */
    public static final int LINES_PER_PAGE = 20;

    private static final DecimalFormat SIGNED_AMOUNT;
    private static final DecimalFormat PLUS_AMOUNT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        SIGNED_AMOUNT = new DecimalFormat("###,###,##0.00", symbols);
        PLUS_AMOUNT = new DecimalFormat("###,###,##0.00", symbols);
    }

    private ReportFormatter() {
    }

    /** {@code REPORT-NAME-HEADER}. */
    public static String nameHeader(String startDate, String endDate) {
        return pad(CobolText.padRight("DALYREPT", 38)
                + CobolText.padRight("Daily Transaction Report", 41)
                + CobolText.padRight("Date Range: ", 12)
                + CobolText.padRight(startDate, 10)
                + " to "
                + CobolText.padRight(endDate, 10));
    }

    /** {@code TRANSACTION-HEADER-1}. */
    public static String columnHeader() {
        return pad(CobolText.padRight("Transaction ID", 17)
                + CobolText.padRight("Account ID", 12)
                + CobolText.padRight("Transaction Type", 19)
                + CobolText.padRight("Tran Category", 35)
                + CobolText.padRight("Tran Source", 14)
                + " "
                + CobolText.padRight("        Amount", 16));
    }

    /** {@code TRANSACTION-HEADER-2}: 133 hyphens. */
    public static String separator() {
        return "-".repeat(RECORD_LENGTH);
    }

    /** {@code TRANSACTION-DETAIL-REPORT}. */
    public static String detail(String transactionId, String accountId, String typeCode,
                                String typeDescription, String categoryCode, String categoryDescription,
                                String source, BigDecimal amount) {
        return pad(CobolText.padRight(transactionId, 16)
                + " "
                + CobolText.padRight(accountId, 11)
                + " "
                + CobolText.padRight(typeCode, 2)
                + "-"
                + CobolText.padRight(typeDescription, 15)
                + " "
                + CobolText.padLeftZero(categoryCode, 4)
                + "-"
                + CobolText.padRight(categoryDescription, 29)
                + " "
                + CobolText.padRight(source, 10)
                + "    "
                + signedAmount(amount)
                + "  ");
    }

    /** {@code REPORT-PAGE-TOTALS}. */
    public static String pageTotal(BigDecimal total) {
        return totalLine("Page Total", 11, 86, total);
    }

    /** {@code REPORT-ACCOUNT-TOTALS}. In the source this groups by card number. */
    public static String accountTotal(BigDecimal total) {
        return totalLine("Account Total", 13, 84, total);
    }

    /** {@code REPORT-GRAND-TOTALS}. */
    public static String grandTotal(BigDecimal total) {
        return totalLine("Grand Total", 11, 86, total);
    }

    private static String totalLine(String label, int labelWidth, int fillWidth, BigDecimal total) {
        return pad(CobolText.padRight(label, labelWidth) + ".".repeat(fillWidth) + plusAmount(total));
    }

    /** {@code PIC -ZZZ,ZZZ,ZZZ.ZZ}: 15 cells, leading minus for a negative value only. */
    static String signedAmount(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String digits = SIGNED_AMOUNT.format(value.abs());
        String text = (value.signum() < 0 ? "-" : " ") + digits;
        return CobolText.padLeft(text, 15);
    }

    /** {@code PIC +ZZZ,ZZZ,ZZZ.ZZ}: 15 cells, the sign is always present. */
    static String plusAmount(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String digits = PLUS_AMOUNT.format(value.abs());
        String text = (value.signum() < 0 ? "-" : "+") + digits;
        return CobolText.padLeft(text, 15);
    }

    private static String pad(String line) {
        return CobolText.padRight(line, RECORD_LENGTH);
    }
}
