package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.LedgerSummaryResponse;
import com.ledgerline.entity.AuditLog;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.service.AuditorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditor")
@PreAuthorize("hasRole('AUDITOR')")
public class AuditorController {

    private final AuditorService auditorService;

    public AuditorController(AuditorService auditorService) {
        this.auditorService = auditorService;
    }

    @GetMapping("/ledger-summary")
    public ResponseEntity<ApiResponse<LedgerSummaryResponse>> getLedgerSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        LedgerSummaryResponse summary = auditorService.verifyBooksBalanced(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Double-Entry ledger verification completed", summary));
    }

    @GetMapping("/ledger-entries")
    public ResponseEntity<ApiResponse<Page<LedgerEntry>>> getLedgerEntries(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<LedgerEntry> entries = auditorService.getLedgerEntries(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Ledger entries retrieved successfully", entries));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getAuditLogs(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLog> logs = auditorService.getAuditLogs(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Security audit logs retrieved successfully", logs));
    }
}
