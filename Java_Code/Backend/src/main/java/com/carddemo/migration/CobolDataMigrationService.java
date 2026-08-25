package com.carddemo.migration;

import com.carddemo.common.CobolText;
import com.carddemo.config.CardDemoProperties;
import com.carddemo.domain.Account;
import com.carddemo.domain.AppUser;
import com.carddemo.domain.Card;
import com.carddemo.domain.CardXref;
import com.carddemo.domain.CategoryBalance;
import com.carddemo.domain.Customer;
import com.carddemo.domain.DailyTransaction;
import com.carddemo.domain.DisclosureGroup;
import com.carddemo.domain.MigrationLog;
import com.carddemo.domain.TransactionCategory;
import com.carddemo.domain.TransactionType;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.AppUserRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CategoryBalanceRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.DailyTransactionRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.MigrationLogRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the COBOL data sets shipped in {@code Cobol_Code} into PostgreSQL.
 *
 * <p>Each loader implements the byte offsets of one copybook exactly as documented in
 * {@code Documentation/Appendix-File-and-Record-Layouts.md}. Nothing is hardcoded: every value
 * the application later serves comes from these files.</p>
 *
 * <p>Load order respects the referential constraints: customers and accounts first, then cards and
 * cross-references, then reference data, category balances and the daily transaction input.</p>
 */
