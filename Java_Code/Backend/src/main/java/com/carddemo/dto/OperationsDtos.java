package com.carddemo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Payloads for menus, reference data, reporting, statements, batch operations and the dashboard. */
public final class OperationsDtos {

    private OperationsDtos() {
    }

    /**
     * One entry of the menu option tables {@code COMEN02Y.cpy} (eleven regular entries) and
     * {@code COADM02Y.cpy} (six administrator entries).
     *
     * @param installed false reproduces the source "not installed" navigation result for optional
     *                  module entries (FR-OPT-017)
     */
    public record MenuOption(
            int number,
            String name,
            String program,
            String userType,
            String route,
            boolean installed) {
    }

    /** Complete menu for the signed-on role plus the screen header values. */
    public record MenuView(
            String title,
            String transactionId,
            String programName,
            String role,
            List<MenuOption> options) {
    }

    /** Transaction type reference row, maintained by the optional Db2 module. */
    public record TransactionTypeDto(String typeCode, String description, long version) {
    }

    /** Transaction category reference row. */
    public record TransactionCategoryDto(String typeCode, String categoryCode, String description, long version) {
    }

    /** Disclosure interest rate row. */
    public record DisclosureGroupDto(String groupId, String typeCode, String categoryCode, BigDecimal interestRate) {
    }

    /** Category balance row. */
    public record CategoryBalanceDto(String accountId, String typeCode, String categoryCode, BigDecimal balance) {
    }

    /** Report request submission; the selector mirrors monthly / yearly / custom of {@code CORPT00C}. */
    public record ReportRequestInput(
            String reportType,
            String startDate,
            String endDate,
            boolean confirmed) {
    }

    /** A queued or completed report. */
    public record ReportRequestDto(
            Long id,
            String reportType,
            String startDate,
            String endDate,
            String status,
            String requestedBy,
            LocalDateTime requestedAt,
            LocalDateTime completedAt,
            int lineCount) {
    }

    /** Statement summary; the full text and HTML are fetched separately. */
    public record StatementDto(
            Long id,
            String cardNumber,
            String accountId,
            String customerId,
            int tranCount,
            BigDecimal totalAmount,
            LocalDateTime generatedAt) {
    }

    /** Batch job run summary. */
    public record BatchRunDto(
            Long id,
            String jobName,
            String parameters,
            String status,
            int returnCode,
            int recordsRead,
            int recordsAccepted,
            int recordsRejected,
            String message,
            String startedBy,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
    }

    /** One rejected posting input, equivalent to a 430-byte {@code DALYREJS} record. */
    public record BatchRejectDto(
            int recordNumber,
            String transactionId,
            String reasonCode,
            String reasonText) {
    }

    /** Interest run parameter: the ten-character cycle id {@code INTCALC.jcl} passed to {@code CBACT04C}. */
    public record InterestRunRequest(String cycleId) {
    }

    /** Dashboard aggregates, all computed from PostgreSQL. */
    public record DashboardSummary(
            long accountCount,
            long customerCount,
            long cardCount,
            long transactionCount,
            long userCount,
            long pendingDailyTransactions,
            BigDecimal totalBalance,
            BigDecimal totalCreditLimit,
            List<BatchRunDto> recentBatchRuns,
            List<TransactionDtos.TransactionRow> recentTransactions,
            Map<String, Long> transactionsByType) {
    }

    /** Migration provenance row (DATA-007). */
    public record MigrationLogDto(
            String sourceFile,
            String entity,
            String codec,
            int recordsRead,
            int recordsLoaded,
            int recordsFailed,
            String detail,
            LocalDateTime executedAt) {
    }

    /** Audit trail row. */
    public record AuditEventDto(
            Long id,
            String actor,
            String action,
            String targetType,
            String targetId,
            String outcome,
            String detail,
            LocalDateTime createdAt) {
    }
}
