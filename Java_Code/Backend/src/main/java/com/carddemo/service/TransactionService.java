package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionType;
import com.carddemo.dto.PageResult;
import com.carddemo.dto.TransactionDtos.TransactionAddRequest;
import com.carddemo.dto.TransactionDtos.TransactionDetail;
import com.carddemo.dto.TransactionDtos.TransactionPrefill;
import com.carddemo.dto.TransactionDtos.TransactionRow;
import com.carddemo.dto.TransactionDtos.TransactionWriteResult;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import com.carddemo.validation.CobolDateValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Transaction list, view and add. COBOL sources {@code COTRN00C.cbl} ({@code CT00}),
 * {@code COTRN01C.cbl} ({@code CT01}) and {@code COTRN02C.cbl} ({@code CT02}).
 *
 * <p>Safe deviations: the view does not take an update lock merely to display a record
 * (FR-TRAN-003); mutation is gated on an explicit validated-state check rather than on control-flow
 * termination (FR-TRAN-007); identifiers come from an atomic allocator (FR-TRAN-009); and the
 * insert runs in one transaction (FR-TRAN-011).</p>
 */
@Service
public class TransactionService {

    /** The transaction list shows ten rows. */
    public static final int PAGE_SIZE = 10;

    /** COBOL processing timestamp presentation: {@code yyyy-MM-dd HH:mm:ss.SSSSSS}, 26 characters. */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static final String HIGH_KEY = "zzzzzzzzzzzzzzzz";

    private final TransactionRepository transactions;
    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final CardXrefRepository xrefs;
    private final CardRepository cards;
    private final SequenceService sequences;
    private final CobolDateValidator dates;
    private final AuditService audit;

    public TransactionService(TransactionRepository transactions, TransactionTypeRepository types,
                              TransactionCategoryRepository categories, CardXrefRepository xrefs,
                              CardRepository cards, SequenceService sequences,
                              CobolDateValidator dates, AuditService audit) {
        this.transactions = transactions;
        this.types = types;
        this.categories = categories;
        this.xrefs = xrefs;
        this.cards = cards;
        this.sequences = sequences;
        this.dates = dates;
        this.audit = audit;
    }

    /**
     * {@code COTRN00C} browse. A blank filter starts from low values; a supplied id must be numeric
     * even though the stored field is {@code X(16)}.
     */
    @Transactional(readOnly = true)
    public PageResult<TransactionRow> list(String filter, String cursor, String direction, int pageNumber) {
        String search = CobolText.trim(filter);
        if (!search.isEmpty() && !CobolText.isAllDigits(search)) {
            throw ApiException.badRequest("Tran ID must be Numeric ...", "filter");
        }

        boolean backward = "prev".equalsIgnoreCase(direction);
        String start = CobolText.trim(cursor);
        List<Transaction> found;
        if (backward) {
            found = transactions.findBackward(start.isEmpty() ? HIGH_KEY : start, search,
                    PageRequest.of(0, PAGE_SIZE + 1));
            found = new ArrayList<>(found);
            Collections.reverse(found);
        } else {
            found = transactions.findForward(start.isEmpty() ? "" : start, search,
                    PageRequest.of(0, PAGE_SIZE + 1));
        }

        boolean overflow = found.size() > PAGE_SIZE;
        List<Transaction> page = backward
                ? found.subList(Math.max(0, found.size() - PAGE_SIZE), found.size())
                : found.subList(0, Math.min(PAGE_SIZE, found.size()));

        if (page.isEmpty()) {
            return PageResult.of(List.of(), null, null, Math.max(1, pageNumber), false, false,
                    "You have reached the bottom of the page...");
        }

        String firstKey = page.get(0).getTransactionId();
        String lastKey = page.get(page.size() - 1).getTransactionId();
        boolean hasNext = backward
                ? !transactions.findForward(lastKey, search, PageRequest.of(0, 1)).isEmpty()
                : overflow;
        boolean hasPrevious = backward
                ? overflow
                : !transactions.findBackward(firstKey, search, PageRequest.of(0, 1)).isEmpty();

        List<TransactionRow> rows = page.stream()
                .map(t -> new TransactionRow(t.getTransactionId(), displayDate(t.getOrigTs()),
                        clip(t.getDescription(), 26), t.getAmount()))
                .toList();

        String message = null;
        if (!hasNext) {
            message = "You have reached the bottom of the page...";
        } else if (backward && !hasPrevious) {
            message = "You are already at the top of the page...";
        }
        return PageResult.of(rows, firstKey, lastKey, Math.max(1, pageNumber), hasNext, hasPrevious, message);
    }

