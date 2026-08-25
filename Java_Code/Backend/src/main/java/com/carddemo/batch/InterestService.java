package com.carddemo.batch;

import com.carddemo.common.ApiException;
import com.carddemo.common.CobolText;
import com.carddemo.domain.BatchRun;
import com.carddemo.domain.CategoryBalance;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.repository.CategoryBalanceRepository;
import com.carddemo.repository.InterestChargeRepository;
import com.carddemo.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Monthly interest calculation. COBOL source {@code CBACT04C.cbl}, job {@code INTCALC.jcl}.
 *
 * <p>The source reads the category balance file in composite-key order and, on each account
 * change, applies the previous account's accumulated interest. This service groups by account
 * first and delegates each account to {@link InterestAccountProcessor}, which produces the same
 * result while making each account atomic.</p>
 *
 * <p>The cycle id is the ten character parameter {@code INTCALC.jcl} passes to the program; it
 * becomes the first ten characters of every generated transaction id, followed by a six digit
 * suffix.</p>
 *
 * <p>Fee calculation is an explicit no-op: {@code 1400-COMPUTE-FEES} in the source is an empty
 * paragraph and FR-BATCH-008 requires it to stay that way until a new approved requirement
 * exists.</p>
 */
@Service
public class InterestService {

    private static final Logger log = LoggerFactory.getLogger(InterestService.class);

    public static final String JOB_NAME = "INTCALC";

    private final CategoryBalanceRepository categoryBalances;
    private final InterestChargeRepository charges;
    private final InterestAccountProcessor processor;
    private final BatchRunService runs;
    private final AuditService audit;

    public InterestService(CategoryBalanceRepository categoryBalances, InterestChargeRepository charges,
                           InterestAccountProcessor processor, BatchRunService runs, AuditService audit) {
        this.categoryBalances = categoryBalances;
        this.charges = charges;
        this.processor = processor;
        this.runs = runs;
        this.audit = audit;
    }

    /**
     * Runs the interest job for one cycle.
     *
     * @param cycleId the ten character cycle identifier, for example {@code 2022071800}
     */
    public BatchRunDto run(String actor, String cycleId) {
        String cycle = validateCycleId(cycleId);
        BatchRun run = runs.start(JOB_NAME, "cycleId=" + cycle, actor);
        int read = 0;
        int written = 0;
        BigDecimal total = BigDecimal.ZERO;
        try {
            // Composite-key order, exactly like the legacy sequential read of TCATBALF.
            List<CategoryBalance> balances =
                    categoryBalances.findAllByOrderByIdAccountIdAscIdTypeCodeAscIdCategoryCodeAsc();
            Set<String> accountIds = new LinkedHashSet<>();
            for (CategoryBalance balance : balances) {
                read++;
                accountIds.add(balance.getAccountId());
            }

            int suffix = charges.findByIdCycleId(cycle).size();
            for (String accountId : accountIds) {
                InterestAccountProcessor.Result result = processor.process(cycle, accountId, suffix);
                suffix += result.transactionsWritten();
                written += result.transactionsWritten();
                total = total.add(result.totalInterest());
            }

            String message = String.format(
                    "Cycle %s: %d category balances read, %d accounts updated, %d interest transactions, total %s",
                    cycle, read, accountIds.size(), written, total.toPlainString());
            audit.success(actor, "BATCH_INTCALC", "BatchRun", String.valueOf(run.getId()), message);
            log.info("INTCALC run {} finished: {}", run.getId(), message);
            return BatchRunService.toDto(runs.finish(run.getId(), 0, read, written, 0, message));
        } catch (RuntimeException e) {
            log.error("INTCALC run {} failed", run.getId(), e);
            runs.fail(run.getId(), e.getMessage());
            throw e;
        }
    }

    /** The cycle id is a ten character parameter; the transaction id appends six digits to it. */
    static String validateCycleId(String rawCycleId) {
        String value = CobolText.trim(rawCycleId);
        if (value.isEmpty()) {
            throw ApiException.badRequest("Cycle ID must be supplied.", "cycleId");
        }
        if (!CobolText.isAllDigits(value) || value.length() != 10) {
            throw ApiException.badRequest("Cycle ID must be a 10 digit number", "cycleId");
        }
        return value;
    }
}
