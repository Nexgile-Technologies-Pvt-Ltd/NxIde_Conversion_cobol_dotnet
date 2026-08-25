package com.carddemo.service;

import com.carddemo.common.ApiException;
import com.carddemo.domain.Account;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Transaction;
import com.carddemo.dto.TransactionDtos.BillPaymentRequest;
import com.carddemo.dto.TransactionDtos.BillPaymentView;
import com.carddemo.dto.TransactionDtos.TransactionWriteResult;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Bill payment. COBOL source {@code COBIL00C.cbl} (transaction {@code CB00}).
 *
 * <p>The generated transaction reproduces the source literals exactly: type {@code 02}, category
 * {@code 0002}, source {@code POS TERM}, description {@code BILL PAYMENT - ONLINE}, merchant
 * {@code 999999999 / BILL PAYMENT / N/A / N/A}, amount equal to the whole current balance
 * ({@code COBIL00C.cbl} lines 208-267).</p>
 *
 * <p>Safe deviations: the account id is validated before conversion (FR-BILL-002), the
 * cross-reference card is chosen deterministically, and the transaction insert plus the balance
 * update are one atomic unit (FR-BILL-005).</p>
 */
@Service
public class BillPaymentService {

    private static final String TYPE_CODE = "02";
    private static final String CATEGORY_CODE = "0002";
    private static final String SOURCE = "POS TERM";
    private static final String DESCRIPTION = "BILL PAYMENT - ONLINE";
    private static final String MERCHANT_ID = "999999999";
    private static final String MERCHANT_NAME = "BILL PAYMENT";
    private static final String MERCHANT_CITY = "N/A";
    private static final String MERCHANT_ZIP = "N/A";

    private final AccountRepository accounts;
    private final CardXrefRepository xrefs;
    private final TransactionRepository transactions;
    private final SequenceService sequences;
    private final AuditService audit;

    public BillPaymentService(AccountRepository accounts, CardXrefRepository xrefs,
                              TransactionRepository transactions, SequenceService sequences,
                              AuditService audit) {
        this.accounts = accounts;
        this.xrefs = xrefs;
        this.transactions = transactions;
        this.sequences = sequences;
        this.audit = audit;
    }

    /** Enquiry step: show the current balance the {@code COBIL0A} map displays. */
    @Transactional(readOnly = true)
    public BillPaymentView view(String rawAccountId) {
        String accountId = AccountService.validateAccountId(rawAccountId);
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account ID NOT found...", "accountId"));
        CardXref xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(accountId).orElse(null);

        boolean payable = account.getCurrBal().compareTo(BigDecimal.ZERO) > 0;
        String message = payable ? null : "You have nothing to pay...";
        if (payable && xref == null) {
            payable = false;
            message = "No card is associated with this account...";
        }
        return new BillPaymentView(accountId, account.getCurrBal(),
                xref == null ? "" : xref.getCardNumber(), payable, message);
    }

    /**
     * Confirmed payment. A non-positive balance creates nothing; a positive balance creates a full
     * balance payment and reduces the account balance by exactly that amount.
     */
    @Transactional
    public TransactionWriteResult pay(String actor, BillPaymentRequest request) {
        String accountId = AccountService.validateAccountId(request.accountId());
        if (!request.confirmed()) {
            throw ApiException.badRequest("Please confirm the payment (Y/N)...", "confirmed");
        }

        Account account = accounts.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("Account ID NOT found...", "accountId"));

        BigDecimal balance = account.getCurrBal();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("You have nothing to pay...", "accountId");
        }

        CardXref xref = xrefs.findFirstByAccountIdOrderByCardNumberAsc(accountId)
                .orElseThrow(() -> ApiException.notFound(
                        "No card is associated with this account...", "accountId"));

        String timestamp = TransactionService.currentTimestamp();
        Transaction payment = new Transaction();
        payment.setTransactionId(sequences.nextTransactionId());
        payment.setTypeCode(TYPE_CODE);
        payment.setCategoryCode(CATEGORY_CODE);
        payment.setSource(SOURCE);
        payment.setDescription(DESCRIPTION);
        payment.setAmount(balance);
        payment.setMerchantId(MERCHANT_ID);
        payment.setMerchantName(MERCHANT_NAME);
        payment.setMerchantCity(MERCHANT_CITY);
        payment.setMerchantZip(MERCHANT_ZIP);
        payment.setCardNumber(xref.getCardNumber());
        payment.setOrigTs(timestamp);
        payment.setProcTs(timestamp);
        transactions.save(payment);

        account.setCurrBal(balance.subtract(balance));
        accounts.save(account);

        audit.success(actor, "BILL_PAYMENT", "Account", accountId,
                "Paid " + balance.toPlainString() + " as transaction " + payment.getTransactionId());

        return new TransactionWriteResult(payment.getTransactionId(),
                "Payment successful. Your Transaction ID is " + payment.getTransactionId() + ".",
                account.getCurrBal());
    }
}
