package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.Account;
import com.carddemo.domain.AuthFraud;
import com.carddemo.domain.Customer;
import com.carddemo.domain.PendingAuthDetail;
import com.carddemo.domain.PendingAuthSummary;
import com.carddemo.dto.PageResult;
import com.carddemo.dto.PendingAuthDtos.FraudMarkRequest;
import com.carddemo.dto.PendingAuthDtos.FraudMarkResult;
import com.carddemo.dto.PendingAuthDtos.PendingAuthDetailView;
import com.carddemo.dto.PendingAuthDtos.PendingAuthRow;
import com.carddemo.dto.PendingAuthDtos.PendingAuthSummaryView;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.AuthFraudRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pending authorization summary and detail. COBOL sources {@code COPAUS0C.cbl} ({@code CPVS}),
 * {@code COPAUS1C.cbl} ({@code CPVD}) and {@code COPAUS2C.cbl}, the optional authorization
 * module that the base conversion reported as not installed.
 *
 * <p>The source kept the authorizations in IMS and the fraud reports in Db2, so
 * {@code COPAUS1C} drove two resource managers by hand: it linked {@code COPAUS2C} to write
 * Db2, replaced the IMS segment only when that returned success, and rolled IMS back
 * otherwise. Both stores are ordinary tables here, so the pair is one local transaction.</p>
 *
 * <p>Safe deviations from the source:</p>
 * <ul>
 *   <li>the authorization key is carried at its full fourteen digit width. The source moved it
 *       through {@code X(08)} COMMAREA fields, which cannot separate two authorizations that
 *       share a date and the leading time digits, and the shipped data contains such pairs;</li>
 *   <li>paging is keyset in both directions, so F7 walks the whole way back. The source kept a
 *       twenty entry page anchor array, overwrote the current entry while paging forward and
 *       could therefore never return past the previous page;</li>
 *   <li>the fraud toggle states the intended outcome instead of flipping whatever it finds, so
 *       a repeated submission cannot silently reverse a colleague's decision, and it is
 *       refused when the authorization was not read successfully. The source toggled
 *       unconditionally, outside any error gate;</li>
 *   <li>the fraud row records the flag actually stored and the report date actually used. The
 *       source wrote the requested action into {@code AUTH_FRAUD} and always stamped
 *       {@code CURRENT DATE}, so the row disagreed with the segment it described;</li>
 *   <li>every mark and removal is written to the audit trail with the acting user.</li>
 * </ul>
 */
@Service
public class PendingAuthService {

    /** {@code SEL0001} through {@code SEL0005}: the summary map lists five authorizations. */
    public static final int PAGE_SIZE = 5;

    /** Ordering is byte ordinal over fourteen digits, so this sorts after every real key. */
    private static final String HIGH_KEY = "99999999999999";

    /** {@code PA-AUTH-RESP-CODE} value the {@code PA-AUTH-APPROVED} condition name tests. */
    private static final String APPROVED_RESPONSE = "00";

    /**
     * {@code WS-DECLINE-REASON-TABLE} of {@code COPAUS1C.cbl} lines 58-67, verbatim. The source
     * searched it with {@code SEARCH ALL} and rendered {@code code-description}; a miss produced
     * {@code 9999-ERROR}.
     */
    private static final Map<String, String> DECLINE_REASONS = declineReasons();

    /**
     * {@code PA-MATCH-STATUS} condition names of {@code CIPAUDTY.cpy}. The source printed the
     * single byte; the target resolves it, which is the whole point of the screen.
     */
    private static final Map<String, String> MATCH_STATUSES = Map.of(
            "P", "Pending",
            "D", "Authorization declined",
            "E", "Pending, expired",
            "M", "Matched with a transaction");

    /** {@code PA-AUTH-FRAUD} condition names of {@code CIPAUDTY.cpy}. */
    private static final Map<String, String> FRAUD_STATUSES = Map.of(
            PendingAuthDetail.FRAUD_CONFIRMED, "Confirmed fraud",
            PendingAuthDetail.FRAUD_REMOVED, "Report removed");

    /**
     * ISO 8583 entry modes. The source displayed the raw two digits; the codes are resolved
     * here for the same reason the match status is.
     */
    private static final Map<String, String> POS_ENTRY_MODES = posEntryModes();

