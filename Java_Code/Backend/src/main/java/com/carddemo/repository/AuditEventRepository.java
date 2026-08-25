package com.carddemo.repository;

import com.carddemo.domain.AuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Audit trail for privileged operations (FR-USER-007). */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditEvent> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);
}
