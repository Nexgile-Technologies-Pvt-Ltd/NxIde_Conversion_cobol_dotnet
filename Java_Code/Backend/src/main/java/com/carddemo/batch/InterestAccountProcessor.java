package com.carddemo.batch;

import com.carddemo.common.CobolText;
import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.CategoryBalance;
import com.carddemo.domain.DisclosureGroup;
import com.carddemo.domain.InterestCharge;
import com.carddemo.domain.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CategoryBalanceRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.InterestChargeRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * One account's interest calculation, in its own transaction so FR-BATCH-007 holds: an account's
 * interest transactions and its balance update commit together, and the unique
 * {@code (cycle, account, type, category)} charge identity stops a restart double charging.
 */
@Service
public class InterestAccountProcessor {

    /** Interest transactions are written with type {@code 01} and category {@code 0005}. */
    public static final String INTEREST_TYPE = "01";
    public static final String INTEREST_CATEGORY = "0005";
    private static final String INTEREST_SOURCE = "System";

    /** Monthly interest divides the annual rate by 1200. */
    private static final BigDecimal MONTHS_PERCENT = new BigDecimal("1200");

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final CategoryBalanceRepository categoryBalances;
    private final DisclosureGroupRepository disclosureGroups;
    private final TransactionRepository transactions;
    private final InterestChargeRepository charges;

    public InterestAccountProcessor(AccountRepository accounts, CardXrefRepository xrefs,
                                    CategoryBalanceRepository categoryBalances,
                                    DisclosureGroupRepository disclosureGroups,
                                    TransactionRepository transactions,
                                    InterestChargeRepository charges) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.categoryBalances = categoryBalances;
        this.disclosureGroups = disclosureGroups;
        this.transactions = transactions;
        this.charges = charges;
    }

    /** Result of one account: how many interest transactions were written and their total. */
    public record Result(int transactionsWritten, BigDecimal totalInterest, boolean accountUpdated) {
    }

    /**
     * Applies interest for one account.
     *
     * <p>For every category balance with a non-zero applicable rate the source computes
     * {@code balance * rate / 1200} without {@code ROUNDED}, so the receiving two-decimal field
     * truncates; {@link RoundingMode#DOWN} reproduces that. One transaction is written per
     * qualifying category, then the accumulated interest is added to the account balance and both
     * cycle accumulators are reset.</p>
     *
     * <p>FR-BATCH-007 also fixes the legacy end-of-file quirk that skipped the final account: here
     * every account, including the last, is updated.</p>
     *
     * @param sequenceStart the running six digit suffix appended to the ten character cycle id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result process(String cycleId, String accountId, int sequenceStart) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null) {
            return new Result(0, BigDecimal.ZERO, false);
        }

        // The source obtains one cross-reference by the non-unique account alternate key. The
        // lowest card number makes that deterministic (decision DEC-ONL-002).
        Optional<CardXref> xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(accountId);
        String cardNumber = xref.map(CardXref::getCardNumber).orElse("");

        List<CategoryBalance> balances =
                categoryBalances.findByIdAccountIdOrderByIdTypeCodeAscIdCategoryCodeAsc(accountId);

        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2);
        int written = 0;
        int suffix = sequenceStart;
        String timestamp = TransactionService.currentTimestamp();

        for (CategoryBalance balance : balances) {
            BigDecimal rate = rateFor(account.getGroupId(), balance.getTypeCode(), balance.getCategoryCode());
            if (rate.signum() == 0) {
                continue;
            }
            InterestCharge.Key chargeKey = new InterestCharge.Key(cycleId, accountId,
                    balance.getTypeCode(), balance.getCategoryCode());
            if (charges.existsById(chargeKey)) {
                continue;
            }

            BigDecimal monthlyInterest = balance.getBalance().multiply(rate)
                    .divide(MONTHS_PERCENT, 2, RoundingMode.DOWN);
            totalInterest = totalInterest.add(monthlyInterest);

            suffix++;
            String transactionId = cycleId + CobolText.padLeftZero(Integer.toString(suffix), 6);

            Transaction interest = new Transaction();
            interest.setTransactionId(transactionId);
            interest.setTypeCode(INTEREST_TYPE);
            interest.setCategoryCode(INTEREST_CATEGORY);
            interest.setSource(INTEREST_SOURCE);
            interest.setDescription("Int. for a/c " + accountId);
            interest.setAmount(monthlyInterest);
            interest.setMerchantId("000000000");
            interest.setMerchantName("");
            interest.setMerchantCity("");
            interest.setMerchantZip("");
            interest.setCardNumber(cardNumber);
            interest.setOrigTs(timestamp);
            interest.setProcTs(timestamp);
            transactions.save(interest);
            written++;

            charges.save(new InterestCharge(cycleId, accountId, balance.getTypeCode(),
                    balance.getCategoryCode(), monthlyInterest, transactionId));
        }

        account.setCurrBal(account.getCurrBal().add(totalInterest));
        account.setCurrCycCredit(BigDecimal.ZERO);
        account.setCurrCycDebit(BigDecimal.ZERO);
        accounts.save(account);

        return new Result(written, totalInterest, true);
    }

    /**
     * Group-specific rate first, then the literal {@code DEFAULT} group. The source relies on VSAM
     * status 23 for that fallback; the fixture accounts have a blank group, so they follow the
     * default path.
     */
    private BigDecimal rateFor(String groupId, String typeCode, String categoryCode) {
        String group = CobolText.trim(groupId);
        if (!group.isEmpty()) {
            Optional<DisclosureGroup> specific = disclosureGroups.findById(
                    new DisclosureGroup.Key(group, typeCode, categoryCode));
            if (specific.isPresent()) {
                return specific.get().getInterestRate();
            }
        }
        return disclosureGroups
                .findById(new DisclosureGroup.Key(DisclosureGroup.DEFAULT_GROUP, typeCode, categoryCode))
                .map(DisclosureGroup::getInterestRate)
                .orElse(BigDecimal.ZERO);
    }
}
