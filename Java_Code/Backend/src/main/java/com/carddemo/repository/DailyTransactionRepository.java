package com.carddemo.repository;

import com.carddemo.domain.DailyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Replaces the sequential {@code DALYTRAN} input consumed by {@code CBTRN02C}. */
public interface DailyTransactionRepository extends JpaRepository<DailyTransaction, Long> {

    List<DailyTransaction> findAllByOrderByRecordNumberAsc();

    List<DailyTransaction> findByProcessedFalseOrderByRecordNumberAsc();

    long countByProcessedFalse();
}