    /** The Db2 {@code FRAUD_RPT_DATE} column, which {@code COPAUS2C} set from {@code CURRENT DATE}. */
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * {@code PA-FRAUD-RPT-DATE} X(8). {@code COPAUS2C} filled it with a CICS {@code FORMATTIME}
     * using {@code MMDDYY} and {@code DATESEP}, so the segment carries {@code mm/dd/yy}.
     */
    private static final DateTimeFormatter SEGMENT_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;
    private final AuthFraudRepository fraudReports;
    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final AuditService audit;
    private final Clock clock;

    public PendingAuthService(PendingAuthSummaryRepository summaries,
                              PendingAuthDetailRepository details,
                              AuthFraudRepository fraudReports,
                              CardXrefRepository xrefs,
                              AccountRepository accounts,
                              CustomerRepository customers,
                              AuditService audit,
                              Clock clock) {
        this.summaries = summaries;
        this.details = details;
        this.fraudReports = fraudReports;
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    /** The accounts that have pending authorizations, so the screen can offer a starting point. */
    @Transactional(readOnly = true)
    public List<String> accountsWithAuthorizations() {
        return summaries.findAllByOrderByAccountIdAsc().stream()
                .map(PendingAuthSummary::getAccountId)
                .toList();
    }

    /**
     * {@code COPAUS0C} account block: the {@code PAUTSUM0} root read that the source issued with
     * {@code GU}, plus the two counts the screen derives from the child segments.
     */
    @Transactional(readOnly = true)
    public PendingAuthSummaryView summary(String rawAccountId) {
        String accountId = validateAccountId(rawAccountId);
        PendingAuthSummary root = summaries.findById(accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "Account:" + accountId + " has no pending authorizations...", "accountId"));
        // COPAUS0C took CREDLIM and CASHLIM from the ACCTDAT record and only the balances and
        // totals from IMS. The root segment carries its own copy of the two limits, but it is a
        // cache that can fall behind: account 00000000001 ships with 2022.00 in IMS against
        // 2020.00 on the account record. The account record wins, as it did on the source screen.
        Optional<Account> account = accounts.findById(accountId);
        return new PendingAuthSummaryView(
                root.getAccountId(),
                root.getCustomerId(),
                customerName(root.getCustomerId()),
                account.map(Account::getActiveStatus).orElse(""),
                root.getAuthStatus(),
                root.getAccountStatus(),
                account.map(Account::getCreditLimit).orElseGet(root::getCreditLimit),
                account.map(Account::getCashCreditLimit).orElseGet(root::getCashLimit),
                root.getCreditBalance(),
                root.getCashBalance(),
                root.getApprovedAuthCount(),
                root.getDeclinedAuthCount(),
                root.getApprovedAuthAmount(),
                root.getDeclinedAuthAmount(),
                details.countByIdAccountId(accountId),
                details.countByIdAccountIdAndAuthFraud(accountId, PendingAuthDetail.FRAUD_CONFIRMED));
    }

