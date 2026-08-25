package com.carddemo.batch;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.BatchRun;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.ReportRequest;
import com.carddemo.domain.Transaction;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionType;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.dto.OperationsDtos.ReportRequestDto;
import com.carddemo.dto.OperationsDtos.ReportRequestInput;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.ReportRequestRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import com.carddemo.service.AuditService;
import com.carddemo.validation.CobolDateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Report request and report generation.
 *
 * <p>Request: COBOL source {@code CORPT00C.cbl} (transaction {@code CR00}). Monthly gives the
 * current month's first and last dates, yearly gives January 1 to December 31, custom takes the
 * entered components. FR-RPT-002 additionally requires exactly one selector and
 * {@code start <= end}, and FR-RPT-003 requires a structured durable request rather than the
 * legacy embedded JCL written to transient data queue {@code JOBS}.</p>
 *
 * <p>Generation: COBOL source {@code CBTRN03C.cbl}, job {@code TRANREPT.jcl}. Transactions in the
 * processing-date range are resolved through the cross-reference to an account, then their type and
 * composite category descriptions, and written as 133-column detail lines with page totals every
 * twenty lines, a total on each card change and a grand total. FR-BATCH-009 fixes the source EOF
 * duplication and the omitted final card total.</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    public static final String JOB_NAME = "TRANREPT";

    /**
     * Fixed record separator. The 133-byte record is the contract; the separator must not
     * depend on the host operating system (NFR-003).
     */
    private static final String LINE_SEPARATOR = "\n";

    private final ReportRequestRepository requests;
    private final TransactionRepository transactions;
    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final CardXrefRepository xrefs;
    private final BatchRunService runs;
    private final CobolDateValidator dates;
    private final AuditService audit;
    private final Clock clock;

    public ReportService(ReportRequestRepository requests, TransactionRepository transactions,
                         TransactionTypeRepository types, TransactionCategoryRepository categories,
                         CardXrefRepository xrefs, BatchRunService runs, CobolDateValidator dates,
                         AuditService audit, Clock clock) {
        this.requests = requests;
        this.transactions = transactions;
        this.types = types;
        this.categories = categories;
        this.xrefs = xrefs;
        this.runs = runs;
        this.dates = dates;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Submits a report request. The two-step confirmation of the legacy screen is preserved: an
     * unconfirmed request is rejected with the source prompt.
     */
    @Transactional
    public ReportRequestDto submit(String actor, ReportRequestInput input) {
        String type = CobolText.trim(input.reportType()).toUpperCase();
        LocalDate today = LocalDate.now(clock);
        String start;
        String end;

        switch (type) {
            case ReportRequest.TYPE_MONTHLY -> {
                start = today.withDayOfMonth(1).toString();
                end = today.withDayOfMonth(today.lengthOfMonth()).toString();
            }
            case ReportRequest.TYPE_YEARLY -> {
                start = LocalDate.of(today.getYear(), 1, 1).toString();
                end = LocalDate.of(today.getYear(), 12, 31).toString();
            }
            case ReportRequest.TYPE_CUSTOM -> {
                start = requireDate(input.startDate(), "Start Date", "startDate");
                end = requireDate(input.endDate(), "End Date", "endDate");
                // FR-RPT-002: the source has no such check; the safe target requires start <= end.
                if (start.compareTo(end) > 0) {
                    throw ApiException.badRequest("Start Date must not be after End Date", "startDate");
                }
            }
            default -> throw ApiException.badRequest(
                    "Please select Monthly, Yearly or Custom report", "reportType");
        }

        if (!input.confirmed()) {
            throw ApiException.badRequest(
                    "Please confirm to print the report (Y/N)...", "confirmed");
        }

        ReportRequest request = new ReportRequest();
        request.setReportType(type);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setRequestedBy(actor);
        request.setStatus(ReportRequest.STATUS_SUBMITTED);
        requests.save(request);

        audit.success(actor, "REPORT_REQUEST", "ReportRequest", String.valueOf(request.getId()),
                type + " " + start + " to " + end);
        return toDto(request);
    }

    /** Report request history for the requesting user, most recent first. */
    @Transactional(readOnly = true)
    public List<ReportRequestDto> history(String actor, boolean all, int limit) {
        List<ReportRequest> rows = all
                ? requests.findAllByOrderByRequestedAtDesc(PageRequest.of(0, limit))
                : requests.findByRequestedByOrderByRequestedAtDesc(actor, PageRequest.of(0, limit));
        return rows.stream().map(ReportService::toDto).toList();
    }

    /** The rendered 133-column report text of one completed request. */
    @Transactional(readOnly = true)
    public String content(Long id) {
        ReportRequest request = requests.findById(id)
                .orElseThrow(() -> ApiException.notFound("Report request not found ..."));
        if (request.getContent() == null) {
            throw ApiException.badRequest("Report has not been generated yet ...");
        }
        return request.getContent();
    }

    /**
     * Runs every submitted request, equivalent to the {@code TRANREPT} job consuming the queue.
     */
    public BatchRunDto runPending(String actor) {
        BatchRun run = runs.start(JOB_NAME, "scope=pending", actor);
        int processed = 0;
        int lines = 0;
        try {
            for (ReportRequest request : requests.findByStatusOrderByRequestedAtAsc(
                    ReportRequest.STATUS_SUBMITTED)) {
                lines += generate(request.getId());
                processed++;
            }
            String message = processed == 0
                    ? "No pending report requests."
                    : String.format("Generated %d report(s), %d lines", processed, lines);
            audit.success(actor, "BATCH_TRANREPT", "BatchRun", String.valueOf(run.getId()), message);
            return BatchRunService.toDto(runs.finish(run.getId(), 0, processed, processed, 0, message));
        } catch (RuntimeException e) {
            log.error("TRANREPT run {} failed", run.getId(), e);
            runs.fail(run.getId(), e.getMessage());
            throw e;
        }
    }

    /** Generates one report and stores its text on the request row. */
    @Transactional
    public int generate(Long requestId) {
        ReportRequest request = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Report request not found ..."));
        List<String> lines = render(request.getStartDate(), request.getEndDate());
        request.setContent(String.join(LINE_SEPARATOR, lines));
        request.setLineCount(lines.size());
        request.setStatus(ReportRequest.STATUS_COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        requests.save(request);
        return lines.size();
    }

    /**
     * Renders the report body. Sorted by card then transaction id, exactly like the sort step of
     * {@code TRANREPT.jcl}; "Account Total" groups on card change as the source does.
     */
    @Transactional(readOnly = true)
    public List<String> render(String startDate, String endDate) {
        List<Transaction> selected = transactions.findForReport(startDate, endDate);

        List<String> lines = new ArrayList<>();
        lines.add(ReportFormatter.nameHeader(startDate, endDate));
        lines.add(ReportFormatter.columnHeader());
        lines.add(ReportFormatter.separator());

        BigDecimal pageTotal = BigDecimal.ZERO;
        BigDecimal cardTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        String currentCard = null;
        int lineCounter = 0;

        for (Transaction transaction : selected) {
            if (currentCard != null && !currentCard.equals(transaction.getCardNumber())) {
                lines.add(ReportFormatter.accountTotal(cardTotal));
                cardTotal = BigDecimal.ZERO;
            }
            currentCard = transaction.getCardNumber();

            String accountId = xrefs.findById(transaction.getCardNumber())
                    .map(CardXref::getAccountId)
                    .orElse("");
            String typeDescription = types.findById(transaction.getTypeCode())
                    .map(TransactionType::getDescription).orElse("");
            String categoryDescription = categories
                    .findById(new TransactionCategory.Key(transaction.getTypeCode(),
                            transaction.getCategoryCode()))
                    .map(TransactionCategory::getDescription).orElse("");

            lines.add(ReportFormatter.detail(transaction.getTransactionId(), accountId,
                    transaction.getTypeCode(), typeDescription, transaction.getCategoryCode(),
                    categoryDescription, transaction.getSource(), transaction.getAmount()));

            pageTotal = pageTotal.add(transaction.getAmount());
            cardTotal = cardTotal.add(transaction.getAmount());
            grandTotal = grandTotal.add(transaction.getAmount());

            lineCounter++;
            if (lineCounter % ReportFormatter.LINES_PER_PAGE == 0) {
                lines.add(ReportFormatter.pageTotal(pageTotal));
                pageTotal = BigDecimal.ZERO;
            }
        }

        // FR-BATCH-009: the source omitted the final card total and re-added the last amount at
        // end of file. Both are corrected here.
        if (currentCard != null) {
            lines.add(ReportFormatter.accountTotal(cardTotal));
        }
        if (pageTotal.signum() != 0 || selected.isEmpty()) {
            lines.add(ReportFormatter.pageTotal(pageTotal));
        }
        lines.add(ReportFormatter.grandTotal(grandTotal));
        return lines;
    }

    private String requireDate(String value, String label, String field) {
        String date = CobolText.trim(value);
        if (date.isEmpty()) {
            throw ApiException.badRequest(label + " must be supplied.", field);
        }
        CobolDateValidator.Result result = dates.validateIso(label, date);
        if (!result.valid()) {
            throw ApiException.badRequest(result.message(), field);
        }
        if (!dates.isRealCalendarDate(date)) {
            throw ApiException.badRequest(label + " - Not a valid date...", field);
        }
        return date;
    }

    static ReportRequestDto toDto(ReportRequest request) {
        return new ReportRequestDto(request.getId(), request.getReportType(), request.getStartDate(),
                request.getEndDate(), request.getStatus(), request.getRequestedBy(),
                request.getRequestedAt(), request.getCompletedAt(), request.getLineCount());
    }
}
