package com.carddemo.web;

import com.carddemo.batch.BatchRunService;
import com.carddemo.batch.InterestService;
import com.carddemo.batch.PostingService;
import com.carddemo.batch.ReportService;
import com.carddemo.batch.StatementService;
import com.carddemo.dto.OperationsDtos.BatchRejectDto;
import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.dto.OperationsDtos.InterestRunRequest;
import com.carddemo.dto.OperationsDtos.MigrationLogDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.carddemo.migration.CobolDataMigrationService;
import com.carddemo.repository.MigrationLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Batch operations console. Each endpoint is one legacy job:
 * {@code POSTTRAN} ({@code CBTRN02C}), {@code INTCALC} ({@code CBACT04C}),
 * {@code TRANREPT} ({@code CBTRN03C}) and {@code CREASTMT} ({@code CBSTM03A}).
 *
 * <p>Batch is an operator capability, so the whole controller requires the administrator role.</p>
 */
@RestController
@RequestMapping("/api/admin/batch")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Batch", description = "POSTTRAN, INTCALC, TRANREPT and CREASTMT job equivalents")
public class BatchController {

    private final PostingService postingService;
    private final InterestService interestService;
    private final ReportService reportService;
    private final StatementService statementService;
    private final BatchRunService batchRunService;
    private final CobolDataMigrationService migrationService;
    private final MigrationLogRepository migrationLogs;

    public BatchController(PostingService postingService, InterestService interestService,
                           ReportService reportService, StatementService statementService,
                           BatchRunService batchRunService, CobolDataMigrationService migrationService,
                           MigrationLogRepository migrationLogs) {
        this.postingService = postingService;
        this.interestService = interestService;
        this.reportService = reportService;
        this.statementService = statementService;
        this.batchRunService = batchRunService;
        this.migrationService = migrationService;
        this.migrationLogs = migrationLogs;
    }

    @GetMapping("/runs")
    @Operation(summary = "Batch run history with counts and completion codes")
    public List<BatchRunDto> runs(@RequestParam(value = "limit", defaultValue = "25") int limit) {
        return batchRunService.history(Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "One batch run")
    public BatchRunDto run(@PathVariable("id") Long id) {
        return batchRunService.get(id);
    }

    @GetMapping("/runs/{id}/rejects")
    @Operation(summary = "Rejected posting records of one run (DALYREJS equivalent)")
    public List<BatchRejectDto> rejects(@PathVariable("id") Long id) {
        return postingService.rejectsOf(id);
    }

    @GetMapping("/posting/pending")
    @Operation(summary = "How many daily transactions are still waiting to be posted")
    public Map<String, Long> pending() {
        return Map.of("pending", postingService.pendingCount());
    }

    @PostMapping("/posting")
    @Operation(summary = "Run POSTTRAN over the unposted daily transactions")
    public BatchRunDto post() {
        return postingService.run(CurrentUser.id());
    }

    @PostMapping("/interest")
    @Operation(summary = "Run INTCALC for one ten character cycle id")
    public BatchRunDto interest(@RequestBody InterestRunRequest request) {
        return interestService.run(CurrentUser.id(), request.cycleId());
    }

    @PostMapping("/reports")
    @Operation(summary = "Run TRANREPT over every submitted report request")
    public BatchRunDto reports() {
        return reportService.runPending(CurrentUser.id());
    }

    @PostMapping("/statements")
    @Operation(summary = "Run CREASTMT and regenerate every card statement")
    public BatchRunDto statements() {
        return statementService.run(CurrentUser.id());
    }

    @GetMapping("/migration")
    @Operation(summary = "Provenance of the COBOL data sets loaded into PostgreSQL")
    public List<MigrationLogDto> migration() {
        return migrationLogs.findAllByOrderByExecutedAtDescIdAsc().stream()
                .map(m -> new MigrationLogDto(m.getSourceFile(), m.getEntity(), m.getCodec(),
                        m.getRecordsRead(), m.getRecordsLoaded(), m.getRecordsFailed(), m.getDetail(),
                        m.getExecutedAt()))
                .toList();
    }

    @GetMapping("/migration/counts")
    @Operation(summary = "Row counts per migrated entity")
    public Map<String, Long> migrationCounts() {
        return migrationService.counts();
    }

    @PostMapping("/migration")
    @Operation(summary = "Re-run the COBOL data migration; existing keys are skipped")
    public List<MigrationLogDto> runMigration() {
        return migrationService.migrate().stream()
                .map(m -> new MigrationLogDto(m.getSourceFile(), m.getEntity(), m.getCodec(),
                        m.getRecordsRead(), m.getRecordsLoaded(), m.getRecordsFailed(), m.getDetail(),
                        m.getExecutedAt()))
                .toList();
    }
}
