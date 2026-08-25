package com.carddemo.repository;

import com.carddemo.domain.BatchRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Durable batch run history; replaces reading JES job output for return codes. */
public interface BatchRunRepository extends JpaRepository<BatchRun, Long> {

    List<BatchRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<BatchRun> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

    boolean existsByStatus(String status);
}
