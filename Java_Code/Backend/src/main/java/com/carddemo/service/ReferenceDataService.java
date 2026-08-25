package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionType;
import com.carddemo.dto.OperationsDtos.CategoryBalanceDto;
import com.carddemo.dto.OperationsDtos.DisclosureGroupDto;
import com.carddemo.dto.OperationsDtos.TransactionCategoryDto;
import com.carddemo.dto.OperationsDtos.TransactionTypeDto;
import com.carddemo.dto.PageResult;
import com.carddemo.repository.CategoryBalanceRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reference data: transaction types, categories, disclosure rates and category balances.
 *
 * <p>Transaction type maintenance is the optional Db2 module: {@code COTRTLIC.cbl} (list
 * {@code CTLI}, seven rows, type and description filters, row actions {@code U}/{@code D}) and
 * {@code COTRTUPC.cbl} (maintenance {@code CTTU}, create or update selected by existence).</p>
 *
 * <p>FR-OPT-002: the type must be a two character numeric non-zero value and the description must
 * be non-blank, at most 50 characters, ASCII alphanumeric and spaces.</p>
 * <p>FR-OPT-003: mutation uses trimmed lengths, optimistic concurrency and valid references.</p>
 */
@Service
public class ReferenceDataService {

    /** {@code WS-MAX-SCREEN-LINES VALUE 7} in {@code COTRTLIC}. */
    public static final int TYPE_PAGE_SIZE = 7;

    private static final String HIGH_KEY = "zz";

    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final DisclosureGroupRepository disclosureGroups;
    private final CategoryBalanceRepository categoryBalances;
    private final TransactionRepository transactions;
    private final AuditService audit;

    public ReferenceDataService(TransactionTypeRepository types, TransactionCategoryRepository categories,
                                DisclosureGroupRepository disclosureGroups,
                                CategoryBalanceRepository categoryBalances,
                                TransactionRepository transactions, AuditService audit) {
        this.types = types;
        this.categories = categories;
        this.disclosureGroups = disclosureGroups;
        this.categoryBalances = categoryBalances;
        this.transactions = transactions;
        this.audit = audit;
    }

    /** All transaction types, used to populate pickers on the transaction screens. */
    @Transactional(readOnly = true)
    public List<TransactionTypeDto> allTypes() {
        return types.findAllByOrderByTypeCodeAsc().stream()
                .map(t -> new TransactionTypeDto(t.getTypeCode(), t.getDescription(), t.getVersion()))
                .toList();
    }

    /** All transaction categories. */
    @Transactional(readOnly = true)
    public List<TransactionCategoryDto> allCategories() {
        return categories.findAllByOrderByIdTypeCodeAscIdCategoryCodeAsc().stream()
                .map(c -> new TransactionCategoryDto(c.getTypeCode(), c.getCategoryCode(),
                        c.getDescription(), c.getVersion()))
                .toList();
    }

    /** Categories of one type. */
    @Transactional(readOnly = true)
    public List<TransactionCategoryDto> categoriesOfType(String typeCode) {
        return categories.findByIdTypeCodeOrderByIdCategoryCodeAsc(CobolText.trim(typeCode)).stream()
                .map(c -> new TransactionCategoryDto(c.getTypeCode(), c.getCategoryCode(),
                        c.getDescription(), c.getVersion()))
                .toList();
    }

    /** All disclosure interest rates. */
    @Transactional(readOnly = true)
    public List<DisclosureGroupDto> allDisclosureGroups() {
        return disclosureGroups.findAllByOrderByIdGroupIdAscIdTypeCodeAscIdCategoryCodeAsc().stream()
                .map(d -> new DisclosureGroupDto(d.getGroupId(), d.getTypeCode(), d.getCategoryCode(),
                        d.getInterestRate()))
                .toList();
    }

    /** Category balances, optionally restricted to one account. */
    @Transactional(readOnly = true)
    public List<CategoryBalanceDto> categoryBalances(String accountId) {
        List<com.carddemo.domain.CategoryBalance> rows = CobolText.isBlank(accountId)
                ? categoryBalances.findAllByOrderByIdAccountIdAscIdTypeCodeAscIdCategoryCodeAsc()
                : categoryBalances.findByIdAccountIdOrderByIdTypeCodeAscIdCategoryCodeAsc(
                        AccountService.validateAccountId(accountId));
        return rows.stream()
                .map(b -> new CategoryBalanceDto(b.getAccountId(), b.getTypeCode(), b.getCategoryCode(),
                        b.getBalance()))
                .toList();
    }