    /**
     * {@code COPAUS0C} authorization list, five rows at a time. The rows come back in
     * {@code auth_key} order, which is the nine's complement order of the IMS twin chain and so
     * puts the newest authorization first.
     *
     * @param direction {@code next} for F8, {@code prev} for F7, anything else for the first page
     */
    @Transactional(readOnly = true)
    public PageResult<PendingAuthRow> list(String rawAccountId, String filter, boolean fraudOnly,
                                           String cursor, String direction, int pageNumber) {
        String accountId = validateAccountId(rawAccountId);
        if (!summaries.existsById(accountId)) {
            throw ApiException.notFound(
                    "Account:" + accountId + " has no pending authorizations...", "accountId");
        }
        String search = CobolText.trim(filter);
        if (!search.isEmpty() && !CobolText.isAllDigits(search)) {
            throw ApiException.badRequest("Card number filter must be Numeric ...", "filter");
        }

        boolean backward = "prev".equalsIgnoreCase(direction);
        String start = CobolText.trim(cursor);
        List<PendingAuthDetail> found;
        if (backward) {
            found = details.findBackward(accountId, start.isEmpty() ? HIGH_KEY : start, search,
                    fraudOnly, PageRequest.of(0, PAGE_SIZE + 1));
            found = new ArrayList<>(found);
            Collections.reverse(found);
        } else {
            found = details.findForward(accountId, start.isEmpty() ? "" : start, search,
                    fraudOnly, PageRequest.of(0, PAGE_SIZE + 1));
        }

        boolean overflow = found.size() > PAGE_SIZE;
        List<PendingAuthDetail> page = backward
                ? found.subList(Math.max(0, found.size() - PAGE_SIZE), found.size())
                : found.subList(0, Math.min(PAGE_SIZE, found.size()));

        if (page.isEmpty()) {
            return PageResult.of(List.of(), null, null, Math.max(1, pageNumber), false, false,
                    "You have reached the bottom of the page...");
        }

        String firstKey = page.get(0).getAuthKey();
        String lastKey = page.get(page.size() - 1).getAuthKey();
        boolean hasNext = backward
                ? !details.findForward(accountId, lastKey, search, fraudOnly, PageRequest.of(0, 1)).isEmpty()
                : overflow;
        boolean hasPrevious = backward
                ? overflow
                : !details.findBackward(accountId, firstKey, search, fraudOnly, PageRequest.of(0, 1)).isEmpty();

        List<PendingAuthRow> rows = page.stream().map(this::toRow).toList();

        String message = null;
        if (!hasNext) {
            message = "You have reached the bottom of the page...";
        } else if (backward && !hasPrevious) {
            message = "You are already at the top of the page...";
        }
        return PageResult.of(rows, firstKey, lastKey, Math.max(1, pageNumber), hasNext, hasPrevious, message);
    }

    /**
     * {@code COPAUS1C} detail, the {@code GU PAUTSUM0} plus qualified {@code GNP PAUTDTL1} pair.
     * Read only: the source held the IMS position open across the send, which is what made its
     * F8 depend on re-reading the current key first.
     */
    @Transactional(readOnly = true)
    public PendingAuthDetailView detail(String rawAccountId, String rawAuthKey) {
        String accountId = validateAccountId(rawAccountId);
        String authKey = validateAuthKey(rawAuthKey);
        PendingAuthDetail row = details.findById(new PendingAuthDetail.Key(accountId, authKey))
                .orElseThrow(() -> ApiException.notFound("Authorization NOT found...", "authKey"));
        return toDetail(row);
    }

    /**
     * F5 on {@code COPAUS1C}. The source linked {@code COPAUS2C} to write the Db2 fraud row and
     * replaced the IMS segment only when that succeeded, rolling back otherwise; here the two
     * writes are one transaction, so either both stand or neither does.
     *
     * @param request the intended state, rather than the source's blind toggle
     */
    @Transactional
    public FraudMarkResult markFraud(String actor, String rawAccountId, String rawAuthKey,
                                     FraudMarkRequest request) {
        String accountId = validateAccountId(rawAccountId);
        String authKey = validateAuthKey(rawAuthKey);
        PendingAuthDetail row = details.findById(new PendingAuthDetail.Key(accountId, authKey))
                .orElseThrow(() -> ApiException.notFound("Authorization NOT found...", "authKey"));

        boolean confirmed = request != null && request.confirmed();
        String flag = confirmed ? PendingAuthDetail.FRAUD_CONFIRMED : PendingAuthDetail.FRAUD_REMOVED;
        if (flag.equals(row.getAuthFraud())) {
            throw ApiException.conflict(confirmed
                    ? "This authorization is already marked as fraud..."
                    : "This authorization is not marked as fraud...", "confirmed");
        }

        LocalDate today = LocalDate.now(clock);
        String reportDate = today.format(ISO_DATE);
        row.setAuthFraud(flag);
        // The source stamped mm/dd/yy into the IMS segment but wrote CURRENT DATE into the Db2
        // column, so the two could disagree across midnight or a timezone difference. One clock
        // reading fills both here.
        row.setFraudRptDate(today.format(SEGMENT_DATE));
        details.save(row);

        writeFraudReport(actor, row, flag, reportDate);

        String message = confirmed ? "AUTH MARKED FRAUD..." : "AUTH FRAUD REMOVED...";
        audit.success(actor, confirmed ? "AUTH_FRAUD_MARK" : "AUTH_FRAUD_REMOVE",
                "PendingAuthDetail", accountId + "/" + authKey,
                "Card " + maskedCard(row.getCardNumber())
                        + ", amount " + row.getTransactionAmt().toPlainString()
                        + noteSuffix(request));
        return new FraudMarkResult(message, toDetail(row));
    }

