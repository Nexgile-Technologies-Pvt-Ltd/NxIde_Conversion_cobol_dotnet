package com.carddemo.migration;

import com.carddemo.common.CobolBinary;
import com.carddemo.common.CobolText;
import com.carddemo.config.CardDemoProperties;
import com.carddemo.domain.MigrationLog;
import com.carddemo.domain.PendingAuthDetail;
import com.carddemo.domain.PendingAuthSummary;
import com.carddemo.repository.MigrationLogRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads the IMS pending authorization database into PostgreSQL.
 *
 * <p>The source is an HD unload of IMS database {@code DBPAUTP0}, the HIDAM database declared by
 * {@code DBPAUTP0.dbd} in the optional authorization module. It carries two segment types: the
 * root {@code PAUTSUM0} of 100 bytes, laid out by {@code CIPAUSMY.cpy}, and its child
 * {@code PAUTDTL1} of 200 bytes, laid out by {@code CIPAUDTY.cpy}. They become
 * {@code pending_auth_summary} and {@code pending_auth_detail}, where the IMS parent and child
 * hierarchy is an ordinary foreign key.</p>
 *
 * <p>An unload is not a fixed length data set, so it cannot go through
 * {@link CobolRecordSource#readEbcdicRecords(String, int)}. Each segment occurrence is introduced
 * by the length of its data as an unsigned big endian halfword, then the eight character segment
 * name in EBCDIC, then twenty one bytes of IMS prefix and pointers, and the segment data follows.
 * The loader therefore scans the raw bytes for the two segment names and accepts a candidate only
 * when the halfword in front of the name is the length the DBD declares, 100 for
 * {@code PAUTSUM0} and 200 for {@code PAUTDTL1}. That test is what separates real occurrences
 * from the unload header and trailer blocks, which quote both names as ordinary text; a rejected
 * candidate costs one byte of scan position rather than a whole segment, so a genuine occurrence
 * sitting immediately behind one is never swallowed.</p>
 *
 * <p>Occurrences arrive in hierarchic order, so a {@code PAUTSUM0} establishes the account that
 * every following {@code PAUTDTL1} belongs to, which is the same walk a DL/I {@code GU} followed
 * by {@code GNP} calls performs.</p>
 *
 * <p><b>The child key is a nine's complement.</b> {@code PA-AUTHORIZATION-KEY} is
 * {@code PA-AUTH-DATE-9C S9(5) COMP-3} followed by {@code PA-AUTH-TIME-9C S9(9) COMP-3}, and the
 * digits actually stored are 99999 minus the Julian date and 999999999 minus the time, so a
 * forward scan of the children returns the newest authorization first. The fourteen complement
 * digits are kept verbatim in {@code auth_key}, which makes an ascending sort on that column
 * reproduce the physical IMS order the authorization screens rely on; the decoded date and time
 * are stored beside it in {@code auth_julian_date} and {@code auth_time_value}.</p>
 *
 * <p>The supplied unload also contains one trailer occurrence whose packed fields are not valid
 * COMP-3. Such an occurrence is counted and skipped, not treated as fatal.</p>
 */
@Service
public class PendingAuthMigrationService {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthMigrationService.class);

    private static final String EBCDIC_CODEC = "EBCDIC-CP037";
    private static final String SOURCE_FILE = "ebcdic/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat";

    /** Root segment name as it appears in the unload, encoded with the data set's own code page. */
    private static final byte[] SUMMARY_MARKER = "PAUTSUM0".getBytes(CobolRecordSource.EBCDIC);
    /** Child segment name, likewise. */
    private static final byte[] DETAIL_MARKER = "PAUTDTL1".getBytes(CobolRecordSource.EBCDIC);

    /** {@code SEGM NAME=PAUTSUM0 ... BYTES=100} in {@code DBPAUTP0.dbd}. */
    private static final int SUMMARY_LENGTH = 100;
    /** {@code SEGM NAME=PAUTDTL1 ... BYTES=200} in {@code DBPAUTP0.dbd}. */
    private static final int DETAIL_LENGTH = 200;

    /** Width of the segment name that introduces every occurrence. */
    private static final int NAME_LENGTH = 8;
    /** IMS prefix and pointer bytes sitting between the segment name and the segment data. */
    private static final int PREFIX_LENGTH = 21;

    /** {@code PA-AUTH-DATE-9C} holds this less the Julian date. */
    private static final int DATE_COMPLEMENT_BASE = 99_999;
    /** {@code PA-AUTH-TIME-9C} holds this less the time. */
    private static final int TIME_COMPLEMENT_BASE = 999_999_999;

    /** What a correct read of the supplied unload yields; anything else means the scan went wrong. */
    private static final int EXPECTED_SUMMARIES = 21;
    private static final int EXPECTED_DETAILS = 202;

    private final CardDemoProperties properties;

    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;
    private final MigrationLogRepository migrationLogs;

    public PendingAuthMigrationService(CardDemoProperties properties,
                                       PendingAuthSummaryRepository summaries,
                                       PendingAuthDetailRepository details,
                                       MigrationLogRepository migrationLogs) {
        this.properties = properties;
        this.summaries = summaries;
        this.details = details;
        this.migrationLogs = migrationLogs;
    }

    /** True when no pending authorization has been loaded yet. */
    public boolean isEmpty() {
        return summaries.count() == 0;
    }

    /**
     * Loads the unload into {@code pending_auth_summary} and {@code pending_auth_detail}. The root
     * rows are saved first because the child table references them.
     *
     * <p>The load is a no-op once the summary table holds rows, so a restart never duplicates the
     * hierarchy.</p>
     *
     * @return one summary line per entity, or an empty list when the load was skipped
     */
    @Transactional
    public List<MigrationLog> load() {
        long existingSummaries = summaries.count();
        if (existingSummaries > 0) {
            log.info("Pending authorizations already present: {} summaries and {} details; skipping {}",
                    existingSummaries, details.count(), SOURCE_FILE);
            return List.of();
        }

        CobolRecordSource source = new CobolRecordSource(properties.getMigration().getSourceDirectory());
        MigrationLog summaryLog = newLog("PendingAuthSummary");
        MigrationLog detailLog = newLog("PendingAuthDetail");
        List<MigrationLog> results = List.of(summaryLog, detailLog);
        try {
            Unload unload = scan(source.readBytes(SOURCE_FILE));

            summaryLog.setRecordsRead(unload.summariesRead);
            summaryLog.setRecordsFailed(unload.summariesRejected);
            summaryLog.setDetail("PAUTSUM0 root segments of the DBPAUTP0 unload, 100 bytes, keyed by "
                    + "PA-ACCT-ID S9(11) COMP-3."
                    + (unload.summariesRejected > 0
                    ? " " + unload.summariesRejected + " occurrences held malformed packed decimal and were skipped."
                    : ""));

            detailLog.setRecordsRead(unload.detailsRead);
            detailLog.setRecordsFailed(unload.detailsRejected + unload.detailsOrphaned);
            detailLog.setDetail("PAUTDTL1 child segments, 200 bytes, keyed by the nine's complement "
                    + "PA-AUTHORIZATION-KEY kept verbatim so ascending auth_key is the physical IMS order."
                    + (unload.detailsOrphaned > 0
                    ? " " + unload.detailsOrphaned + " occurrences preceded any root segment and were skipped."
                    : ""));

            summaries.saveAll(unload.summaryRows);
            details.saveAll(unload.detailRows);
            summaryLog.setRecordsLoaded(unload.summaryRows.size());
            detailLog.setRecordsLoaded(unload.detailRows.size());

            log.info("Pending authorization migration finished: {} PAUTSUM0 summaries and {} PAUTDTL1 details "
                            + "loaded from {}",
                    unload.summaryRows.size(), unload.detailRows.size(), SOURCE_FILE);
            if (unload.summaryRows.size() != EXPECTED_SUMMARIES || unload.detailRows.size() != EXPECTED_DETAILS) {
                log.warn("Unexpected pending authorization counts: the DBPAUTP0 unload should yield {} summaries "
                                + "and {} details, but the scan produced {} and {}",
                        EXPECTED_SUMMARIES, EXPECTED_DETAILS, unload.summaryRows.size(), unload.detailRows.size());
            }
        } catch (IOException | RuntimeException e) {
            summaryLog.setRecordsFailed(summaryLog.getRecordsRead());
            detailLog.setRecordsFailed(detailLog.getRecordsRead());
            summaryLog.setDetail("Failed: " + e.getMessage());
            detailLog.setDetail("Failed: " + e.getMessage());
            log.error("Pending authorization migration failed", e);
        }
        migrationLogs.saveAll(results);
        return results;
    }

    // ------------------------------------------------------------------ unload scan

    /**
     * Walks the unload once and decodes every segment occurrence it can trust.
     *
     * <p>A candidate is the eight byte segment name; it is genuine only when the halfword in front
     * of it is that segment's declared length. A rejected candidate advances the scan by a single
     * byte, because the header and trailer blocks quote the names close to real data, while an
     * accepted one advances past the end of its data so that the names never match inside a
     * segment.</p>
     */
    private static Unload scan(byte[] data) {
        Unload unload = new Unload();
        String parentAccountId = null;
        int position = 0;
        while (position + NAME_LENGTH <= data.length) {
            boolean summary = startsWith(data, position, SUMMARY_MARKER);
            boolean detail = !summary && startsWith(data, position, DETAIL_MARKER);
            if (!summary && !detail) {
                position++;
                continue;
            }
            int declaredLength = summary ? SUMMARY_LENGTH : DETAIL_LENGTH;
            if (position < 2 || CobolBinary.unsignedHalfword(data, position - 2) != declaredLength) {
                position++;
                continue;
            }
            int start = position + NAME_LENGTH + PREFIX_LENGTH;
            if (start + declaredLength > data.length) {
                position++;
                continue;
            }
            byte[] segment = Arrays.copyOfRange(data, start, start + declaredLength);
            if (summary) {
                unload.summariesRead++;
            } else {
                unload.detailsRead++;
            }
            try {
                if (summary) {
                    PendingAuthSummary row = readSummary(segment);
                    unload.summaryRows.add(row);
                    parentAccountId = row.getAccountId();
                } else if (parentAccountId == null) {
                    unload.detailsOrphaned++;
                } else {
                    unload.detailRows.add(readDetail(parentAccountId, segment));
                }
            } catch (IllegalArgumentException malformed) {
                // CobolBinary rejects a nibble that is not a digit or a sign, which is how the one
                // malformed trailer occurrence in the supplied unload shows up. A root that cannot
                // be decoded also clears the current parent, so its children are not attributed to
                // the previous account.
                if (summary) {
                    unload.summariesRejected++;
                    parentAccountId = null;
                } else {
                    unload.detailsRejected++;
                }
            }
            position = start + declaredLength;
        }
        return unload;
    }

    /** What one pass over the unload produced: the rows to load, and the occurrences it could not use. */
    private static final class Unload {

        private final List<PendingAuthSummary> summaryRows = new ArrayList<>();
        private final List<PendingAuthDetail> detailRows = new ArrayList<>();

        private int summariesRead;
        private int detailsRead;
        private int summariesRejected;
        private int detailsRejected;
        private int detailsOrphaned;
    }

    // ------------------------------------------------------------------ segment decoders

    /**
     * {@code CIPAUSMY.cpy}, the 100 byte {@code PAUTSUM0} root. Offsets within the segment data:
     * account id 0-5 ({@code PA-ACCT-ID S9(11) COMP-3}), customer id 6-14, authorization status 15,
     * account status 16-25, credit limit 26-31, cash limit 32-37, credit balance 38-43, cash
     * balance 44-49, approved count 50-51 ({@code S9(4) COMP}), declined count 52-53, approved
     * amount 54-59, declined amount 60-65, filler 66-99. Every amount is {@code S9(9)V99 COMP-3}.
     */
    private static PendingAuthSummary readSummary(byte[] segment) {
        PendingAuthSummary row = new PendingAuthSummary();
        // PA-ACCT-ID is packed, so it is widened back to the eleven character account key; the
        // leading zeroes are part of the identifier and must survive (DATA-002).
        row.setAccountId(CobolText.padLeftZero(CobolBinary.packedDigits(segment, 0, 6), 11));
        row.setCustomerId(text(segment, 6, 9));
        row.setAuthStatus(text(segment, 15, 1));
        // PA-ACCOUNT-STATUS is X(2) OCCURS 5; the whole ten character block is kept as one value.
        row.setAccountStatus(text(segment, 16, 10));
        row.setCreditLimit(CobolBinary.packedDecimal(segment, 26, 6, 2));
        row.setCashLimit(CobolBinary.packedDecimal(segment, 32, 6, 2));
        row.setCreditBalance(CobolBinary.packedDecimal(segment, 38, 6, 2));
        row.setCashBalance(CobolBinary.packedDecimal(segment, 44, 6, 2));
        row.setApprovedAuthCount(CobolBinary.binaryHalfword(segment, 50));
        row.setDeclinedAuthCount(CobolBinary.binaryHalfword(segment, 52));
        row.setApprovedAuthAmount(CobolBinary.packedDecimal(segment, 54, 6, 2));
        row.setDeclinedAuthAmount(CobolBinary.packedDecimal(segment, 60, 6, 2));
        return row;
    }

    /**
     * {@code CIPAUDTY.cpy}, the 200 byte {@code PAUTDTL1} child. Offsets within the segment data:
     * complement date 0-2 and complement time 3-7 (together {@code PA-AUTHORIZATION-KEY}),
     * original date 8-13, original time 14-19, card number 20-35, authorization type 36-39, card
     * expiry 40-43, message type 44-49, message source 50-55, authorization id code 56-61,
     * response code 62-63, response reason 64-67, processing code 68-73, transaction amount 74-80
     * ({@code S9(10)V99 COMP-3}), approved amount 81-87, merchant category code 88-91, acquirer
     * country 92-94, POS entry mode 95-96, merchant id 97-111, merchant name 112-133, merchant
     * city 134-146, merchant state 147-148, merchant ZIP 149-157, transaction id 158-172, match
     * status 173, fraud flag 174, fraud report date 175-182, filler 183-199.
     */
    private static PendingAuthDetail readDetail(String accountId, byte[] segment) {
        // COMP-3 stores a fixed number of digits, so these are already five and nine characters
        // wide. They are concatenated untouched: the complement is the sort order the screens want.
        String dateComplement = CobolBinary.packedDigits(segment, 0, 3);
        String timeComplement = CobolBinary.packedDigits(segment, 3, 5);
        PendingAuthDetail row = new PendingAuthDetail(accountId, dateComplement + timeComplement);
        row.setAuthJulianDate(DATE_COMPLEMENT_BASE - Integer.parseInt(dateComplement));
        row.setAuthTimeValue(TIME_COMPLEMENT_BASE - Integer.parseInt(timeComplement));
        row.setAuthOrigDate(text(segment, 8, 6));
        row.setAuthOrigTime(text(segment, 14, 6));
        row.setCardNumber(text(segment, 20, 16));
        row.setAuthType(text(segment, 36, 4));
        row.setCardExpiryDate(text(segment, 40, 4));
        row.setMessageType(text(segment, 44, 6));
        row.setMessageSource(text(segment, 50, 6));
        row.setAuthIdCode(text(segment, 56, 6));
        row.setAuthRespCode(text(segment, 62, 2));
        row.setAuthRespReason(text(segment, 64, 4));
        row.setProcessingCode(text(segment, 68, 6));
        row.setTransactionAmt(CobolBinary.packedDecimal(segment, 74, 7, 2));
        row.setApprovedAmt(CobolBinary.packedDecimal(segment, 81, 7, 2));
        // PA-MERCHANT-CATAGORY-CODE keeps the source spelling; the column is mcc_code.
        row.setMccCode(text(segment, 88, 4));
        row.setAcqrCountryCode(text(segment, 92, 3));
        // PA-POS-ENTRY-MODE is 9(2) but stays text, so a leading zero survives.
        row.setPosEntryMode(text(segment, 95, 2));
        row.setMerchantId(text(segment, 97, 15));
        row.setMerchantName(text(segment, 112, 22));
        row.setMerchantCity(text(segment, 134, 13));
        row.setMerchantState(text(segment, 147, 2));
        row.setMerchantZip(text(segment, 149, 9));
        row.setTransactionId(text(segment, 158, 15));
        row.setMatchStatus(text(segment, 173, 1));
        row.setAuthFraud(text(segment, 174, 1));
        row.setFraudRptDate(text(segment, 175, 8));
        return row;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Decodes a {@code DISPLAY} field of the segment with code page 037 and drops the padding.
     * Every character column of both tables is {@code NOT NULL}, so this never returns null.
     */
    private static String text(byte[] segment, int offset, int length) {
        return CobolText.trim(new String(segment, offset, length, CobolRecordSource.EBCDIC));
    }

    /** True when the segment name marker begins at this offset. */
    private static boolean startsWith(byte[] data, int offset, byte[] marker) {
        if (offset + marker.length > data.length) {
            return false;
        }
        for (int i = 0; i < marker.length; i++) {
            if (data[offset + i] != marker[i]) {
                return false;
            }
        }
        return true;
    }

    private static MigrationLog newLog(String entity) {
        MigrationLog logRow = new MigrationLog();
        logRow.setSourceFile(SOURCE_FILE);
        logRow.setEntity(entity);
        logRow.setCodec(EBCDIC_CODEC);
        return logRow;
    }
}