    /** {@code COTRTLIC} list: seven rows, optional exact type filter and description LIKE filter. */
    @Transactional(readOnly = true)
    public PageResult<TransactionTypeDto> listTypes(String typeFilter, String descriptionFilter,
                                                    String cursor, String direction, int pageNumber) {
        String type = CobolText.trim(typeFilter);
        if (!type.isEmpty()) {
            if (!CobolText.isAllDigits(type) || type.length() > 2) {
                throw ApiException.badRequest("Type must be a 2 digit number", "typeFilter");
            }
            type = CobolText.padLeftZero(type, 2);
            if ("00".equals(type)) {
                type = "";
            }
        }
        String description = CobolText.trim(descriptionFilter);

        boolean backward = "prev".equalsIgnoreCase(direction);
        String start = CobolText.trim(cursor);
        List<TransactionType> found;
        if (backward) {
            found = types.findBackward(start.isEmpty() ? HIGH_KEY : start, type, description,
                    PageRequest.of(0, TYPE_PAGE_SIZE + 1));
            found = new ArrayList<>(found);
            Collections.reverse(found);
        } else {
            found = types.findForward(start.isEmpty() ? "" : start, type, description,
                    PageRequest.of(0, TYPE_PAGE_SIZE + 1));
        }

        boolean overflow = found.size() > TYPE_PAGE_SIZE;
        List<TransactionType> page = backward
                ? found.subList(Math.max(0, found.size() - TYPE_PAGE_SIZE), found.size())
                : found.subList(0, Math.min(TYPE_PAGE_SIZE, found.size()));

        if (page.isEmpty()) {
            return PageResult.of(List.of(), null, null, Math.max(1, pageNumber), false, false,
                    "No records found for this search condition.");
        }

        String firstKey = page.get(0).getTypeCode();
        String lastKey = page.get(page.size() - 1).getTypeCode();
        boolean hasNext = backward
                ? !types.findForward(lastKey, type, description, PageRequest.of(0, 1)).isEmpty()
                : overflow;
        boolean hasPrevious = backward
                ? overflow
                : !types.findBackward(firstKey, type, description, PageRequest.of(0, 1)).isEmpty();

        List<TransactionTypeDto> rows = page.stream()
                .map(t -> new TransactionTypeDto(t.getTypeCode(), t.getDescription(), t.getVersion()))
                .toList();
        return PageResult.of(rows, firstKey, lastKey, Math.max(1, pageNumber), hasNext, hasPrevious, null);
    }

