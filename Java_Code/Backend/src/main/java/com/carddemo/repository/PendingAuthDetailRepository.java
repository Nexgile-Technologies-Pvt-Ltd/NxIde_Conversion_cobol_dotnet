package com.carddemo.repository;

import com.carddemo.domain.PendingAuthDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Replaces the DL/I {@code GNP PAUTDTL1} child scans of IMS database {@code DBPAUTP0} used by
 * {@code COPAUS0C} (the summary browse) and {@code COPAUS1C} (detail, F8 next).
 *
 * <p>The IMS key {@code PAUT9CTS} holds the authorization date and time as nine's complements,
 * so the physical order of the child segments under one parent is newest first. Every query
 * here orders by {@code auth_key} ascending, which reproduces that order exactly: an
 * unqualified {@code GNP} is the next row after the current key, and a qualified
 * {@code GNP} that repositions at or after a key is a {@code >=} predicate.</p>
 */
public interface PendingAuthDetailRepository
        extends JpaRepository<PendingAuthDetail, PendingAuthDetail.Key> {

    /**
     * Forward keyset page within one account (F8). Equivalent to repositioning the parent scan
     * after {@code fromKey} and issuing unqualified {@code GNP} calls until the page is full.
     */
    @Query("""
            select d from PendingAuthDetail d
            where d.id.accountId = :accountId
              and d.id.authKey > :fromKey
              and (:filter = '' or d.cardNumber like concat('%', :filter, '%'))
              and (:fraudOnly = false or d.authFraud = 'F')
            order by d.id.authKey asc
            """)
    List<PendingAuthDetail> findForward(@Param("accountId") String accountId,
                                        @Param("fromKey") String fromKey,
                                        @Param("filter") String filter,
                                        @Param("fraudOnly") boolean fraudOnly,
                                        Pageable pageable);

    /** Backward keyset page within one account (F7); results are descending and the caller reverses. */
    @Query("""
            select d from PendingAuthDetail d
            where d.id.accountId = :accountId
              and d.id.authKey < :beforeKey
              and (:filter = '' or d.cardNumber like concat('%', :filter, '%'))
              and (:fraudOnly = false or d.authFraud = 'F')
            order by d.id.authKey desc
            """)
    List<PendingAuthDetail> findBackward(@Param("accountId") String accountId,
                                         @Param("beforeKey") String beforeKey,
                                         @Param("filter") String filter,
                                         @Param("fraudOnly") boolean fraudOnly,
                                         Pageable pageable);

    /**
     * The authorization that follows {@code afterKey} under the same parent, which is what F8 on
     * the detail screen ({@code COPAUS1C}) advances to.
     */
    @Query("""
            select d from PendingAuthDetail d
            where d.id.accountId = :accountId
              and d.id.authKey > :afterKey
            order by d.id.authKey asc
            """)
    List<PendingAuthDetail> findNextInParent(@Param("accountId") String accountId,
                                             @Param("afterKey") String afterKey,
                                             Pageable pageable);

    /** The authorization that precedes {@code beforeKey} under the same parent. */
    @Query("""
            select d from PendingAuthDetail d
            where d.id.accountId = :accountId
              and d.id.authKey < :beforeKey
            order by d.id.authKey desc
            """)
    List<PendingAuthDetail> findPreviousInParent(@Param("accountId") String accountId,
                                                 @Param("beforeKey") String beforeKey,
                                                 Pageable pageable);

    /** Every authorization of one account in physical order, newest first. */
    List<PendingAuthDetail> findByIdAccountIdOrderByIdAuthKeyAsc(String accountId);

    /** Row count for one account, used for the "n record(s)" note on the list screen. */
    long countByIdAccountId(String accountId);

    /** Rows currently reported as fraud for one account. */
    long countByIdAccountIdAndAuthFraud(String accountId, String authFraud);
}
