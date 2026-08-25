package com.carddemo.service;

import com.carddemo.batch.BatchRunService;
import com.carddemo.dto.OperationsDtos.DashboardSummary;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.AppUserRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates for the landing dashboard. Every figure is computed from PostgreSQL; nothing is
 * hardcoded.
 */
@Service
public class DashboardService {

    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final CardRepository cards;
    private final TransactionRepository transactions;
    private final AppUserRepository users;
    private final DailyTransactionRepository dailyTransactions;
    private final TransactionTypeRepository types;
    private final TransactionService transactionService;
    private final BatchRunService batchRuns;

    public DashboardService(AccountRepository accounts, CustomerRepository customers, CardRepository cards,
                            TransactionRepository transactions, AppUserRepository users,
                            DailyTransactionRepository dailyTransactions, TransactionTypeRepository types,
                            TransactionService transactionService, BatchRunService batchRuns) {
        this.accounts = accounts;
        this.customers = customers;
        this.cards = cards;
        this.transactions = transactions;
        this.users = users;
        this.dailyTransactions = dailyTransactions;
        this.types = types;
        this.transactionService = transactionService;
        this.batchRuns = batchRuns;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        BigDecimal totalBalance = accounts.sumCurrentBalance();
        BigDecimal totalCreditLimit = accounts.findAllByOrderByAccountIdAsc().stream()
                .map(a -> a.getCreditLimit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Volume per transaction type, labelled with the description from the reference table.
        Map<String, String> typeNames = new LinkedHashMap<>();
        types.findAllByOrderByTypeCodeAsc().forEach(type ->
                typeNames.put(type.getTypeCode(), type.getDescription().trim()));

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Object[] row : transactions.countByTypeCode()) {
            String code = (String) row[0];
            long count = ((Number) row[1]).longValue();
            String label = typeNames.getOrDefault(code, code);
            byType.put(label.isEmpty() ? code : code + " " + label, count);
        }

        return new DashboardSummary(
                accounts.count(),
                customers.count(),
                cards.count(),
                transactions.count(),
                users.count(),
                dailyTransactions.countByProcessedFalse(),
                totalBalance == null ? BigDecimal.ZERO : totalBalance,
                totalCreditLimit,
                batchRuns.history(5),
                transactionService.recent(8),
                byType);
    }
}