    /**
     * The {@code COPAUS2C} write against {@code CARDDEMO.AUTHFRDS}: insert the report row, or
     * update the flag and report date when the same {@code (CARD_NUM, AUTH_TS)} was reported
     * before. The timestamp is built exactly as the source built it, from the original date and
     * the decoded nine's complement time, so a row keeps its identity across marks.
     */
    private void writeFraudReport(String actor, PendingAuthDetail row, String flag, String reportDate) {
        AuthFraud.Key key = new AuthFraud.Key(row.getCardNumber(), authTimestamp(row));
        AuthFraud report = fraudReports.findById(key).orElseGet(() -> {
            AuthFraud created = new AuthFraud(key.getCardNumber(), key.getAuthTs());
            created.setAuthType(row.getAuthType());
            created.setCardExpiryDate(row.getCardExpiryDate());
            created.setMessageType(row.getMessageType());
            created.setMessageSource(row.getMessageSource());
            created.setAuthIdCode(row.getAuthIdCode());
            created.setAuthRespCode(row.getAuthRespCode());
            created.setAuthRespReason(row.getAuthRespReason());
            created.setProcessingCode(row.getProcessingCode());
            created.setTransactionAmt(row.getTransactionAmt());
            created.setApprovedAmt(row.getApprovedAmt());
            created.setMccCode(row.getMccCode());
            created.setAcqrCountryCode(row.getAcqrCountryCode());
            created.setPosEntryMode(parsePosEntryMode(row.getPosEntryMode()));
            created.setMerchantId(row.getMerchantId());
            // MERCHANT_NAME is a Db2 VARCHAR(22) whose length host variable was the constant 22,
            // so the source stored the trailing spaces. The stored image keeps them.
            created.setMerchantName(row.getMerchantName());
            created.setMerchantCity(row.getMerchantCity());
            created.setMerchantState(row.getMerchantState());
            created.setMerchantZip(row.getMerchantZip());
            created.setTransactionId(row.getTransactionId());
            created.setAccountId(row.getAccountId());
            created.setCustomerId(customerIdFor(row.getCardNumber()));
            return created;
        });
        report.setMatchStatus(row.getMatchStatus());
        report.setAuthFraud(flag);
        report.setFraudRptDate(reportDate);
        report.setReportedBy(actor);
        fraudReports.save(report);
    }

    /* ------------------------------------------------------------------ presentation */

    private PendingAuthRow toRow(PendingAuthDetail row) {
        return new PendingAuthRow(
                row.getAuthKey(),
                displayDate(row.getAuthOrigDate()),
                displayTime(row.getAuthOrigTime()),
                row.getCardNumber(),
                row.getTransactionAmt(),
                row.getAuthRespCode(),
                responseText(row.getAuthRespCode()),
                row.getMatchStatus(),
                matchStatusText(row.getMatchStatus()),
                row.getAuthFraud(),
                fraudStatusText(row.getAuthFraud()),
                CobolText.trim(row.getMerchantName()));
    }

