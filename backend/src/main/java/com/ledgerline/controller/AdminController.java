package com.ledgerline.controller;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.AdminUserResponse;
import com.ledgerline.dto.ApiResponse;
import com.ledgerline.dto.UpdateAccountStatusRequest;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.CustomUserDetails;
import com.ledgerline.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public AdminController(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAllUsers(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminUserResponse> users = userRepository.findAll(pageable).map(AdminUserResponse::of);
        return ResponseEntity.ok(ApiResponse.ok("Users retrieved successfully", users));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<Page<AccountResponse>>> getAllAccounts(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AccountResponse> accounts = accountService.getAllAccounts(pageable);
        return ResponseEntity.ok(ApiResponse.ok("All bank accounts retrieved successfully", accounts));
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccountStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest servletRequest) {

        String clientIp = extractClientIp(servletRequest);
        AccountResponse updated = accountService.updateAccountStatus(
                id, request.status(), request.reason(), userDetails.getUsername(), clientIp
        );
        return ResponseEntity.ok(ApiResponse.ok("Account status updated to " + request.status(), updated));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
