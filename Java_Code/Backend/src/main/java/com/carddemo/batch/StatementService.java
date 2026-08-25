package com.carddemo.batch;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.Account;
import com.carddemo.domain.AccountStatement;
import com.carddemo.domain.BatchRun;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.Customer;
import com.carddemo.domain.Transaction;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.dto.OperationsDtos.StatementDto;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.AccountStatementRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Card statement generation. COBOL sources {@code CBSTM03A.CBL} and {@code CBSTM03B.CBL},
 * job {@code CREASTMT.JCL}.
 *
 * <p>The text form reproduces the {@code STATEMENT-LINES} group: a start marker, the customer name
 * and three address lines, a "Basic Details" block with account id, current balance and FICO score,
 * a transaction summary table and a signed {@code Total EXP} line, then an end marker. Records are
 * 80 characters wide, exactly like {@code FD-STMTFILE-REC}.</p>
 *
 * <p>FR-BATCH-010 fixes two source limits: there is no fixed 51 card by 10 transaction table, and
 * the HTML output is escaped and forms one valid document per card.</p>
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    public static final String JOB_NAME = "CREASTMT";

    /** {@code FD-STMTFILE-REC PIC X(80)}. */
    private static final int TEXT_WIDTH = 80;

    /** Fixed record separator, independent of the host operating system (NFR-003). */
    private static final String LINE_SEPARATOR = "\n";

    private static final DecimalFormat AMOUNT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        AMOUNT = new DecimalFormat("###,###,##0.00", symbols);
    }

    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final TransactionRepository transactions;
    private final AccountStatementRepository statements;
    private final BatchRunService runs;
    private final AuditService audit;

    public StatementService(CardXrefRepository xrefs, AccountRepository accounts,
                            CustomerRepository customers, TransactionRepository transactions,
                            AccountStatementRepository statements, BatchRunService runs,
                            AuditService audit) {
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.customers = customers;
        this.transactions = transactions;
        this.statements = statements;
        this.runs = runs;
        this.audit = audit;
    }

    /**
     * Generates one statement per cross-reference card, in card order, exactly like the source
     * which scans every xref and produces a statement even when the card has no transactions.
     */
    @Transactional
    public BatchRunDto run(String actor) {
        BatchRun run = runs.start(JOB_NAME, "scope=all-cards", actor);
        int generated = 0;
        int detailRows = 0;
        try {
            Map<String, List<Transaction>> byCard = new LinkedHashMap<>();
            for (Transaction transaction : transactions.findAllForStatements()) {
                byCard.computeIfAbsent(transaction.getCardNumber(), key -> new ArrayList<>()).add(transaction);
            }

            for (CardXref xref : xrefs.findAllByOrderByCardNumberAsc()) {
                Account account = accounts.findById(xref.getAccountId()).orElse(null);
                Customer customer = customers.findById(xref.getCustomerId()).orElse(null);
                if (account == null || customer == null) {
                    continue;
                }
                List<Transaction> cardTransactions = byCard.getOrDefault(xref.getCardNumber(), List.of());
                AccountStatement statement = build(run.getId(), xref, account, customer, cardTransactions);
                statements.save(statement);
                generated++;
                detailRows += cardTransactions.size();
            }

            String message = String.format("Generated %d statements covering %d transaction rows",
                    generated, detailRows);
            audit.success(actor, "BATCH_CREASTMT", "BatchRun", String.valueOf(run.getId()), message);
            log.info("CREASTMT run {} finished: {}", run.getId(), message);
            return BatchRunService.toDto(runs.finish(run.getId(), 0, generated, generated, 0, message));
        } catch (RuntimeException e) {
            log.error("CREASTMT run {} failed", run.getId(), e);
            runs.fail(run.getId(), e.getMessage());
            throw e;
        }
    }

    private AccountStatement build(Long runId, CardXref xref, Account account, Customer customer,
                                   List<Transaction> cardTransactions) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : cardTransactions) {
            total = total.add(transaction.getAmount());
        }

        AccountStatement statement = new AccountStatement();
        statement.setBatchRunId(runId);
        statement.setCardNumber(xref.getCardNumber());
        statement.setAccountId(account.getAccountId());
        statement.setCustomerId(customer.getCustomerId());
        statement.setTranCount(cardTransactions.size());
        statement.setTotalAmount(total);
        statement.setTextContent(renderText(account, customer, cardTransactions, total));
        statement.setHtmlContent(renderHtml(xref, account, customer, cardTransactions, total));
        return statement;
    }

    /** The fixed-width 80-column text statement of {@code CBSTM03A}. */
    String renderText(Account account, Customer customer, List<Transaction> cardTransactions,
                      BigDecimal total) {
        List<String> lines = new ArrayList<>();
        lines.add(marker("START OF STATEMENT", 31));
        lines.add(fixed(fullName(customer), TEXT_WIDTH));
        lines.add(fixed(customer.getAddrLine1(), TEXT_WIDTH));
        lines.add(fixed(customer.getAddrLine2(), TEXT_WIDTH));
        lines.add(fixed(addressLine3(customer), TEXT_WIDTH));
        lines.add("-".repeat(TEXT_WIDTH));
        lines.add(fixed(" ".repeat(33) + "Basic Details", TEXT_WIDTH));
        lines.add(fixed("Account ID         :" + CobolText.padRight(account.getAccountId(), 20), TEXT_WIDTH));
        lines.add(fixed("Current Balance    :" + signed(account.getCurrBal()), TEXT_WIDTH));
        lines.add(fixed("FICO Score         :"
                + CobolText.padRight(Integer.toString(customer.getFicoScore()), 20), TEXT_WIDTH));
        lines.add("-".repeat(TEXT_WIDTH));
        lines.add(fixed(" ".repeat(30) + "TRANSACTION SUMMARY ", TEXT_WIDTH));
        lines.add("-".repeat(TEXT_WIDTH));
        lines.add(fixed(CobolText.padRight("Tran ID", 16) + CobolText.padRight("Tran Details", 51)
                + "  Tran Amount", TEXT_WIDTH));

        for (Transaction transaction : cardTransactions) {
            lines.add(fixed(CobolText.padRight(transaction.getTransactionId(), 16) + " "
                    + CobolText.padRight(transaction.getDescription(), 49) + "$"
                    + signed(transaction.getAmount()), TEXT_WIDTH));
        }

        lines.add(fixed(CobolText.padRight("Total EXP:", 10) + " ".repeat(56) + "$" + signed(total),
                TEXT_WIDTH));
        lines.add(marker("END OF STATEMENT", 32));
        return String.join(LINE_SEPARATOR, lines);
    }

    /**
     * The HTML statement. Unlike the source this escapes every data value, includes the total and
     * produces one complete document per card (FR-BATCH-010).
     */
    String renderHtml(CardXref xref, Account account, Customer customer,
                      List<Transaction> cardTransactions, BigDecimal total) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>").append(LINE_SEPARATOR);
        html.append("<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Statement ").append(escape(maskCard(xref.getCardNumber())))
                .append("</title></head><body>").append(LINE_SEPARATOR);
        html.append("<h1>CardDemo Statement</h1>").append(LINE_SEPARATOR);
        html.append("<p><strong>").append(escape(fullName(customer))).append("</strong><br>")
                .append(escape(customer.getAddrLine1())).append("<br>")
                .append(escape(customer.getAddrLine2())).append("<br>")
                .append(escape(addressLine3(customer))).append("</p>").append(LINE_SEPARATOR);
        html.append("<table><tbody>")
                .append("<tr><td>Account ID</td><td>").append(escape(account.getAccountId())).append("</td></tr>")
                .append("<tr><td>Card Number</td><td>").append(escape(maskCard(xref.getCardNumber())))
                .append("</td></tr>")
                .append("<tr><td>Current Balance</td><td>").append(escape(AMOUNT.format(account.getCurrBal())))
                .append("</td></tr>")
                .append("<tr><td>FICO Score</td><td>").append(customer.getFicoScore()).append("</td></tr>")
                .append("</tbody></table>").append(LINE_SEPARATOR);
        html.append("<h2>Transaction summary</h2>").append(LINE_SEPARATOR);
        html.append("<table><thead><tr><th>Tran ID</th><th>Tran Details</th><th>Tran Amount</th></tr></thead>")
                .append("<tbody>");
        for (Transaction transaction : cardTransactions) {
            html.append("<tr><td>").append(escape(transaction.getTransactionId())).append("</td><td>")
                    .append(escape(transaction.getDescription())).append("</td><td>")
                    .append(escape(AMOUNT.format(transaction.getAmount()))).append("</td></tr>");
        }
        html.append("</tbody><tfoot><tr><th colspan=\"2\">Total EXP</th><th>")
                .append(escape(AMOUNT.format(total))).append("</th></tr></tfoot></table>")
                .append(LINE_SEPARATOR);
        html.append("</body></html>");
        return html.toString();
    }

    /** Statement list, most recent first. */
    @Transactional(readOnly = true)
    public List<StatementDto> list(int limit) {
        return statements.findAllByOrderByGeneratedAtDescCardNumberAsc(PageRequest.of(0, limit)).stream()
                .map(StatementService::toDto)
                .toList();
    }

    /** Statements of one account. */
    @Transactional(readOnly = true)
    public List<StatementDto> byAccount(String accountId) {
        return statements.findByAccountIdOrderByGeneratedAtDesc(accountId).stream()
                .map(StatementService::toDto)
                .toList();
    }

    /** The stored text or HTML body of one statement. */
    @Transactional(readOnly = true)
    public String content(Long id, boolean html) {
        AccountStatement statement = statements.findById(id)
                .orElseThrow(() -> ApiException.notFound("Statement not found ..."));
        return html ? statement.getHtmlContent() : statement.getTextContent();
    }

    private static String marker(String label, int stars) {
        return fixed("*".repeat(stars) + label + "*".repeat(stars), TEXT_WIDTH);
    }

    private static String fullName(Customer customer) {
        return (CobolText.trim(customer.getFirstName()) + " " + CobolText.trim(customer.getMiddleName())
                + " " + CobolText.trim(customer.getLastName())).replaceAll("\\s+", " ").trim();
    }

    /** The source prints address line 3 followed by state and ZIP. */
    private static String addressLine3(Customer customer) {
        return (CobolText.trim(customer.getAddrLine3()) + " " + CobolText.trim(customer.getAddrStateCd())
                + " " + CobolText.trim(customer.getAddrZip())).trim();
    }

    /** {@code PIC Z(9).99-}: trailing minus for a negative amount. */
    private static String signed(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String text = AMOUNT.format(value.abs()) + (value.signum() < 0 ? "-" : " ");
        return CobolText.padLeft(text, 13);
    }

    private static String fixed(String value, int width) {
        return CobolText.padRight(value, width);
    }

    private static String maskCard(String cardNumber) {
        String value = CobolText.trim(cardNumber);
        return value.length() < 4 ? "****" : "************" + value.substring(value.length() - 4);
    }

    /** HTML escaping the source never performed. */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return CobolText.trim(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static StatementDto toDto(AccountStatement statement) {
        return new StatementDto(statement.getId(), statement.getCardNumber(), statement.getAccountId(),
                statement.getCustomerId(), statement.getTranCount(), statement.getTotalAmount(),
                statement.getGeneratedAt());
    }
}
