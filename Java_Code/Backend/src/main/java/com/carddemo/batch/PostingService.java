package com.carddemo.batch;

import com.carddemo.common.CobolText;
import com.carddemo.domain.BatchRun;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.dto.OperationsDtos.BatchRejectDto;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.repository.BatchRejectRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Daily transaction posting. COBOL source {@code CBTRN02C.cbl}, job {@code POSTTRAN.jcl}.
 *
 * <p>Validation order, exactly as the source applies it:</p>
 * <ol>
 *   <li>resolve the card in the cross-reference; missing gives reason {@code 0100}
 *       {@code INVALID CARD NUMBER FOUND};</li>
 *   <li>resolve the account; missing gives reason {@code 0101} {@code ACCOUNT RECORD NOT FOUND};</li>
 *   <li>compute {@code cycle credit - cycle debit + amount}; when the credit limit is smaller the
 *       reason is {@code 0102} {@code OVERLIMIT TRANSACTION.};</li>
 *   <li>compare the account expiry text with the first ten characters of the original timestamp;
 *       an expired account gives reason {@code 0103}, overwriting {@code 0102} when both fail.</li>
 * </ol>
 *
 * <p>All records continue after a business reject. FR-BATCH-003: any reject makes the completion
 * code 4 while accepted records stay posted. The per-record unit of work lives in
 * {@link PostingRecordProcessor}.</p>
 */
@Service
public class PostingService {

    private static final Logger log = LoggerFactory.getLogger(PostingService.class);

    public static final String JOB_NAME = "POSTTRAN";

    private final DailyTransactionRepository dailyTransactions;
    private final BatchRejectRepository rejects;
    private final PostingRecordProcessor processor;
    private final BatchRunService runs;
    private final AuditService audit;

    public PostingService(DailyTransactionRepository dailyTransactions, BatchRejectRepository rejects,
                          PostingRecordProcessor processor, BatchRunService runs, AuditService audit) {
        this.dailyTransactions = dailyTransactions;
        this.rejects = rejects;
        this.processor = processor;
        this.runs = runs;
        this.audit = audit;
    }

    /** Runs the posting job over every unprocessed daily transaction. */
    public BatchRunDto run(String actor) {
        BatchRun run = runs.start(JOB_NAME, "source=daily_transaction", actor);
        int read = 0;
        int accepted = 0;
        int rejected = 0;
        try {
            List<DailyTransaction> input = dailyTransactions.findByProcessedFalseOrderByRecordNumberAsc();
            for (DailyTransaction daily : input) {
                read++;
                if (processor.process(run.getId(), daily.getId())) {
                    accepted++;
                } else {
                    rejected++;
                }
            }
            int returnCode = rejected > 0 ? 4 : 0;
            String message = String.format("Processed %d, accepted %d, rejected %d", read, accepted, rejected);
            audit.success(actor, "BATCH_POSTTRAN", "BatchRun", String.valueOf(run.getId()), message);
            log.info("POSTTRAN run {} finished: {}", run.getId(), message);
            return BatchRunService.toDto(runs.finish(run.getId(), returnCode, read, accepted, rejected, message));
        } catch (RuntimeException e) {
            log.error("POSTTRAN run {} failed", run.getId(), e);
            runs.fail(run.getId(), e.getMessage());
            throw e;
        }
    }

    /** How many daily records are still waiting to be posted. */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return dailyTransactions.countByProcessedFalse();
    }

    /** Rejects of one run, equivalent to reading the {@code DALYREJS} generation data set. */
    @Transactional(readOnly = true)
    public List<BatchRejectDto> rejectsOf(Long runId) {
        return rejects.findByBatchRunIdOrderByRecordNumberAsc(runId).stream()
                .map(r -> new BatchRejectDto(r.getRecordNumber(),
                        CobolText.text(r.getRawRecord(), 1, 16), r.getReasonCode(),
                        CobolText.trim(r.getReasonText())))
                .toList();
    }
}
