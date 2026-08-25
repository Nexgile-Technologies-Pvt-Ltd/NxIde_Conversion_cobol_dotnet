package com.carddemo.service;

import com.carddemo.dto.OperationsDtos.BatchRunDto;
import com.carddemo.dto.OperationsDtos.DashboardSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each role may see on the landing dashboard.
 *
 * <p>CardDemo partitions no data by user: {@code COMEN02Y} marks all eleven servicing options
 * available to a regular user, and every signed-on user works the same account, card and
 * transaction estate. So the portfolio figures are deliberately identical for both roles.</p>
 *
 * <p>The operator-console figures are not portfolio data. The security-user count, the pending
 * posting counter and the batch history describe files a regular user has no route to, so they
 * are withheld rather than shown to someone who would be refused on following them up.</p>
 */
class DashboardVisibilityTest {

    private static DashboardSummary summary(boolean admin) {
        return new DashboardSummary(
                50, 50, 50, 342,
                admin ? 10L : null,
                admin ? 0L : null,
                new BigDecimal("90200.20"),
                new BigDecimal("233711.00"),
                admin ? List.of(new BatchRunDto(1L, "POSTTRAN", "source=daily_transaction",
                        "COMPLETED", 4, 300, 262, 38, "Processed 300", "ADMIN001", null, null))
                        : List.of(),
                List.of(),
                Map.of("01 Purchase", 277L));
    }

    @Test
    @DisplayName("Both roles see the same portfolio, because the servicing estate is shared")
    void portfolioIsIdenticalForBothRoles() {
        DashboardSummary admin = summary(true);
        DashboardSummary user = summary(false);

        assertEquals(admin.accountCount(), user.accountCount());
        assertEquals(admin.customerCount(), user.customerCount());
        assertEquals(admin.cardCount(), user.cardCount());
        assertEquals(admin.transactionCount(), user.transactionCount());
        assertEquals(admin.totalBalance(), user.totalBalance());
        assertEquals(admin.totalCreditLimit(), user.totalCreditLimit());
        assertEquals(admin.transactionsByType(), user.transactionsByType());
    }

    @Test
    @DisplayName("An administrator sees the security file and batch estate")
    void administratorSeesTheConsoleFigures() {
        DashboardSummary admin = summary(true);

        assertNotNull(admin.userCount());
        assertNotNull(admin.pendingDailyTransactions());
        assertTrue(admin.recentBatchRuns().size() > 0);
    }

    @Test
    @DisplayName("A regular user is told nothing about the security file or the batch estate")
    void regularUserIsNotToldAboutTheConsole() {
        DashboardSummary user = summary(false);

        assertNull(user.userCount(), "the count of security users is not a regular user's business");
        assertNull(user.pendingDailyTransactions(), "posting is run from a console this role cannot open");
        assertTrue(user.recentBatchRuns().isEmpty(), "batch history belongs to the console");
    }
}
