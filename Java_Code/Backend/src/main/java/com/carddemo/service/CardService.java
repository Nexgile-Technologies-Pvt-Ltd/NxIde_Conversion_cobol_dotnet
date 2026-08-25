package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.Card;
import com.carddemo.dto.CardDtos.CardDetail;
import com.carddemo.dto.CardDtos.CardRow;
import com.carddemo.dto.CardDtos.CardUpdateRequest;
import com.carddemo.dto.PageResult;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.validation.CobolDateValidator;
import com.carddemo.validation.CobolFieldValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Card list, card view and card update. COBOL sources {@code COCRDLIC.cbl} ({@code CCLI}),
 * {@code COCRDSLC.cbl} ({@code CCDL}) and {@code COCRDUPC.cbl} ({@code CCUP}).
 *
 * <p>Safe deviations required by the requirements page:</p>
 * <ul>
 *   <li>FR-CARD-002 page availability is derived from the next matching row, not from an
 *       unfiltered look-ahead;</li>
 *   <li>FR-CARD-004 the entered account is verified against the card record instead of being
 *       validated and then ignored;</li>
 *   <li>FR-CARD-006 the complete expiry date including the hidden day is validated before save;</li>
 *   <li>FR-CARD-007 CVV is preserved, never displayed, never overwritten with an unassigned value.</li>
 * </ul>
 */
@Service
public class CardService {

    /** {@code WS-MAX-SCREEN-LINES}: the card list shows seven rows. */
    public static final int PAGE_SIZE = 7;

    private static final String LOW_KEY = "";
    private static final String HIGH_KEY = "zzzzzzzzzzzzzzzz";

    private final CardRepository cards;
    private final AccountRepository accounts;
    private final CobolFieldValidator fields;
    private final CobolDateValidator dates;
    private final AuditService audit;

    public CardService(CardRepository cards, AccountRepository accounts, CobolFieldValidator fields,
                       CobolDateValidator dates, AuditService audit) {
        this.cards = cards;
        this.accounts = accounts;
        this.fields = fields;
        this.dates = dates;
        this.audit = audit;
    }

    /**
     * {@code COCRDLIC} browse. Both filters are optional, must be numeric at their fixed width when
     * supplied, and combine with AND.
     *
     * @param direction {@code next} for F8, {@code prev} for F7, anything else for the first page
     */
    @Transactional(readOnly = true)
    public PageResult<CardRow> list(String accountFilter, String cardFilter, String cursor,
                                    String direction, int pageNumber) {
        String account = normaliseFilter(accountFilter, 11, "Account Filter");
        String card = normaliseFilter(cardFilter, 16, "Card Filter");

        List<Card> found;
        boolean backward = "prev".equalsIgnoreCase(direction);
        String start = CobolText.trim(cursor);

        if (backward) {
            String before = start.isEmpty() ? HIGH_KEY : start;
            found = cards.findBackward(before, account, card, PageRequest.of(0, PAGE_SIZE + 1));
            found = new ArrayList<>(found);
            Collections.reverse(found);
        } else {
            String from = start.isEmpty() ? LOW_KEY : start;
            found = cards.findForward(from, account, card, PageRequest.of(0, PAGE_SIZE + 1));
        }

        // One extra row was requested; its presence proves another matching row exists on that side.
        boolean overflow = found.size() > PAGE_SIZE;
        List<Card> page = backward
                ? found.subList(Math.max(0, found.size() - PAGE_SIZE), found.size())
                : found.subList(0, Math.min(PAGE_SIZE, found.size()));

        if (page.isEmpty()) {
            return PageResult.of(List.of(), null, null, Math.max(1, pageNumber), false, false,
                    "No records found for this search condition.");
        }

        String firstKey = page.get(0).getCardNumber();
        String lastKey = page.get(page.size() - 1).getCardNumber();

        // FR-CARD-002: both flags come from an actual matching row, never from an unfiltered peek.
        boolean hasNext = backward
                ? !cards.findForward(lastKey, account, card, PageRequest.of(0, 1)).isEmpty()
                : overflow;
        boolean hasPrevious = backward
                ? overflow
                : !cards.findBackward(firstKey, account, card, PageRequest.of(0, 1)).isEmpty();

        List<CardRow> rows = page.stream()
                .map(c -> new CardRow(c.getCardNumber(), c.getAccountId(), c.getActiveStatus(),
                        c.getEmbossedName(), c.getExpirationDate()))
                .toList();

        String message = hasNext ? null : "You have reached the bottom of the page...";
        if (backward && !hasPrevious) {
            message = "You are already at the top of the page...";
        }
        return PageResult.of(rows, firstKey, lastKey, Math.max(1, pageNumber), hasNext, hasPrevious, message);
    }