    private PendingAuthDetailView toDetail(PendingAuthDetail row) {
        String accountId = row.getAccountId();
        String authKey = row.getAuthKey();
        return new PendingAuthDetailView(
                accountId,
                authKey,
                row.getCardNumber(),
                displayDate(row.getAuthOrigDate()),
                displayTime(row.getAuthOrigTime()),
                row.getAuthOrigDate(),
                row.getAuthOrigTime(),
                CobolText.trim(row.getAuthType()),
                displayExpiry(row.getCardExpiryDate()),
                CobolText.trim(row.getMessageType()),
                CobolText.trim(row.getMessageSource()),
                CobolText.trim(row.getAuthIdCode()),
                row.getAuthRespCode(),
                responseText(row.getAuthRespCode()),
                row.getAuthRespReason(),
                declineReasonText(row.getAuthRespReason()),
                row.getProcessingCode(),
                row.getTransactionAmt(),
                row.getApprovedAmt(),
                CobolText.trim(row.getMccCode()),
                CobolText.trim(row.getAcqrCountryCode()),
                row.getPosEntryMode(),
                posEntryModeText(row.getPosEntryMode()),
                CobolText.trim(row.getMerchantId()),
                CobolText.trim(row.getMerchantName()),
                CobolText.trim(row.getMerchantCity()),
                CobolText.trim(row.getMerchantState()),
                CobolText.trim(row.getMerchantZip()),
                CobolText.trim(row.getTransactionId()),
                row.getMatchStatus(),
                matchStatusText(row.getMatchStatus()),
                row.getAuthFraud(),
                fraudStatusText(row.getAuthFraud()),
                CobolText.trim(row.getFraudRptDate()),
                neighbourKey(details.findNextInParent(accountId, authKey, PageRequest.of(0, 1))),
                neighbourKey(details.findPreviousInParent(accountId, authKey, PageRequest.of(0, 1))));
    }

    private static String neighbourKey(List<PendingAuthDetail> found) {
        return found.isEmpty() ? null : found.get(0).getAuthKey();
    }

    /** {@code AUTHRSP}: the source showed {@code A} in green or {@code D} in red. */
    private static String responseText(String respCode) {
        return APPROVED_RESPONSE.equals(CobolText.trim(respCode)) ? "Approved" : "Declined";
    }

    /** {@code BUILD-AUTH-RSN}: the table text, or the source's {@code 9999-ERROR} on a miss. */
    private static String declineReasonText(String reason) {
        String code = CobolText.trim(reason);
        return DECLINE_REASONS.getOrDefault(code, "ERROR");
    }

    private static String matchStatusText(String matchStatus) {
        String code = CobolText.trim(matchStatus);
        return MATCH_STATUSES.getOrDefault(code, code.isEmpty() ? "" : "Unknown");
    }

    private static String fraudStatusText(String authFraud) {
        String code = CobolText.trim(authFraud);
        return FRAUD_STATUSES.getOrDefault(code, code.isEmpty() ? "Never reported" : "Unknown");
    }

    private static String posEntryModeText(String posEntryMode) {
        String code = CobolText.trim(posEntryMode);
        return POS_ENTRY_MODES.getOrDefault(code, code.isEmpty() ? "" : "Unknown");
    }

    /** {@code PA-AUTH-ORIG-DATE} is {@code yymmdd}; the maps sliced it into {@code mm/dd/yy}. */
    private static String displayDate(String yymmdd) {
        String value = CobolText.trim(yymmdd);
        if (value.length() != 6) {
            return value;
        }
        return value.substring(2, 4) + "/" + value.substring(4, 6) + "/" + value.substring(0, 2);
    }

    /** {@code PA-AUTH-ORIG-TIME} is {@code hhmmss}, overlaid onto {@code 00:00:00} by the map. */
    private static String displayTime(String hhmmss) {
        String value = CobolText.trim(hhmmss);
        if (value.length() != 6) {
            return value;
        }
        return value.substring(0, 2) + ":" + value.substring(2, 4) + ":" + value.substring(4, 6);
    }

    /**
     * {@code CRDEXP}: the source sliced {@code PA-CARD-EXPIRY-DATE} into {@code mm/yy} without
     * validating either half, and the shipped data does contain months outside 01-12.
     */
    private static String displayExpiry(String mmyy) {
        String value = CobolText.trim(mmyy);
        if (value.length() != 4) {
            return value;
        }
        return value.substring(0, 2) + "/" + value.substring(2, 4);
    }

    /**
     * The {@code AUTH_TS} primary key half of {@code AUTHFRDS}, built the way {@code COPAUS2C}
     * built it: the two digit year, month and day of {@code PA-AUTH-ORIG-DATE}, then the decoded
     * nine's complement time as {@code hh.mm.ss.sss} with three trailing zeroes for microseconds.
     */
    static String authTimestamp(PendingAuthDetail row) {
        String date = CobolText.padLeftZero(CobolText.trim(row.getAuthOrigDate()), 6);
        String time = CobolText.padLeftZero(Integer.toString(Math.abs(row.getAuthTimeValue())), 9);
        return date.substring(0, 2) + "-" + date.substring(2, 4) + "-" + date.substring(4, 6)
                + " " + time.substring(0, 2) + "." + time.substring(2, 4) + "." + time.substring(4, 6)
                + "." + time.substring(6, 9) + "000";
    }

