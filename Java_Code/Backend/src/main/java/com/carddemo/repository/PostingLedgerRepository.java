package com.carddemo.repository;

import com.carddemo.domain.PostingLedger;
import org.springframework.data.jpa.repository.JpaRepository;

/** Idempotency ledger for posting (FR-BATCH-005). */
public interface PostingLedgerRepository extends JpaRepository<PostingLedger, PostingLedger.Key> {
}
