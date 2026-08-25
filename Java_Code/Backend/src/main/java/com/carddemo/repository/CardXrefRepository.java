package com.carddemo.repository;

import com.carddemo.domain.CardXref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Replaces CICS file {@code CCXREF} (key card number) and its non-unique alternate path
 * {@code CXACAIX} (key account id).
 *
 * <p>The legacy programs issued a single {@code READ} on {@code CXACAIX} with no tie-break rule
 * when an account owned more than one card. The services here use the deterministic lowest card
 * number so behaviour is reproducible (decision DEC-ONL-002).</p>
 */
public interface CardXrefRepository extends JpaRepository<CardXref, String> {

    List<CardXref> findByAccountIdOrderByCardNumberAsc(String accountId);

    Optional<CardXref> findFirstByAccountIdOrderByCardNumberAsc(String accountId);

    List<CardXref> findByCustomerIdOrderByCardNumberAsc(String customerId);

    List<CardXref> findAllByOrderByCardNumberAsc();
}