    /* ------------------------------------------------------------------ edits and helpers */

    /**
     * {@code PROCESS-ENTER-KEY} of {@code COPAUS0C.cbl}: the account id must be supplied and
     * numeric. The two messages are the source literals.
     */
    static String validateAccountId(String rawAccountId) {
        String value = CobolText.trim(rawAccountId);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Please enter Acct Id...", "accountId");
        }
        if (!CobolText.isAllDigits(value) || value.length() > 11) {
            throw ApiException.badRequest("Acct Id must be Numeric ...", "accountId");
        }
        return CobolText.padLeftZero(value, 11);
    }

    /** The fourteen digit {@code PAUT9CTS} carried at full width, not the source's {@code X(08)}. */
    static String validateAuthKey(String rawAuthKey) {
        String value = CobolText.trim(rawAuthKey);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Please select an authorization...", "authKey");
        }
        if (!CobolText.isAllDigits(value) || value.length() != 14) {
            throw ApiException.badRequest("Authorization key must be fourteen digits ...", "authKey");
        }
        return value;
    }

    /**
     * {@code CNAME} of {@code COPAUS0C}: first name, the middle initial and the last name, each
     * part delimited by spaces exactly as the source {@code STRING} statement assembled them.
     */
    private String customerName(String customerId) {
        return customers.findById(CobolText.trim(customerId))
                .map(PendingAuthService::assembleName)
                .orElse("");
    }

    private static String assembleName(Customer customer) {
        String first = CobolText.trim(customer.getFirstName());
        String middle = CobolText.trim(customer.getMiddleName());
        String last = CobolText.trim(customer.getLastName());
        StringBuilder name = new StringBuilder(first);
        if (!middle.isEmpty()) {
            name.append(' ').append(middle.charAt(0));
        }
        if (!last.isEmpty()) {
            name.append(' ').append(last);
        }
        return name.toString().trim();
    }

    private String customerIdFor(String cardNumber) {
        return xrefs.findById(CobolText.trim(cardNumber))
                .map(x -> x.getCustomerId())
                .orElse("");
    }

    private static short parsePosEntryMode(String posEntryMode) {
        String code = CobolText.trim(posEntryMode);
        return CobolText.isAllDigits(code) && !code.isEmpty() ? Short.parseShort(code) : (short) 0;
    }

    /** FR-CARD-007 style redaction: an audit detail never carries a full card number. */
    private static String maskedCard(String cardNumber) {
        String value = CobolText.trim(cardNumber);
        return value.length() <= 4 ? value : "************" + value.substring(value.length() - 4);
    }

    private static String noteSuffix(FraudMarkRequest request) {
        String note = request == null ? "" : CobolText.trim(request.note());
        return note.isEmpty() ? "" : ", note " + note;
    }

    private static Map<String, String> declineReasons() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("0000", "APPROVED");
        table.put("3100", "INVALID CARD");
        table.put("4100", "INSUFFICNT FUND");
        table.put("4200", "CARD NOT ACTIVE");
        table.put("4300", "ACCOUNT CLOSED");
        table.put("4400", "EXCED DAILY LMT");
        table.put("5100", "CARD FRAUD");
        table.put("5200", "MERCHANT FRAUD");
        table.put("5300", "LOST CARD");
        table.put("9000", "UNKNOWN");
        return Collections.unmodifiableMap(table);
    }

    private static Map<String, String> posEntryModes() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("00", "Unknown");
        table.put("01", "Manual key entry");
        table.put("02", "Magnetic stripe");
        table.put("05", "Chip");
        table.put("07", "Contactless chip");
        table.put("80", "Chip fallback to stripe");
        table.put("81", "Electronic commerce");
        table.put("90", "Magnetic stripe, full track");
        table.put("91", "Contactless magnetic stripe");
        return Collections.unmodifiableMap(table);
    }
}
