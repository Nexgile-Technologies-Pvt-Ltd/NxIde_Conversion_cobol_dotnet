package com.carddemo.web;

import com.carddemo.dto.OperationsDtos.AuditEventDto;
import com.carddemo.dto.OperationsDtos.DashboardSummary;
import com.carddemo.service.AuditService;
import com.carddemo.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Landing dashboard aggregates and the audit trail. */
@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard", description = "Portfolio aggregates and the audit trail")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuditService auditService;

    public DashboardController(DashboardService dashboardService, AuditService auditService) {
        this.dashboardService = dashboardService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Counts and totals computed from PostgreSQL")
    public DashboardSummary dashboard() {
        return dashboardService.summary();
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Recent audit events")
    public List<AuditEventDto> audit(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return auditService.recent(Math.min(Math.max(limit, 1), 500));
    }
}
