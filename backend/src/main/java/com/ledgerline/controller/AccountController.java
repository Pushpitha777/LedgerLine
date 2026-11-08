package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.CreateAccountRequest;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        AccountResponse response = accountService.createAccount(request, userDetails.getUserId(), clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Bank account created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AccountResponse> accounts = accountService.getUserAccounts(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Accounts retrieved successfully", accounts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AccountResponse account = accountService.getAccountById(id, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Account details retrieved successfully", account));
    }

    @GetMapping("/{id}/statement")
    public ResponseEntity<ApiResponse<AccountStatementResponse>> getAccountStatement(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        AccountStatementResponse statement = accountService.getAccountStatement(id, userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.ok("Account statement generated successfully", statement));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
