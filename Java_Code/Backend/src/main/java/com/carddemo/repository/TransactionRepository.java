package com.carddemo.repository;

import com.carddemo.domain.Transaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Replaces CICS file {@code TRANSACT} browse/read used by {@code COTRN00C}, {@code COTRN01C},
 * {@code COTRN02C}, {@code COBIL00C}, {@code CBTRN03C} and the statement programs.
 */
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** Equivalent of the legacy "browse to HIGH-VALUES and read backwards" highest-id lookup. */
    Optional<Transaction> findFirstByOrderByTransactionIdDesc();

    List<Transaction> findByCardNumberOrderByTransactionIdAsc(String cardNumber);

    /** Forward keyset page for the transaction list (F8). */
    @Query("""
            select t from Transaction t
            where t.transactionId > :fromId
              and (:filter = '' or t.transactionId like concat(:filter, '%'))
            order by t.transactionId asc
            """)
    List<Transaction> findForward(@Param("fromId") String fromId, @Param("filter") String filter, Pageable pageable);

    /** Backward keyset page for the transaction list (F7); results are descending. */
    @Query("""
            select t from Transaction t
            where t.transactionId < :beforeId
              and (:filter = '' or t.transactionId like concat(:filter, '%'))
            order by t.transactionId desc
            """)
    List<Transaction> findBackward(@Param("beforeId") String beforeId, @Param("filter") String filter, Pageable pageable);

    /**
     * Report selection. {@code CBTRN03C} compares the first ten characters of the processing
     * timestamp lexically against the start/end date parameters, and {@code TRANREPT.jcl} sorts by
     * card number.
     */
    @Query("""
            select t from Transaction t
            where substring(t.procTs, 1, 10) >= :startDate
              and substring(t.procTs, 1, 10) <= :endDate
            order by t.cardNumber asc, t.transactionId asc
            """)
    List<Transaction> findForReport(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** Statement input: sorted by card then transaction id, matching {@code CREASTMT.JCL}. */
    @Query("select t from Transaction t order by t.cardNumber asc, t.transactionId asc")
    List<Transaction> findAllForStatements();

    long countByCardNumber(String cardNumber);

    /** Transaction volume per type code, used by the dashboard. */
    @Query("select t.typeCode, count(t) from Transaction t group by t.typeCode order by t.typeCode asc")
    List<Object[]> countByTypeCode();
}
