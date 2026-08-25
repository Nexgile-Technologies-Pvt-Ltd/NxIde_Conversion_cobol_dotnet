package com.carddemo.repository;

import com.carddemo.domain.InterestCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Idempotency ledger for interest calculation (FR-BATCH-005). */
public interface InterestChargeRepository extends JpaRepository<InterestCharge, InterestCharge.Key> {

    List<InterestCharge> findByIdCycleId(String cycleId);

    boolean existsByIdCycleId(String cycleId);
}
