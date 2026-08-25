package com.carddemo.web;

import com.carddemo.batch.ReportService;
import com.carddemo.batch.StatementService;
import com.carddemo.dto.OperationsDtos.ReportRequestDto;
import com.carddemo.dto.OperationsDtos.ReportRequestInput;
import com.carddemo.dto.OperationsDtos.StatementDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Report requests and statements. COBOL sources {@code CORPT00C} ({@code CR00}),
 * {@code CBTRN03C} (job {@code TRANREPT}) and {@code CBSTM03A} (job {@code CREASTMT}).
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports and statements", description = "CR00 report request, TRANREPT report, CREASTMT statements")
public class ReportController {

    private final ReportService reportService;
    private final StatementService statementService;

    public ReportController(ReportService reportService, StatementService statementService) {
        this.reportService = reportService;
        this.statementService = statementService;
    }

    @PostMapping("/requests")
    @Operation(summary = "Submit a monthly, yearly or custom report request")
    public ReportRequestDto submit(@RequestBody ReportRequestInput input) {
        return reportService.submit(CurrentUser.id(), input);
    }

    @GetMapping("/requests")
    @Operation(summary = "Report request history")
    public List<ReportRequestDto> history(@RequestParam(value = "all", defaultValue = "false") boolean all,
                                          @RequestParam(value = "limit", defaultValue = "50") int limit) {
        boolean includeAll = all && CurrentUser.isAdmin();
        return reportService.history(CurrentUser.id(), includeAll, Math.min(Math.max(limit, 1), 200));
    }

    @PostMapping("/requests/{id}/generate")
    @Operation(summary = "Generate one queued report")
    public ReportRequestDto generate(@PathVariable("id") Long id) {
        reportService.generate(id);
        return reportService.history(CurrentUser.id(), true, 200).stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @GetMapping(value = "/requests/{id}/content", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "The rendered 133-column report text")
    public ResponseEntity<String> content(@PathVariable("id") Long id) {
        return ResponseEntity.ok(reportService.content(id));
    }

    @GetMapping("/statements")
    @Operation(summary = "Generated statements, most recent first")
    public List<StatementDto> statements(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return statementService.list(Math.min(Math.max(limit, 1), 200));
    }

    @GetMapping("/statements/by-account/{accountId}")
    @Operation(summary = "Statements of one account")
    public List<StatementDto> statementsByAccount(@PathVariable("accountId") String accountId) {
        return statementService.byAccount(accountId);
    }

    @GetMapping(value = "/statements/{id}/text", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "The fixed 80-column statement text")
    public ResponseEntity<String> statementText(@PathVariable("id") Long id) {
        return ResponseEntity.ok(statementService.content(id, false));
    }

    @GetMapping(value = "/statements/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "The HTML statement document")
    public ResponseEntity<String> statementHtml(@PathVariable("id") Long id) {
        return ResponseEntity.ok(statementService.content(id, true));
    }
}
