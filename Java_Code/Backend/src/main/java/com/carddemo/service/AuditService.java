package com.carddemo.service;

import com.carddemo.domain.AuditEvent;
import com.carddemo.dto.OperationsDtos.AuditEventDto;
import com.carddemo.repository.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes the redacted audit events required by FR-USER-007 and the security controls page.
 * Sensitive values (password, CVV, SSN, government id, full EFT id) never reach the detail text.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /**
     * Records an event in its own transaction so an audit write survives a rolled back business
     * operation such as a denied or failed attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String targetType, String targetId,
                       String outcome, String detail) {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setOutcome(outcome);
        event.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)));
        events.save(event);
    }

    public void success(String actor, String action, String targetType, String targetId, String detail) {
        record(actor, action, targetType, targetId, AuditEvent.OUTCOME_SUCCESS, detail);
    }

    public void failure(String actor, String action, String targetType, String targetId, String detail) {
        record(actor, action, targetType, targetId, AuditEvent.OUTCOME_FAILURE, detail);
    }

    @Transactional(readOnly = true)
    public List<AuditEventDto> recent(int limit) {
        return events.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(e -> new AuditEventDto(e.getId(), e.getActor(), e.getAction(), e.getTargetType(),
                        e.getTargetId(), e.getOutcome(), e.getDetail(), e.getCreatedAt()))
                .toList();
    }
}
