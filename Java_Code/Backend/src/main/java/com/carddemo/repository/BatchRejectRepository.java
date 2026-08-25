package com.carddemo.repository;

import com.carddemo.domain.BatchReject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Replaces the sequential {@code DALYREJS} 430-byte reject output of {@code CBTRN02C}. */
public interface BatchRejectRepository extends JpaRepository<BatchReject, Long> {

    List<BatchReject> findByBatchRunIdOrderByRecordNumberAsc(Long batchRunId);

    long countByBatchRunId(Long batchRunId);
}
