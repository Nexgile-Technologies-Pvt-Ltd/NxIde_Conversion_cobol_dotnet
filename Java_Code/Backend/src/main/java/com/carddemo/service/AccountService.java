package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.dto.AccountDtos.AccountDetail;
import com.carddemo.dto.AccountDtos.AccountSummary;
import com.carddemo.dto.AccountDtos.AccountUpdateRequest;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.validation.CobolDateValidator;
import com.carddemo.validation.CobolFieldValidator;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Account view and account update. COBOL sources {@code COACTVWC.cbl} (transaction {@code CAVW})
 * and {@code COACTUPC.cbl} (transaction {@code CAUP}).
 *
 * <p>View reproduces the source read order cross-reference then account then customer. Update
 * reproduces the normative twenty-three step validation order documented for {@code COACTUPC},
 * then commits the account and customer changes as a single unit of work with optimistic
 * concurrency (FR-ACCT-007, FR-ACCT-008).</p>
 */
@Service
public class AccountService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final CardXrefRepository xrefs;
    private final CobolFieldValidator fields;
    private final CobolDateValidator dates;
    private final AuditService audit;

    public AccountService(AccountRepository accounts, CustomerRepository customers, CardXrefRepository xrefs,
                          CobolFieldValidator fields, CobolDateValidator dates, AuditService audit) {
        this.accounts = accounts;
        this.customers = customers;
        this.xrefs = xrefs;
        this.fields = fields;
        this.dates = dates;
        this.audit = audit;
    }

    /**
     * {@code COACTVWC}: validate the account id, then resolve cross-reference, account and
     * customer in that order.
     *
     * <p>FR-ACCT-001 keeps the source validation; the defect where the source continued with stale
     * buffers after a failed lookup is not reproduced: each missing record stops the flow.</p>
     */
    @Transactional(readOnly = true)
    public AccountDetail view(String rawAccountId) {
        String accountId = validateAccountId(rawAccountId);

        CardXref xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(accountId).orElse(null);

        Account account = accounts.findById(accountId).orElseThrow(() ->
                ApiException.notFound("Account " + accountId + " not found in Account Master file.", "accountId"));

        String customerId = xref != null ? xref.getCustomerId() : null;
        Customer customer = customerId == null ? null : customers.findById(customerId).orElse(null);
        if (customer == null) {
            throw ApiException.notFound("Customer record not found for account " + accountId + ".", "accountId");
        }
        return toDetail(account, customer, xref == null ? "" : xref.getCardNumber());
    }

    /** Paged account list; a modern convenience over the legacy single-key screen. */
    @Transactional(readOnly = true)
    public List<AccountSummary> list(int limit) {
        return accounts.findAllByOrderByAccountIdAsc().stream()
                .limit(limit)
                .map(account -> {
                    Optional<CardXref> xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(account.getAccountId());
                    String name = xref.flatMap(x -> customers.findById(x.getCustomerId()))
                            .map(c -> (CobolText.trim(c.getFirstName()) + " " + CobolText.trim(c.getLastName())).trim())
                            .orElse("");
                    return new AccountSummary(account.getAccountId(), account.getActiveStatus(),
                            account.getCurrBal(), account.getCreditLimit(), account.getOpenDate(),
                            account.getExpirationDate(), account.getGroupId(), name);
                })
                .toList();
    }

    /**
     * {@code COACTUPC} save. Validation order is normative and matches the source orchestration:
     * status, open date, credit limit, expiry date, cash limit, reissue date, current balance,
     * cycle credit, cycle debit, SSN, DOB, FICO, names, address line 1, state, ZIP, city, country,
     * phone 1, phone 2, EFT, primary holder, then the state / first two ZIP cross-check.
     */
    @Transactional
    public AccountDetail update(String actor, AccountUpdateRequest request) {
        String accountId = validateAccountId(request.accountId());

        Account account = accounts.findById(accountId).orElseThrow(() ->
                ApiException.notFound("Account " + accountId + " not found in Account Master file.", "accountId"));
        CardXref xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(accountId).orElseThrow(() ->
                ApiException.notFound("Account " + accountId + " has no card cross-reference.", "accountId"));
        Customer customer = customers.findById(xref.getCustomerId()).orElseThrow(() ->
                ApiException.notFound("Customer record not found for account " + accountId + ".", "accountId"));

        // FR-ACCT-007: refuse to overwrite a later change.
        if (request.accountVersion() != account.getVersion()
                || request.customerVersion() != customer.getVersion()) {
            throw ApiException.conflict(
                    "Record changed by some one else. Please review and try again ...");
        }

        //  1. status
        reject(fields.yesNo("Account Status", request.activeStatus()), "activeStatus");
        //  2. open date
        validateDate("Open Date", request.openDate(), "openDate");
        //  3. credit limit
        reject(fields.signedAmount("Credit Limit", request.creditLimit()), "creditLimit");
        //  4. expiry date
        validateDate("Expiry Date", request.expirationDate(), "expirationDate");
        //  5. cash credit limit
        reject(fields.signedAmount("Cash Credit Limit", request.cashCreditLimit()), "cashCreditLimit");
        //  6. reissue date
        validateDate("Reissue Date", request.reissueDate(), "reissueDate");
        //  7. current balance
        reject(fields.signedAmount("Current Balance", request.currentBalance()), "currentBalance");
        //  8. current cycle credit
        reject(fields.signedAmount("Current Cycle Credit", request.currentCycleCredit()), "currentCycleCredit");
        //  9. current cycle debit
        reject(fields.signedAmount("Current Cycle Debit", request.currentCycleDebit()), "currentCycleDebit");
        // 10. SSN
        String ssn = digitsOnly(request.ssn());
        reject(fields.ssn(part(ssn, 0, 3), part(ssn, 3, 5), part(ssn, 5, 9)), "ssn");
        // 11. date of birth, which must be strictly in the past
        validateDateOfBirth(request.dateOfBirth());
        // 12. FICO
        reject(fields.fico(request.ficoScore()), "ficoScore");
        // 13. names
        reject(fields.alphaRequired("First Name", request.firstName()), "firstName");
        if (!CobolText.isBlank(request.middleName())) {
            reject(fields.alphaRequired("Middle Name", request.middleName()), "middleName");
        }
        reject(fields.alphaRequired("Last Name", request.lastName()), "lastName");
        // 14. address line 1
        reject(fields.mandatory("Address Line 1", request.addressLine1()), "addressLine1");
        // 15. state
        reject(fields.stateCode(request.stateCode()), "stateCode");
        // 16. ZIP
        reject(fields.numericRequired("Zip", CobolText.trim(request.zipCode()), 5), "zipCode");
        // 17. city (address line 3 in storage)
        reject(fields.mandatory("City", request.city()), "city");
        // 18. country
        reject(fields.mandatory("Country", request.countryCode()), "countryCode");
        // 19-20. phones
        String phone1 = digitsOnly(request.phone1());
        reject(fields.usPhone("Phone 1", part(phone1, 0, 3), part(phone1, 3, 6), part(phone1, 6, 10)), "phone1");
        String phone2 = digitsOnly(request.phone2());
        reject(fields.usPhone("Phone 2", part(phone2, 0, 3), part(phone2, 3, 6), part(phone2, 6, 10)), "phone2");
        // 21. EFT account id has no semantic edit in the source beyond field width.
        // 22. primary card holder indicator
        reject(fields.yesNo("Primary Card Holder", request.primaryCardHolderIndicator()),
                "primaryCardHolderIndicator");
        // 23. state and the first two ZIP digits must be a valid combination
        reject(fields.stateZipCombination(request.stateCode(), request.zipCode()), "zipCode");

        account.setActiveStatus(CobolText.trim(request.activeStatus()).toUpperCase());
        account.setOpenDate(CobolText.trim(request.openDate()));
        account.setCreditLimit(amount(request.creditLimit()));
        account.setExpirationDate(CobolText.trim(request.expirationDate()));
        account.setCashCreditLimit(amount(request.cashCreditLimit()));
        account.setReissueDate(CobolText.trim(request.reissueDate()));
        account.setCurrBal(amount(request.currentBalance()));
        account.setCurrCycCredit(amount(request.currentCycleCredit()));
        account.setCurrCycDebit(amount(request.currentCycleDebit()));
        account.setGroupId(clip(request.groupId(), 10));

        customer.setSsn(ssn);
        customer.setDateOfBirth(CobolText.trim(request.dateOfBirth()));
        customer.setFicoScore(Integer.parseInt(CobolText.trim(request.ficoScore())));
        customer.setFirstName(clip(request.firstName(), 25));
        customer.setMiddleName(clip(request.middleName(), 25));
        customer.setLastName(clip(request.lastName(), 25));
        customer.setAddrLine1(clip(request.addressLine1(), 50));
        customer.setAddrLine2(clip(request.addressLine2(), 50));
        customer.setAddrLine3(clip(request.city(), 50));
        customer.setAddrStateCd(clip(request.stateCode(), 2).toUpperCase());
        customer.setAddrCountryCd(clip(request.countryCode(), 3).toUpperCase());
        // FR-ACCT-009 note: the screen shows five ZIP cells, storage keeps ten characters.
        customer.setAddrZip(clip(request.zipCode(), 10));
        customer.setPhoneNum1(formatPhone(phone1));
        customer.setPhoneNum2(formatPhone(phone2));
        customer.setGovtIssuedId(clip(request.governmentIssuedId(), 20));
        customer.setEftAccountId(clip(request.eftAccountId(), 10));
        customer.setPriCardHolderInd(clip(request.primaryCardHolderIndicator(), 1).toUpperCase());

        try {
            // FR-ACCT-008: one transaction covers both writes, so a failure leaves neither changed.
            accounts.saveAndFlush(account);
            customers.saveAndFlush(customer);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict("Record changed by some one else. Please review and try again ...");
        }

        audit.success(actor, "ACCOUNT_UPDATE", "Account", accountId,
                "Account and customer " + customer.getCustomerId() + " updated");
        return toDetail(account, customer, xref.getCardNumber());
    }

    /**
     * {@code 1210-EDIT-ACCOUNT}: the account number must be supplied, eleven digits and non-zero.
     */
    public static String validateAccountId(String rawAccountId) {
        String value = CobolText.trim(rawAccountId);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Account Number must be supplied.", "accountId");
        }
        if (!CobolText.isAllDigits(value) || value.length() > 11 || Long.parseLong(value) == 0) {
            throw ApiException.badRequest(
                    "Account Number if supplied must be a 11 digit Non-Zero Number", "accountId");
        }
        return CobolText.padLeftZero(value, 11);
    }

    private void validateDate(String label, String value, String field) {
        String v = CobolText.trim(value);
        if (v.isEmpty()) {
            throw ApiException.badRequest(label + " must be supplied.", field);
        }
        CobolDateValidator.Result result = dates.validateIso(label, v);
        if (!result.valid()) {
            throw ApiException.badRequest(result.message(), field);
        }
        if (!dates.isRealCalendarDate(v)) {
            throw ApiException.badRequest(label + " - Not a valid date...", field);
        }
    }

    private void validateDateOfBirth(String value) {
        String v = CobolText.trim(value);
        if (v.length() != 10 || v.charAt(4) != '-' || v.charAt(7) != '-') {
            throw ApiException.badRequest("Date of Birth should be in format YYYY-MM-DD", "dateOfBirth");
        }
        CobolDateValidator.Result result = dates.validateDateOfBirth("Date of Birth",
                v.substring(0, 4), v.substring(5, 7), v.substring(8, 10));
        if (!result.valid()) {
            throw ApiException.badRequest(result.message(), "dateOfBirth");
        }
    }

    private static void reject(String message, String field) {
        if (message != null) {
            throw ApiException.badRequest(message, field);
        }
    }

    private static BigDecimal amount(String value) {
        BigDecimal parsed = CobolFieldValidator.parseNumvalC(value);
        return parsed == null ? BigDecimal.ZERO : parsed.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Strips formatting so {@code (908)119-8310} and {@code 9081198310} behave identically. */
    private static String digitsOnly(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** The customer file stores phones in the fixed {@code (999)999-9999} presentation. */
    private static String formatPhone(String digits) {
        if (digits.length() != 10) {
            return "";
        }
        return "(" + digits.substring(0, 3) + ")" + digits.substring(3, 6) + "-" + digits.substring(6);
    }

    private static String part(String value, int from, int to) {
        if (value.length() < to) {
            return "";
        }
        return value.substring(from, to);
    }

    private static String clip(String value, int width) {
        String v = CobolText.trim(value);
        return v.length() > width ? v.substring(0, width) : v;
    }

    static AccountDetail toDetail(Account account, Customer customer, String cardNumber) {
        return new AccountDetail(
                account.getAccountId(), account.getActiveStatus(), account.getCurrBal(),
                account.getCreditLimit(), account.getCashCreditLimit(), account.getOpenDate(),
                account.getExpirationDate(), account.getReissueDate(), account.getCurrCycCredit(),
                account.getCurrCycDebit(), account.getAddrZip(), account.getGroupId(), account.getVersion(),
                customer.getCustomerId(), customer.getFirstName(), customer.getMiddleName(),
                customer.getLastName(), customer.getSsn(), customer.getDateOfBirth(), customer.getFicoScore(),
                customer.getAddrLine1(), customer.getAddrLine2(), customer.getAddrLine3(),
                customer.getAddrStateCd(), customer.getAddrZip(), customer.getAddrCountryCd(),
                customer.getPhoneNum1(), customer.getPhoneNum2(), customer.getGovtIssuedId(),
                customer.getEftAccountId(), customer.getPriCardHolderInd(), customer.getVersion(),
                cardNumber);
    }
}
