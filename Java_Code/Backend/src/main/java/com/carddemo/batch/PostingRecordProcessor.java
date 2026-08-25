package com.carddemo.batch;

import com.carddemo.common.CobolText;
import com.carddemo.domain.Account;
import com.carddemo.domain.BatchReject;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.CategoryBalance;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.PostingLedger;
import com.carddemo.domain.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.BatchRejectRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CategoryBalanceRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.repository.PostingLedgerRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * One posting unit of work, kept in its own bean so the {@code REQUIRES_NEW} boundary actually
 * applies (a self invocation inside {@link PostingService} would bypass the transaction proxy).
 *
 * <p>FR-BATCH-005: the category balance, the account mutation, the transaction write and the
 * {@code (run, record-number)} ledger entry either all commit for one input row or none do.</p>
 */
@Service
public class PostingRecordProcessor {

    private final DailyTransactionRepository dailyTransactions;
    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CategoryBalanceRepository categoryBalances;
    private final TransactionRepository transactions;
    private final BatchRejectRepository rejects;
    private final PostingLedgerRepository ledger;

    public PostingRecordProcessor(DailyTransactionRepository dailyTransactions, CardXrefRepository xrefs,
                                  AccountRepository accounts, CategoryBalanceRepository categoryBalances,
                                  TransactionRepository transactions, BatchRejectRepository rejects,
                                  PostingLedgerRepository ledger) {
        this.dailyTransactions = dailyTransactions;
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.categoryBalances = categoryBalances;
        this.transactions = transactions;
        this.rejects = rejects;
        this.ledger = ledger;
    }

