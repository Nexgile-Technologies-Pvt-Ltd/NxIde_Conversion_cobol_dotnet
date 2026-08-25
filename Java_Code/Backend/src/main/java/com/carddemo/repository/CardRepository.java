package com.carddemo.repository;

import com.carddemo.domain.Card;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Replaces CICS file {@code CARDDAT} (primary key card number, alternate index {@code CARDAIX} on
 * account) used by {@code COCRDLIC}, {@code COCRDSLC} and {@code COCRDUPC}.
 */
public interface CardRepository extends JpaRepository<Card, String> {

    List<Card> findByAccountIdOrderByCardNumberAsc(String accountId);

    Optional<Card> findFirstByAccountIdOrderByCardNumberAsc(String accountId);

    /**
     * Forward keyset page for the card list (F8). FR-CARD-002: the filters are applied inside the
     * query so {@code hasNext} is computed from the next <em>matching</em> row, not from the next
     * raw record as the legacy look-ahead did.
     */
    @Query("""
            select c from Card c
            where c.cardNumber > :fromCard
              and (:accountFilter = '' or c.accountId = :accountFilter)
              and (:cardFilter = '' or c.cardNumber = :cardFilter)
            order by c.cardNumber asc
            """)
    List<Card> findForward(@Param("fromCard") String fromCard,
                           @Param("accountFilter") String accountFilter,
                           @Param("cardFilter") String cardFilter,
                           Pageable pageable);

    /** Backward keyset page for the card list (F7); results are descending. */
    @Query("""
            select c from Card c
            where c.cardNumber < :beforeCard
              and (:accountFilter = '' or c.accountId = :accountFilter)
              and (:cardFilter = '' or c.cardNumber = :cardFilter)
            order by c.cardNumber desc
            """)
    List<Card> findBackward(@Param("beforeCard") String beforeCard,
                            @Param("accountFilter") String accountFilter,
                            @Param("cardFilter") String cardFilter,
                            Pageable pageable);
}
