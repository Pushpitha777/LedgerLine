package com.ledgerline.service;

import com.ledgerline.dto.LedgerSummaryResponse;
import com.ledgerline.entity.AuditLog;
import com.ledgerline.entity.AuditStatus;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerEntryType;
import com.ledgerline.repository.AuditLogRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditorService {

    private static final Logger log = LoggerFactory.getLogger(AuditorService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    public AuditorService(
            LedgerEntryRepository ledgerEntryRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Mathematically verifies that total debits equal total credits across all recorded ledger entries.
     * Core Invariant: sum(DEBIT) == sum(CREDIT).
     */
    @Transactional(readOnly = true)
    public LedgerSummaryResponse verifyBooksBalanced(String auditorUsername) {
        long totalDebits = ledgerEntryRepository.sumAmountByEntryType(LedgerEntryType.DEBIT);
        long totalCredits = ledgerEntryRepository.sumAmountByEntryType(LedgerEntryType.CREDIT);
        long totalCount = ledgerEntryRepository.count();

        LedgerSummaryResponse summary = LedgerSummaryResponse.of(totalDebits, totalCredits, totalCount);

        if (!summary.booksBalanced()) {
            auditLogService.recordSecurityEvent(
                    "LEDGER_IMBALANCE_ALERT",
                    auditorUsername,
                    "INTERNAL",
                    "CRITICAL: Ledger books are out of balance! Difference: " + summary.differencePaise() + " paise.",
                    AuditStatus.FAILURE
            );
            log.error("CRITICAL: Ledger imbalance detected! Debits: {}, Credits: {}", totalDebits, totalCredits);
        } else {
            log.info("Ledger books verified balanced by {}. Debits == Credits == {} paise across {} entries.",
                    auditorUsername, totalDebits, totalCount);
        }

        return summary;
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> getLedgerEntries(Pageable pageable) {
        return ledgerEntryRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