    /**
     * Validates and posts or rejects one daily record, following the {@code CBTRN02C} order.
     *
     * @return true when the record was accepted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(Long runId, Long dailyId) {
        DailyTransaction daily = dailyTransactions.findById(dailyId).orElseThrow();
        PostingLedger.Key ledgerKey = new PostingLedger.Key(runId, daily.getRecordNumber());
        Optional<PostingLedger> existing = ledger.findById(ledgerKey);
        if (existing.isPresent()) {
            return PostingLedger.OUTCOME_POSTED.equals(existing.get().getOutcome());
        }

        // 1. resolve the card in the cross-reference
        Optional<CardXref> xref = xrefs.findById(daily.getCardNumber());
        if (xref.isEmpty()) {
            return reject(runId, daily, BatchReject.REASON_INVALID_CARD, BatchReject.TEXT_INVALID_CARD);
        }

        // 2. resolve the account
        Optional<Account> found = accounts.findById(xref.get().getAccountId());
        if (found.isEmpty()) {
            return reject(runId, daily, BatchReject.REASON_ACCOUNT_NOT_FOUND,
                    BatchReject.TEXT_ACCOUNT_NOT_FOUND);
        }
        Account account = found.get();

        String reasonCode = null;
        String reasonText = null;

        // 3. credit-limit test, using the source arithmetic verbatim
        BigDecimal temporaryBalance = account.getCurrCycCredit()
                .subtract(account.getCurrCycDebit())
                .add(daily.getAmount());
        if (account.getCreditLimit().compareTo(temporaryBalance) < 0) {
            reasonCode = BatchReject.REASON_OVERLIMIT;
            reasonText = BatchReject.TEXT_OVERLIMIT;
        }

        // 4. expiry test; runs after the limit test and overwrites reason 0102 when both fail
        String originDate = CobolText.trim(daily.getOrigTs());
        originDate = originDate.length() >= 10 ? originDate.substring(0, 10) : originDate;
        if (CobolText.trim(account.getExpirationDate()).compareTo(originDate) < 0) {
            reasonCode = BatchReject.REASON_EXPIRED;
            reasonText = BatchReject.TEXT_EXPIRED;
        }

        if (reasonCode != null) {
            return reject(runId, daily, reasonCode, reasonText);
        }

        // Accepted: category balance, then account, then transaction, in source order.
        CategoryBalance.Key balanceKey = new CategoryBalance.Key(account.getAccountId(),
                daily.getTypeCode(), daily.getCategoryCode());
        CategoryBalance balance = categoryBalances.findById(balanceKey)
                .orElseGet(() -> new CategoryBalance(account.getAccountId(), daily.getTypeCode(),
                        daily.getCategoryCode(), BigDecimal.ZERO));
        balance.setBalance(balance.getBalance().add(daily.getAmount()));
        categoryBalances.save(balance);

        account.setCurrBal(account.getCurrBal().add(daily.getAmount()));
        if (daily.getAmount().signum() >= 0) {
            account.setCurrCycCredit(account.getCurrCycCredit().add(daily.getAmount()));
        } else {
            account.setCurrCycDebit(account.getCurrCycDebit().add(daily.getAmount()));
        }
        accounts.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionId(daily.getTransactionId());
        transaction.setTypeCode(daily.getTypeCode());
        transaction.setCategoryCode(daily.getCategoryCode());
        transaction.setSource(daily.getSource());
        transaction.setDescription(daily.getDescription());
        transaction.setAmount(daily.getAmount());
        transaction.setMerchantId(daily.getMerchantId());
        transaction.setMerchantName(daily.getMerchantName());
        transaction.setMerchantCity(daily.getMerchantCity());
        transaction.setMerchantZip(daily.getMerchantZip());
        transaction.setCardNumber(daily.getCardNumber());
        transaction.setOrigTs(daily.getOrigTs());
        // The source generates a fresh processing timestamp for every accepted record.
        transaction.setProcTs(TransactionService.currentTimestamp());
        transactions.save(transaction);

        daily.setProcessed(true);
        dailyTransactions.save(daily);
        ledger.save(new PostingLedger(runId, daily.getRecordNumber(), daily.getTransactionId(),
                PostingLedger.OUTCOME_POSTED));
        return true;
    }

    /** Writes the 430-byte equivalent reject record and marks the input consumed. */
    private boolean reject(Long runId, DailyTransaction daily, String reasonCode, String reasonText) {
        BatchReject reject = new BatchReject();
        reject.setBatchRunId(runId);
        reject.setRecordNumber(daily.getRecordNumber());
        reject.setRawRecord(rebuildRawRecord(daily));
        reject.setReasonCode(reasonCode);
        reject.setReasonText(CobolText.padRight(reasonText, 76));
        rejects.save(reject);

        daily.setProcessed(true);
        dailyTransactions.save(daily);
        ledger.save(new PostingLedger(runId, daily.getRecordNumber(), daily.getTransactionId(),
                PostingLedger.OUTCOME_REJECTED));
        return false;
    }

    /** Rebuilds the original 350-byte daily record so the reject output stays byte compatible. */
    static String rebuildRawRecord(DailyTransaction daily) {
        StringBuilder sb = new StringBuilder(350);
        sb.append(CobolText.padRight(daily.getTransactionId(), 16));
        sb.append(CobolText.padRight(daily.getTypeCode(), 2));
        sb.append(CobolText.padLeftZero(daily.getCategoryCode(), 4));
        sb.append(CobolText.padRight(daily.getSource(), 10));
        sb.append(CobolText.padRight(daily.getDescription(), 100));
        sb.append(CobolText.toSignedDisplay(daily.getAmount(), 11, 2));
        sb.append(CobolText.padLeftZero(daily.getMerchantId(), 9));
        sb.append(CobolText.padRight(daily.getMerchantName(), 50));
        sb.append(CobolText.padRight(daily.getMerchantCity(), 50));
        sb.append(CobolText.padRight(daily.getMerchantZip(), 10));
        sb.append(CobolText.padRight(daily.getCardNumber(), 16));
        sb.append(CobolText.padRight(daily.getOrigTs(), 26));
        sb.append(CobolText.padRight(daily.getProcTs(), 26));
        sb.append(" ".repeat(20));
        return sb.toString();
    }
}