    /** All cards belonging to one account; used by the account and transaction screens. */
    @Transactional(readOnly = true)
    public List<CardRow> byAccount(String accountId) {
        String id = AccountService.validateAccountId(accountId);
        return cards.findByAccountIdOrderByCardNumberAsc(id).stream()
                .map(c -> new CardRow(c.getCardNumber(), c.getAccountId(), c.getActiveStatus(),
                        c.getEmbossedName(), c.getExpirationDate()))
                .toList();
    }

    /**
     * {@code COCRDSLC} card view. Both the account and the card must be supplied, numeric and
     * non-zero; the card must actually belong to the account (FR-CARD-004).
     */
    @Transactional(readOnly = true)
    public CardDetail view(String rawAccountId, String rawCardNumber) {
        String accountId = AccountService.validateAccountId(rawAccountId);
        String cardNumber = validateCardNumber(rawCardNumber);
        Card card = cards.findById(cardNumber).orElseThrow(() ->
                ApiException.notFound("Did not find this card in cards database", "cardNumber"));
        if (!card.getAccountId().equals(accountId)) {
            throw ApiException.notFound("Did not find this card in cards database", "cardNumber");
        }
        return toDetail(card);
    }

    /** Card view by card number only, used after selecting a row on the list screen. */
    @Transactional(readOnly = true)
    public CardDetail viewByCard(String rawCardNumber) {
        String cardNumber = validateCardNumber(rawCardNumber);
        return cards.findById(cardNumber)
                .map(CardService::toDetail)
                .orElseThrow(() -> ApiException.notFound("Did not find this card in cards database", "cardNumber"));
    }

    /**
     * {@code COCRDUPC} save. Validation order: embossed name required and letters/spaces only,
     * status Y or N, month 1-12, year 1950-2099. The retained day plus the new month and year must
     * form a real calendar date before anything is written.
     */
    @Transactional
    public CardDetail update(String actor, CardUpdateRequest request) {
        String accountId = AccountService.validateAccountId(request.accountId());
        String cardNumber = validateCardNumber(request.cardNumber());

        Card card = cards.findById(cardNumber).orElseThrow(() ->
                ApiException.notFound("Did not find this card in cards database", "cardNumber"));
        if (!card.getAccountId().equals(accountId)) {
            throw ApiException.notFound("Did not find this card in cards database", "cardNumber");
        }
        if (request.version() != card.getVersion()) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }

        String name = CobolText.trim(request.embossedName());
        String nameError = fields.alphaRequired("Card Name", name);
        if (nameError != null) {
            throw ApiException.badRequest(
                    name.isEmpty() ? "Card Name must be supplied." : "Card Name can only contain alphabets and spaces",
                    "embossedName");
        }

        String status = CobolText.trim(request.activeStatus()).toUpperCase();
        if (status.isEmpty()) {
            throw ApiException.badRequest("Card Active Status must be supplied.", "activeStatus");
        }
        if (!"Y".equals(status) && !"N".equals(status)) {
            throw ApiException.badRequest("Card Active Status must be Y or N.", "activeStatus");
        }