    /**
     * {@code COTRTUPC} maintenance: create when the type is new, update when it already exists.
     */
    @Transactional
    public TransactionTypeDto saveType(String actor, String rawTypeCode, String rawDescription, long version) {
        String typeCode = validateTypeCode(rawTypeCode);
        String description = validateDescription(rawDescription);

        TransactionType existing = types.findById(typeCode).orElse(null);
        if (existing == null) {
            TransactionType created = new TransactionType(typeCode, description);
            types.save(created);
            audit.success(actor, "TRAN_TYPE_ADD", "TransactionType", typeCode, description);
            return new TransactionTypeDto(created.getTypeCode(), created.getDescription(), created.getVersion());
        }
        if (version != existing.getVersion()) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }
        existing.setDescription(description);
        try {
            types.saveAndFlush(existing);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }
        audit.success(actor, "TRAN_TYPE_UPDATE", "TransactionType", typeCode, description);
        return new TransactionTypeDto(existing.getTypeCode(), existing.getDescription(), existing.getVersion());
    }

    /**
     * Delete a transaction type. FR-OPT-003: the reference must still be valid afterwards, so a
     * type still owning categories cannot be removed.
     */
    @Transactional
    public String deleteType(String actor, String rawTypeCode, boolean confirmed) {
        String typeCode = validateTypeCode(rawTypeCode);
        if (!confirmed) {
            throw ApiException.badRequest("Please confirm the deletion of type " + typeCode + " ...", "confirmed");
        }
        TransactionType existing = types.findById(typeCode)
                .orElseThrow(() -> ApiException.notFound("Transaction type NOT found...", "typeCode"));
        if (categories.countByIdTypeCode(typeCode) > 0) {
            throw ApiException.conflict(
                    "Transaction type still has categories and cannot be deleted ...", "typeCode");
        }
        // Removing a type still in use would leave those transactions with an unresolvable
        // description on the view screen, so the reference is checked before the delete.
        if (transactions.countByTypeCode(typeCode) > 0) {
            throw ApiException.conflict(
                    "Transaction type is used by existing transactions and cannot be deleted ...", "typeCode");
        }
        types.delete(existing);
        audit.success(actor, "TRAN_TYPE_DELETE", "TransactionType", typeCode, null);
        return "Transaction type " + typeCode + " has been deleted ...";
    }

    /** Category maintenance keyed by {@code (type, category)}. */
    @Transactional
    public TransactionCategoryDto saveCategory(String actor, String rawTypeCode, String rawCategoryCode,
                                               String rawDescription, long version) {
        String typeCode = validateTypeCode(rawTypeCode);
        String categoryCode = validateCategoryCode(rawCategoryCode);
        String description = validateDescription(rawDescription);

        if (!types.existsById(typeCode)) {
            throw ApiException.badRequest("Transaction type " + typeCode + " does not exist...", "typeCode");
        }
        TransactionCategory.Key key = new TransactionCategory.Key(typeCode, categoryCode);
        TransactionCategory existing = categories.findById(key).orElse(null);
        if (existing == null) {
            TransactionCategory created = new TransactionCategory(typeCode, categoryCode, description);
            categories.save(created);
            audit.success(actor, "TRAN_CATEGORY_ADD", "TransactionCategory",
                    typeCode + "/" + categoryCode, description);
            return new TransactionCategoryDto(typeCode, categoryCode, description, created.getVersion());
        }
        if (version != existing.getVersion()) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }
        existing.setDescription(description);
        categories.saveAndFlush(existing);
        audit.success(actor, "TRAN_CATEGORY_UPDATE", "TransactionCategory",
                typeCode + "/" + categoryCode, description);
        return new TransactionCategoryDto(typeCode, categoryCode, description, existing.getVersion());
    }

    /**
     * Category deletion, on the same terms as type deletion: confirmation is required and a
     * category still referenced by a transaction is refused.
     *
     * <p>Without this a category created in error could never be removed, and the type that owns
     * it could never be deleted either, because {@link #deleteType} refuses while categories
     * remain.</p>
     */
    @Transactional
    public String deleteCategory(String actor, String rawTypeCode, String rawCategoryCode, boolean confirmed) {
        String typeCode = validateTypeCode(rawTypeCode);
        String categoryCode = validateCategoryCode(rawCategoryCode);
        if (!confirmed) {
            throw ApiException.badRequest(
                    "Please confirm the deletion of category " + typeCode + "/" + categoryCode + " ...",
                    "confirmed");
        }
        TransactionCategory existing = categories.findById(new TransactionCategory.Key(typeCode, categoryCode))
                .orElseThrow(() -> ApiException.notFound("Transaction category NOT found...", "categoryCode"));
        if (transactions.countByTypeCodeAndCategoryCode(typeCode, categoryCode) > 0) {
            throw ApiException.conflict(
                    "Transaction category is used by existing transactions and cannot be deleted ...",
                    "categoryCode");
        }
        categories.delete(existing);
        audit.success(actor, "TRAN_CATEGORY_DELETE", "TransactionCategory", typeCode + "/" + categoryCode, null);
        return "Transaction category " + typeCode + "/" + categoryCode + " has been deleted ...";
    }

    /** Two numeric characters, non-zero. */
    private static String validateTypeCode(String rawTypeCode) {
        String value = CobolText.trim(rawTypeCode);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Type CD can NOT be empty...", "typeCode");
        }
        if (!CobolText.isAllDigits(value) || value.length() > 2) {
            throw ApiException.badRequest("Type CD must be a 2 digit number", "typeCode");
        }
        String padded = CobolText.padLeftZero(value, 2);
        if ("00".equals(padded)) {
            throw ApiException.badRequest("Type CD must be a non-zero 2 digit number", "typeCode");
        }
        return padded;
    }

    private static String validateCategoryCode(String rawCategoryCode) {
        String value = CobolText.trim(rawCategoryCode);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Category CD can NOT be empty...", "categoryCode");
        }
        if (!CobolText.isAllDigits(value) || value.length() > 4) {
            throw ApiException.badRequest("Category CD must be a 4 digit number", "categoryCode");
        }
        return CobolText.padLeftZero(value, 4);
    }

    /** Non-blank, at most 50 characters, ASCII alphanumeric and spaces only. */
    private static String validateDescription(String rawDescription) {
        String value = CobolText.trim(rawDescription);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Description can NOT be empty...", "description");
        }
        if (value.length() > 50) {
            throw ApiException.badRequest("Description can be a maximum of 50 characters", "description");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == ' ';
            if (!allowed) {
                throw ApiException.badRequest(
                        "Description can have numbers, alphabets and spaces only.", "description");
            }
        }
        return value;
    }
}
