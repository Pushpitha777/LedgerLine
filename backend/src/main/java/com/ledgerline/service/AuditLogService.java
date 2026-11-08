package com.ledgerline.service;

import com.ledgerline.entity.AuditLog;
import com.ledgerline.entity.AuditStatus;
import com.ledgerline.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists audit logs in a new independent transaction (REQUIRES_NEW)
     * so that security failure events are recorded even if the calling transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(String eventType, String principal, String ipAddress, String details, AuditStatus status) {
        try {
            AuditLog auditLog = new AuditLog(eventType, principal, ipAddress, details, status);
            auditLogRepository.save(auditLog);
            log.info("[AUDIT] Event: {}, Principal: {}, Status: {}", eventType, principal, status);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage(), e);
        }
    }
}