    /**
     * {@code COTRN01C} detail. Read-only: FR-TRAN-003 forbids the source's {@code READ UPDATE} on a
     * view-only screen.
     */
    @Transactional(readOnly = true)
    public TransactionDetail view(String rawTransactionId) {
        String id = CobolText.trim(rawTransactionId);
        if (id.isEmpty()) {
            throw ApiException.badRequest("Tran ID can NOT be empty...", "transactionId");
        }
        Transaction transaction = transactions.findById(CobolText.padLeftZero(id, 16))
                .or(() -> transactions.findById(id))
                .orElseThrow(() -> ApiException.notFound("Transaction ID NOT found...", "transactionId"));
        return toDetail(transaction);
    }

    /** All transactions of one card, used by the card and statement screens. */
    @Transactional(readOnly = true)
    public List<TransactionRow> byCard(String rawCardNumber) {
        String cardNumber = CardService.validateCardNumber(rawCardNumber);
        return transactions.findByCardNumberOrderByTransactionIdAsc(cardNumber).stream()
                .map(t -> new TransactionRow(t.getTransactionId(), displayDate(t.getOrigTs()),
                        clip(t.getDescription(), 26), t.getAmount()))
                .toList();
    }

    /**
     * F5 on the add screen: copy every non-key value from the greatest transaction id
     * ({@code COTRN02C} lines 471-495).
     */
    @Transactional(readOnly = true)
    public TransactionPrefill prefillFromLatest() {
        Transaction latest = transactions.findFirstByOrderByTransactionIdDesc()
                .orElseThrow(() -> ApiException.notFound("No transactions available to copy..."));
        return new TransactionPrefill(latest.getTypeCode(), latest.getCategoryCode(), latest.getSource(),
                latest.getDescription(), latest.getAmount().toPlainString(),
                isoDate(latest.getOrigTs()), isoDate(latest.getProcTs()),
                latest.getMerchantId(), latest.getMerchantName(), latest.getMerchantCity(),
                latest.getMerchantZip());
    }