@Service
public class CobolDataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CobolDataMigrationService.class);

    private static final String ASCII = "ASCII";
    private static final String EBCDIC_CODEC = "EBCDIC-CP037";

    private final CardDemoProperties properties;
    private final PasswordEncoder passwordEncoder;

    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final CardRepository cards;
    private final CardXrefRepository xrefs;
    private final TransactionTypeRepository transactionTypes;
    private final TransactionCategoryRepository transactionCategories;
    private final CategoryBalanceRepository categoryBalances;
    private final DisclosureGroupRepository disclosureGroups;
    private final DailyTransactionRepository dailyTransactions;
    private final MigrationLogRepository migrationLogs;

    public CobolDataMigrationService(CardDemoProperties properties,
                                     PasswordEncoder passwordEncoder,
                                     AppUserRepository users,
                                     CustomerRepository customers,
                                     AccountRepository accounts,
                                     CardRepository cards,
                                     CardXrefRepository xrefs,
                                     TransactionTypeRepository transactionTypes,
                                     TransactionCategoryRepository transactionCategories,
                                     CategoryBalanceRepository categoryBalances,
                                     DisclosureGroupRepository disclosureGroups,
                                     DailyTransactionRepository dailyTransactions,
                                     MigrationLogRepository migrationLogs) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.customers = customers;
        this.accounts = accounts;
        this.cards = cards;
        this.xrefs = xrefs;
        this.transactionTypes = transactionTypes;
        this.transactionCategories = transactionCategories;
        this.categoryBalances = categoryBalances;
        this.disclosureGroups = disclosureGroups;
        this.dailyTransactions = dailyTransactions;
        this.migrationLogs = migrationLogs;
    }

    /** True when the core tables are still empty and a migration is required. */
    public boolean isDatabaseEmpty() {
        return accounts.count() == 0 && customers.count() == 0 && users.count() == 0;
    }

    /**
     * Runs the whole migration. Safe to call repeatedly: each loader skips rows whose key already
     * exists, so a rerun tops up missing data rather than duplicating it.
     *
     * @return one summary line per source file
     */
    @Transactional
    public List<MigrationLog> migrate() {
        CobolRecordSource source = new CobolRecordSource(properties.getMigration().getSourceDirectory());
        List<MigrationLog> results = new ArrayList<>();

        results.add(loadUsers(source));
        results.add(loadCustomers(source));
        results.add(loadAccounts(source));
        results.add(loadCards(source));
        results.add(loadXrefs(source));
        results.add(loadTransactionTypes(source));
        results.add(loadTransactionCategories(source));
        results.add(loadDisclosureGroups(source));
        results.add(loadCategoryBalances(source));
        results.add(loadDailyTransactions(source));

        migrationLogs.saveAll(results);
        log.info("COBOL data migration finished: {} source files processed", results.size());
        return results;
    }

    // ------------------------------------------------------------------ users

    /**
     * {@code CSUSR01Y.cpy} / {@code AWS.M2.CARDDEMO.USRSEC.PS}, 80-byte EBCDIC records.
     * Offsets: id 1-8, first name 9-28, last name 29-48, password 49-56, type 57.
     *
     * <p>The legacy plaintext password is never stored. Every migrated account receives a hash of
     * the configured migration password and must change it at first sign-in.</p>
     */
    private MigrationLog loadUsers(CobolRecordSource source) {
        String file = "ebcdic/AWS.M2.CARDDEMO.USRSEC.PS";
        MigrationLog logRow = newLog(file, "AppUser", EBCDIC_CODEC);
        try {
            List<String> records = source.readEbcdicRecords(file, 80);
            logRow.setRecordsRead(records.size());
            String hash = passwordEncoder.encode(properties.getMigration().getLegacyPassword());
            List<AppUser> batch = new ArrayList<>();
            for (String record : records) {
                String userId = CobolText.text(record, 1, 8).toUpperCase();
                if (userId.isEmpty() || users.existsById(userId)) {
                    continue;
                }
                String type = CobolText.text(record, 57, 1).toUpperCase();
                AppUser user = new AppUser();
                user.setUserId(userId);
                user.setFirstName(CobolText.text(record, 9, 20));
                user.setLastName(CobolText.text(record, 29, 20));
                user.setPasswordHash(hash);
                user.setUserType("A".equals(type) ? "A" : "U");
                user.setActive(true);
                batch.add(user);
            }
            users.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setDetail("Plaintext SEC-USR-PWD discarded; migration password hashed with bcrypt.");
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("User migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ customers

    /**
     * {@code CVCUS01Y.cpy} / {@code custdata.txt}, 500-byte records.
     */
    private MigrationLog loadCustomers(CobolRecordSource source) {
        String file = "ascii/custdata.txt";
        MigrationLog logRow = newLog(file, "Customer", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<Customer> batch = new ArrayList<>();
            for (String record : records) {
                String id = CobolText.digits(record, 1, 9);
                if (customers.existsById(id)) {
                    continue;
                }
                Customer c = new Customer();
                c.setCustomerId(id);
                c.setFirstName(CobolText.text(record, 10, 25));
                c.setMiddleName(CobolText.text(record, 35, 25));
                c.setLastName(CobolText.text(record, 60, 25));
                c.setAddrLine1(CobolText.text(record, 85, 50));
                c.setAddrLine2(CobolText.text(record, 135, 50));
                c.setAddrLine3(CobolText.text(record, 185, 50));
                c.setAddrStateCd(CobolText.text(record, 235, 2));
                c.setAddrCountryCd(CobolText.text(record, 237, 3));
                c.setAddrZip(CobolText.text(record, 240, 10));
                c.setPhoneNum1(CobolText.text(record, 250, 15));
                c.setPhoneNum2(CobolText.text(record, 265, 15));
                c.setSsn(CobolText.digits(record, 280, 9));
                c.setGovtIssuedId(CobolText.text(record, 289, 20));
                c.setDateOfBirth(CobolText.text(record, 309, 10));
                c.setEftAccountId(CobolText.text(record, 319, 10));
                String primary = CobolText.text(record, 329, 1);
                c.setPriCardHolderInd(primary.isEmpty() ? "N" : primary);
                c.setFicoScore(parseInt(CobolText.digits(record, 330, 3)));
                batch.add(c);
            }
            customers.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Customer migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ accounts

    /**
     * {@code CVACT01Y.cpy} / {@code acctdata.txt}, 300-byte records with signed overpunch amounts.
     */
    private MigrationLog loadAccounts(CobolRecordSource source) {
        String file = "ascii/acctdata.txt";
        MigrationLog logRow = newLog(file, "Account", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<Account> batch = new ArrayList<>();
            for (String record : records) {
                String id = CobolText.digits(record, 1, 11);
                if (accounts.existsById(id)) {
                    continue;
                }
                Account a = new Account();
                a.setAccountId(id);
                String status = CobolText.text(record, 12, 1);
                a.setActiveStatus(status.isEmpty() ? "Y" : status);
                a.setCurrBal(CobolText.signedAmount(record, 13, 12, 2));
                a.setCreditLimit(CobolText.signedAmount(record, 25, 12, 2));
                a.setCashCreditLimit(CobolText.signedAmount(record, 37, 12, 2));
                a.setOpenDate(CobolText.text(record, 49, 10));
                a.setExpirationDate(CobolText.text(record, 59, 10));
                a.setReissueDate(CobolText.text(record, 69, 10));
                a.setCurrCycCredit(CobolText.signedAmount(record, 79, 12, 2));
                a.setCurrCycDebit(CobolText.signedAmount(record, 91, 12, 2));
                a.setAddrZip(CobolText.text(record, 103, 10));
                a.setGroupId(CobolText.text(record, 113, 10));
                batch.add(a);
            }
            accounts.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setDetail("ZIP bytes 103-112 and group bytes 113-122 loaded into their own columns.");
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Account migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ cards

    /** {@code CVACT02Y.cpy} / {@code carddata.txt}, 150-byte records. */
    private MigrationLog loadCards(CobolRecordSource source) {
        String file = "ascii/carddata.txt";
        MigrationLog logRow = newLog(file, "Card", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<Card> batch = new ArrayList<>();
            int skipped = 0;
            for (String record : records) {
                String number = CobolText.text(record, 1, 16);
                if (number.isEmpty() || cards.existsById(number)) {
                    continue;
                }
                String accountId = CobolText.digits(record, 17, 11);
                if (!accounts.existsById(accountId)) {
                    skipped++;
                    continue;
                }
                Card card = new Card();
                card.setCardNumber(number);
                card.setAccountId(accountId);
                card.setCvvCode(CobolText.digits(record, 28, 3));
                card.setEmbossedName(CobolText.text(record, 31, 50));
                card.setExpirationDate(CobolText.text(record, 81, 10));
                String status = CobolText.text(record, 91, 1);
                card.setActiveStatus(status.isEmpty() ? "Y" : status);
                batch.add(card);
            }
            cards.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setRecordsFailed(skipped);
            if (skipped > 0) {
                logRow.setDetail(skipped + " card records referenced an account that is not present.");
            }
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Card migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ cross-reference

    /**
     * {@code CVACT03Y.cpy} / {@code cardxref.txt}. The ASCII fixture holds only the 36 meaningful
     * characters and omits the 14-byte filler, so short records are padded by the slice helper.
     */
    private MigrationLog loadXrefs(CobolRecordSource source) {
        String file = "ascii/cardxref.txt";
        MigrationLog logRow = newLog(file, "CardXref", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<CardXref> batch = new ArrayList<>();
            int skipped = 0;
            for (String record : records) {
                String number = CobolText.text(record, 1, 16);
                if (number.isEmpty() || xrefs.existsById(number)) {
                    continue;
                }
                String customerId = CobolText.digits(record, 17, 9);
                String accountId = CobolText.digits(record, 26, 11);
                if (!customers.existsById(customerId) || !accounts.existsById(accountId)) {
                    skipped++;
                    continue;
                }
                batch.add(new CardXref(number, customerId, accountId));
            }
            xrefs.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setRecordsFailed(skipped);
            if (skipped > 0) {
                logRow.setDetail(skipped + " cross-reference records had an unresolved customer or account.");
            }
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Cross-reference migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ reference data

    /** {@code CVTRA03Y.cpy} / {@code trantype.txt}, 60-byte records. */
    private MigrationLog loadTransactionTypes(CobolRecordSource source) {
        String file = "ascii/trantype.txt";
        MigrationLog logRow = newLog(file, "TransactionType", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<TransactionType> batch = new ArrayList<>();
            for (String record : records) {
                String code = CobolText.text(record, 1, 2);
                if (code.isEmpty() || transactionTypes.existsById(code)) {
                    continue;
                }
                batch.add(new TransactionType(code, CobolText.text(record, 3, 50)));
            }
            transactionTypes.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Transaction type migration failed", e);
        }
        return logRow;
    }

    /** {@code CVTRA04Y.cpy} / {@code trancatg.txt}, 60-byte records keyed by type plus category. */
    private MigrationLog loadTransactionCategories(CobolRecordSource source) {
        String file = "ascii/trancatg.txt";
        MigrationLog logRow = newLog(file, "TransactionCategory", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<TransactionCategory> batch = new ArrayList<>();
            int skipped = 0;
            for (String record : records) {
                String type = CobolText.text(record, 1, 2);
                String category = CobolText.digits(record, 3, 4);
                if (type.isEmpty()) {
                    continue;
                }
                if (!transactionTypes.existsById(type)) {
                    skipped++;
                    continue;
                }
                TransactionCategory.Key key = new TransactionCategory.Key(type, category);
                if (transactionCategories.existsById(key)) {
                    continue;
                }
                batch.add(new TransactionCategory(type, category, CobolText.text(record, 7, 50)));
            }
            transactionCategories.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setRecordsFailed(skipped);
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Transaction category migration failed", e);
        }
        return logRow;
    }

    /** {@code CVTRA02Y.cpy} / {@code discgrp.txt}, 50-byte records with an S9(4)V99 rate. */
    private MigrationLog loadDisclosureGroups(CobolRecordSource source) {
        String file = "ascii/discgrp.txt";
        MigrationLog logRow = newLog(file, "DisclosureGroup", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<DisclosureGroup> batch = new ArrayList<>();
            for (String record : records) {
                String group = CobolText.text(record, 1, 10);
                String type = CobolText.text(record, 11, 2);
                String category = CobolText.digits(record, 13, 4);
                if (group.isEmpty() || type.isEmpty()) {
                    continue;
                }
                DisclosureGroup.Key key = new DisclosureGroup.Key(group, type, category);
                if (disclosureGroups.existsById(key)) {
                    continue;
                }
                BigDecimal rate = CobolText.signedAmount(record, 17, 6, 2);
                batch.add(new DisclosureGroup(group, type, category, rate));
            }
            disclosureGroups.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Disclosure group migration failed", e);
        }
        return logRow;
    }

    /** {@code CVTRA01Y.cpy} / {@code tcatbal.txt}, 50-byte records keyed by account, type, category. */
    private MigrationLog loadCategoryBalances(CobolRecordSource source) {
        String file = "ascii/tcatbal.txt";
        MigrationLog logRow = newLog(file, "CategoryBalance", ASCII);
        try {
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<CategoryBalance> batch = new ArrayList<>();
            int skipped = 0;
            for (String record : records) {
                String accountId = CobolText.digits(record, 1, 11);
                String type = CobolText.text(record, 12, 2);
                String category = CobolText.digits(record, 14, 4);
                if (!accounts.existsById(accountId)) {
                    skipped++;
                    continue;
                }
                CategoryBalance.Key key = new CategoryBalance.Key(accountId, type, category);
                if (categoryBalances.existsById(key)) {
                    continue;
                }
                batch.add(new CategoryBalance(accountId, type, category,
                        CobolText.signedAmount(record, 18, 11, 2)));
            }
            categoryBalances.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setRecordsFailed(skipped);
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Category balance migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ daily transactions

    /**
     * {@code CVTRA06Y.cpy} / {@code dailytran.txt}, 350-byte records. These are posting input, not
     * master rows, so they land in {@code daily_transaction} awaiting the posting job.
     */
    private MigrationLog loadDailyTransactions(CobolRecordSource source) {
        String file = "ascii/dailytran.txt";
        MigrationLog logRow = newLog(file, "DailyTransaction", ASCII);
        try {
            if (dailyTransactions.count() > 0) {
                logRow.setDetail("Skipped: daily transaction staging already populated.");
                return logRow;
            }
            List<String> records = source.readAsciiLines(file);
            logRow.setRecordsRead(records.size());
            List<DailyTransaction> batch = new ArrayList<>();
            int recordNumber = 0;
            for (String record : records) {
                recordNumber++;
                DailyTransaction d = new DailyTransaction();
                d.setTransactionId(CobolText.text(record, 1, 16));
                d.setTypeCode(CobolText.text(record, 17, 2));
                d.setCategoryCode(CobolText.digits(record, 19, 4));
                d.setSource(CobolText.text(record, 23, 10));
                d.setDescription(CobolText.text(record, 33, 100));
                d.setAmount(CobolText.signedAmount(record, 133, 11, 2));
                d.setMerchantId(CobolText.digits(record, 144, 9));
                d.setMerchantName(CobolText.text(record, 153, 50));
                d.setMerchantCity(CobolText.text(record, 203, 50));
                d.setMerchantZip(CobolText.text(record, 253, 10));
                d.setCardNumber(CobolText.text(record, 263, 16));
                d.setOrigTs(CobolText.text(record, 279, 26));
                d.setProcTs(CobolText.text(record, 305, 26));
                d.setRecordNumber(recordNumber);
                batch.add(d);
            }
            dailyTransactions.saveAll(batch);
            logRow.setRecordsLoaded(batch.size());
            logRow.setDetail("Loaded as posting input; run the POSTTRAN batch job to apply them.");
        } catch (IOException | RuntimeException e) {
            logRow.setRecordsFailed(logRow.getRecordsRead());
            logRow.setDetail("Failed: " + e.getMessage());
            log.error("Daily transaction migration failed", e);
        }
        return logRow;
    }

    // ------------------------------------------------------------------ helpers

    private static MigrationLog newLog(String file, String entity, String codec) {
        MigrationLog logRow = new MigrationLog();
        logRow.setSourceFile(file);
        logRow.setEntity(entity);
        logRow.setCodec(codec);
        return logRow;
    }

    private static int parseInt(String digits) {
        try {
            return Integer.parseInt(digits.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Counts loaded per entity, used by the migration status endpoint. */
    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("users", users.count());
        counts.put("customers", customers.count());
        counts.put("accounts", accounts.count());
        counts.put("cards", cards.count());
        counts.put("cardXrefs", xrefs.count());
        counts.put("transactionTypes", transactionTypes.count());
        counts.put("transactionCategories", transactionCategories.count());
        counts.put("disclosureGroups", disclosureGroups.count());
        counts.put("categoryBalances", categoryBalances.count());
        counts.put("dailyTransactions", dailyTransactions.count());
        return counts;
    }
}
