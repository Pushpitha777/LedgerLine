package com.ledgerline.controller;

import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Transaction;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.repository.TransactionRepository;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.service.BankingLedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final BankingLedgerService bankingLedgerService;
    private final TransactionRepository transactionRepository;

    public TransactionController(
            BankingLedgerService bankingLedgerService,
            TransactionRepository transactionRepository) {
        this.bankingLedgerService = bankingLedgerService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        TransactionResponse response = bankingLedgerService.deposit(
                request, idempotencyKey, userDetails.getUserId(), clientIp
        );
        return ResponseEntity.ok(ApiResponse.ok("Deposit completed successfully", response));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        TransactionResponse response = bankingLedgerService.withdraw(
                request, idempotencyKey, userDetails.getUserId(), clientIp
        );
        return ResponseEntity.ok(ApiResponse.ok("Withdrawal completed successfully", response));
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        TransactionResponse response = bankingLedgerService.transfer(
                request, idempotencyKey, userDetails.getUserId(), clientIp
        );
        return ResponseEntity.ok(ApiResponse.ok("Transfer completed successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TransactionResponse> page = transactionRepository.findByUserId(userDetails.getUserId(), pageable)
                .map(tx -> TransactionResponse.of(tx, 0L));
        return ResponseEntity.ok(ApiResponse.ok("Transactions retrieved successfully", page));
    }

    @GetMapping("/{referenceNumber}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionByReference(
            @PathVariable String referenceNumber) {

        Transaction tx = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + referenceNumber));

        return ResponseEntity.ok(ApiResponse.ok("Transaction found", TransactionResponse.of(tx, 0L)));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
