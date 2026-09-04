package com.carddemo.repository;

import com.carddemo.domain.PendingAuthSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Replaces the DL/I {@code GU PAUTSUM0} root read of IMS database {@code DBPAUTP0} used by
 * {@code COPAUS0C} and {@code COPAUS1C}. The IMS sequence field {@code ACCNTID} is the account
 * id, so the root read becomes an ordinary primary key lookup.
 */
public interface PendingAuthSummaryRepository extends JpaRepository<PendingAuthSummary, String> {

    /** The accounts that have a pending authorization root segment, in key order. */
    List<PendingAuthSummary> findAllByOrderByAccountIdAsc();
}