    /**
     * {@code COTRN02C} add.
     *
     * <p>Key resolution first (account wins over card), then the required-field chain, then the
     * format checks, in the exact source order. Nothing is written until every check has passed
     * and the caller has confirmed (FR-TRAN-007).</p>
     */
    @Transactional
    public TransactionWriteResult add(String actor, TransactionAddRequest request) {
        String accountId = CobolText.trim(request.accountId());
        String cardNumber = CobolText.trim(request.cardNumber());
        String resolvedAccount;
        String resolvedCard;

        if (!accountId.isEmpty()) {
            if (!CobolText.isAllDigits(accountId)) {
                throw ApiException.badRequest("Account ID must be Numeric ...", "accountId");
            }
            resolvedAccount = CobolText.padLeftZero(accountId, 11);
            CardXref xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(resolvedAccount)
                    .orElseThrow(() -> ApiException.notFound("Account ID NOT found...", "accountId"));
            // Source behaviour: when both are supplied the account wins and the card is overwritten.
            resolvedCard = xref.getCardNumber();
        } else if (!cardNumber.isEmpty()) {
            if (!CobolText.isAllDigits(cardNumber)) {
                throw ApiException.badRequest("Card Number must be Numeric ...", "cardNumber");
            }
            resolvedCard = CobolText.padLeftZero(cardNumber, 16);
            CardXref xref = xrefs.findById(resolvedCard)
                    .orElseThrow(() -> ApiException.notFound("Card Number NOT found...", "cardNumber"));
            resolvedAccount = xref.getAccountId();
        } else {
            throw ApiException.badRequest("Account or Card Number must be entered ...", "accountId");
        }

        // Required-field chain, in source order.
        requireField(request.typeCode(), "Type CD can NOT be empty...", "typeCode");
        requireField(request.categoryCode(), "Category CD can NOT be empty...", "categoryCode");
        requireField(request.source(), "Source can NOT be empty...", "source");
        requireField(request.description(), "Description can NOT be empty...", "description");
        requireField(request.amount(), "Amount can NOT be empty...", "amount");
        requireField(request.originDate(), "Orig Date can NOT be empty...", "originDate");
        requireField(request.processDate(), "Proc Date can NOT be empty...", "processDate");
        requireField(request.merchantId(), "Merchant ID can NOT be empty...", "merchantId");
        requireField(request.merchantName(), "Merchant Name can NOT be empty...", "merchantName");
        requireField(request.merchantCity(), "Merchant City can NOT be empty...", "merchantCity");
        requireField(request.merchantZip(), "Merchant Zip can NOT be empty...", "merchantZip");

        String typeCode = CobolText.trim(request.typeCode());
        if (!CobolText.isAllDigits(typeCode)) {
            throw ApiException.badRequest("Type CD must be Numeric...", "typeCode");
        }
        String categoryCode = CobolText.trim(request.categoryCode());
        if (!CobolText.isAllDigits(categoryCode)) {
            throw ApiException.badRequest("Category CD must be Numeric...", "categoryCode");
        }

        BigDecimal amount = parseSignedAmount(request.amount());

        String originDate = CobolText.trim(request.originDate());
        if (!isStructuralIsoDate(originDate)) {
            throw ApiException.badRequest("Orig Date should be in format YYYY-MM-DD", "originDate");
        }
        String processDate = CobolText.trim(request.processDate());
        if (!isStructuralIsoDate(processDate)) {
            throw ApiException.badRequest("Proc Date should be in format YYYY-MM-DD", "processDate");
        }
        if (!dates.isRealCalendarDate(originDate)) {
            throw ApiException.badRequest("Orig Date - Not a valid date...", "originDate");
        }
        if (!dates.isRealCalendarDate(processDate)) {
            throw ApiException.badRequest("Proc Date - Not a valid date...", "processDate");
        }

        String merchantId = CobolText.trim(request.merchantId());
        if (!CobolText.isAllDigits(merchantId)) {
            throw ApiException.badRequest("Merchant ID must be Numeric...", "merchantId");
        }

        // FR-TRAN-007: the explicit validated-state gate, replacing the legacy send-then-return flow.
        if (!request.confirmed()) {
            throw ApiException.badRequest(
                    "Please confirm to add this transaction (Y/N)...", "confirmed");
        }

        Transaction transaction = new Transaction();
        transaction.setTransactionId(sequences.nextTransactionId());
        transaction.setTypeCode(CobolText.padLeftZero(typeCode, 2));
        transaction.setCategoryCode(CobolText.padLeftZero(categoryCode, 4));
        transaction.setSource(clip(request.source(), 10));
        transaction.setDescription(clip(request.description(), 100));
        transaction.setAmount(amount);
        transaction.setMerchantId(CobolText.padLeftZero(merchantId, 9));
        transaction.setMerchantName(clip(request.merchantName(), 50));
        transaction.setMerchantCity(clip(request.merchantCity(), 50));
        transaction.setMerchantZip(clip(request.merchantZip(), 10));
        transaction.setCardNumber(resolvedCard);
        transaction.setOrigTs(timestampFromDate(originDate));
        transaction.setProcTs(timestampFromDate(processDate));
        transactions.save(transaction);

        audit.success(actor, "TRANSACTION_ADD", "Transaction", transaction.getTransactionId(),
                "Account " + resolvedAccount + ", amount " + amount.toPlainString());

        return new TransactionWriteResult(transaction.getTransactionId(),
                "Transaction added successfully. Your Tran ID is " + transaction.getTransactionId() + ".", null);
    }

