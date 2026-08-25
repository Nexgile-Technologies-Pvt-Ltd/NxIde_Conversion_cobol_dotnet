package com.carddemo.repository;

import com.carddemo.domain.ReportRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Durable report queue replacing the legacy {@code WRITEQ TD JOBS} JCL submission. */
public interface ReportRequestRepository extends JpaRepository<ReportRequest, Long> {

    List<ReportRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);

    List<ReportRequest> findByStatusOrderByRequestedAtAsc(String status);

    List<ReportRequest> findByRequestedByOrderByRequestedAtDesc(String requestedBy, Pageable pageable);
}