        String month = CobolText.trim(request.expirationMonth());
        if (month.isEmpty() || !CobolText.isAllDigits(month)) {
            throw ApiException.badRequest("Card Expiry Month must be supplied.", "expirationMonth");
        }
        int monthValue = Integer.parseInt(month);
        if (monthValue < 1 || monthValue > 12) {
            throw ApiException.badRequest("Card Expiry Month must be between 1 and 12.", "expirationMonth");
        }

        String year = CobolText.trim(request.expirationYear());
        if (year.isEmpty() || !CobolText.isAllDigits(year)) {
            throw ApiException.badRequest("Card Expiry Year must be supplied.", "expirationYear");
        }
        int yearValue = Integer.parseInt(year);
        if (yearValue < 1950 || yearValue > 2099) {
            throw ApiException.badRequest("Card Expiry Year must be between 1950 and 2099.", "expirationYear");
        }

        // FR-CARD-006: the hidden day the source preserved must still produce a real date.
        String day = expirationDay(card.getExpirationDate());
        String newExpiry = String.format("%04d-%02d-%s", yearValue, monthValue, day);
        if (!dates.isRealCalendarDate(newExpiry)) {
            throw ApiException.badRequest("Card Expiry Date is not a valid calendar date.", "expirationMonth");
        }

        card.setEmbossedName(name.length() > 50 ? name.substring(0, 50) : name);
        card.setActiveStatus(status);
        card.setExpirationDate(newExpiry);
        // FR-CARD-007: CVV is not part of this use case and is left exactly as stored.

        try {
            cards.saveAndFlush(card);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }
        audit.success(actor, "CARD_UPDATE", "Card", maskCard(cardNumber), "Status " + status);
        return toDetail(card);
    }

    /** Card number must be supplied, numeric and non-zero, at most sixteen digits. */
    public static String validateCardNumber(String rawCardNumber) {
        String value = CobolText.trim(rawCardNumber);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Card number must be supplied.", "cardNumber");
        }
        if (!CobolText.isAllDigits(value) || value.length() > 16) {
            throw ApiException.badRequest(
                    "Card number if supplied must be a 16 digit Non-Zero Number", "cardNumber");
        }
        String padded = CobolText.padLeftZero(value, 16);
        if (padded.chars().allMatch(c -> c == '0')) {
            throw ApiException.badRequest(
                    "Card number if supplied must be a 16 digit Non-Zero Number", "cardNumber");
        }
        return padded;
    }

    private String normaliseFilter(String raw, int width, String label) {
        String value = CobolText.trim(raw);
        if (value.isEmpty()) {
            return "";
        }
        if (!CobolText.isAllDigits(value) || value.length() > width) {
            throw ApiException.badRequest(label + " must be a " + width + " digit number",
                    width == 11 ? "accountFilter" : "cardFilter");
        }
        return CobolText.padLeftZero(value, width);
    }

    /** The BMS update map hid the day; it is read back from storage and preserved. */
    private static String expirationDay(String expirationDate) {
        String value = CobolText.trim(expirationDate);
        if (value.length() == 10) {
            return value.substring(8, 10);
        }
        return "01";
    }

    static CardDetail toDetail(Card card) {
        String expiry = CobolText.trim(card.getExpirationDate());
        String year = expiry.length() == 10 ? expiry.substring(0, 4) : "";
        String month = expiry.length() == 10 ? expiry.substring(5, 7) : "";
        String day = expiry.length() == 10 ? expiry.substring(8, 10) : "";
        return new CardDetail(card.getCardNumber(), card.getAccountId(), card.getEmbossedName(),
                card.getActiveStatus(), expiry, month, day, year, card.getVersion());
    }

    /** Audit text must never carry a full card number (NFR-006). */
    static String maskCard(String cardNumber) {
        String value = CobolText.trim(cardNumber);
        if (value.length() < 4) {
            return "****";
        }
        return "************" + value.substring(value.length() - 4);
    }
}