    /** Maps a stored transaction to the {@code COTRN1A} view, resolving the reference descriptions. */
    TransactionDetail toDetail(Transaction transaction) {
        String typeDescription = types.findById(transaction.getTypeCode())
                .map(TransactionType::getDescription).orElse("");
        String categoryDescription = categories
                .findById(new TransactionCategory.Key(transaction.getTypeCode(), transaction.getCategoryCode()))
                .map(TransactionCategory::getDescription).orElse("");
        String accountId = xrefs.findById(transaction.getCardNumber())
                .map(CardXref::getAccountId)
                .orElseGet(() -> cards.findById(transaction.getCardNumber())
                        .map(Card::getAccountId).orElse(""));

        return new TransactionDetail(transaction.getTransactionId(), transaction.getCardNumber(),
                transaction.getTypeCode(), typeDescription, transaction.getCategoryCode(), categoryDescription,
                transaction.getSource(), transaction.getDescription(), transaction.getAmount(),
                isoDate(transaction.getOrigTs()), isoDate(transaction.getProcTs()),
                transaction.getMerchantId(), transaction.getMerchantName(), transaction.getMerchantCity(),
                transaction.getMerchantZip(), accountId);
    }

    /** Latest transactions for the dashboard. */
    @Transactional(readOnly = true)
    public List<TransactionRow> recent(int limit) {
        return transactions.findBackward(HIGH_KEY, "", PageRequest.of(0, limit)).stream()
                .map(t -> new TransactionRow(t.getTransactionId(), displayDate(t.getOrigTs()),
                        clip(t.getDescription(), 60), t.getAmount()))
                .toList();
    }

    /**
     * The exact amount grammar of the add screen: sign, eight digits, a decimal point and two
     * digits, checked before the value is converted.
     */
    static BigDecimal parseSignedAmount(String raw) {
        String value = CobolText.trim(raw);
        boolean negative = value.startsWith("-");
        if (value.startsWith("+") || value.startsWith("-")) {
            value = value.substring(1);
        }
        int dot = value.indexOf('.');
        if (dot < 0) {
            throw ApiException.badRequest("Amount should be in format -99999999.99", "amount");
        }
        String whole = value.substring(0, dot);
        String cents = value.substring(dot + 1);
        if (whole.isEmpty() || whole.length() > 9 || !CobolText.isAllDigits(whole)
                || cents.length() != 2 || !CobolText.isAllDigits(cents)) {
            throw ApiException.badRequest("Amount should be in format -99999999.99", "amount");
        }
        BigDecimal amount = new BigDecimal(whole + "." + cents).setScale(2, RoundingMode.UNNECESSARY);
        return negative ? amount.negate() : amount;
    }

    private static void requireField(String value, String message, String field) {
        if (CobolText.isBlank(value)) {
            throw ApiException.badRequest(message, field);
        }
    }

    /** Structural check only: {@code NNNN-NN-NN}. The calendar check follows separately. */
    static boolean isStructuralIsoDate(String value) {
        return value.length() == 10
                && CobolText.isAllDigits(value.substring(0, 4))
                && value.charAt(4) == '-'
                && CobolText.isAllDigits(value.substring(5, 7))
                && value.charAt(7) == '-'
                && CobolText.isAllDigits(value.substring(8, 10));
    }

    /** Builds the 26-character COBOL timestamp used by the transaction record. */
    static String timestampFromDate(String isoDate) {
        return CobolText.padRight(isoDate + " 00:00:00.000000", 26);
    }

    /** Current timestamp in the COBOL 26-character presentation. */
    public static String currentTimestamp() {
        return CobolText.padRight(LocalDateTime.now().format(TIMESTAMP), 26);
    }

    /** First ten characters of a stored timestamp, which is the date part. */
    static String isoDate(String timestamp) {
        String value = CobolText.trim(timestamp);
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    /** {@code MM/DD/YY} presentation used by the ten list rows. */
    static String displayDate(String timestamp) {
        String iso = isoDate(timestamp);
        if (iso.length() != 10) {
            return iso;
        }
        return iso.substring(5, 7) + "/" + iso.substring(8, 10) + "/" + iso.substring(2, 4);
    }

    private static String clip(String value, int width) {
        String v = CobolText.trim(value);
        return v.length() > width ? v.substring(0, width) : v;
    }

    /** Used by the report and statement services to resolve an account from a card. */
    Optional<String> accountForCard(String cardNumber) {
        return xrefs.findById(cardNumber).map(CardXref::getAccountId);
    }
}
