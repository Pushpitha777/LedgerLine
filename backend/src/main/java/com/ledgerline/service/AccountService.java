package com.ledgerline.service;

import com.ledgerline.dto.AccountResponse;
import com.ledgerline.dto.AccountStatementResponse;
import com.ledgerline.dto.CreateAccountRequest;
import com.ledgerline.dto.StatementEntry;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AuditStatus;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.User;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerEntryRepository ledgerEntryRepository,
            AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request, UUID userId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String accountNumber = generateUniqueAccountNumber();
        Account account = new Account(accountNumber, user, request.accountType(), 0L);
        Account saved = accountRepository.save(account);

        auditLogService.recordSecurityEvent("ACCOUNT_CREATED", user.getUsername(), ipAddress,
                "New " + request.accountType() + " account created: " + accountNumber, AuditStatus.SUCCESS);

        return AccountResponse.of(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(AccountResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId, UUID userId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        validateOwnership(account, userId, "view account details");
        return AccountResponse.of(account);
    }

    @Transactional(readOnly = true)
    public AccountStatementResponse getAccountStatement(UUID accountId, UUID userId, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        validateOwnership(account, userId, "generate statement");

        Page<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        List<StatementEntry> statementEntries = entries.stream()
                .map(StatementEntry::of)
                .toList();

        return AccountStatementResponse.of(account, statementEntries);
    }

    // Admin Operations
    @Transactional(readOnly = true)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable).map(AccountResponse::of);
    }

    @Transactional
    public AccountResponse updateAccountStatus(
            UUID accountId, AccountStatus newStatus, String reason, String adminUsername, String ipAddress) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));

        AccountStatus oldStatus = account.getStatus();
        account.setStatus(newStatus);
        Account saved = accountRepository.save(account);

        auditLogService.recordSecurityEvent("ACCOUNT_STATUS_UPDATED", adminUsername, ipAddress,
                "Admin updated account " + account.getAccountNumber() + " status from " +
                        oldStatus + " to " + newStatus + ". Reason: " + reason, AuditStatus.SUCCESS);

        return AccountResponse.of(saved);
    }

    private void validateOwnership(Account account, UUID userId, String operation) {
        if (account.getUser() == null || !account.getUser().getId().equals(userId)) {
            auditLogService.recordSecurityEvent("IDOR_ATTEMPT_BLOCKED", userId.toString(), "N/A",
                    "Blocked unauthorized attempt to " + operation + " for account " + account.getId(), AuditStatus.FAILURE);
            throw new UnauthorizedAccountAccessException("Access Denied: You do not own account " + account.getAccountNumber());
        }
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long randomNum = 1000000000L + (long) (secureRandom.nextDouble() * 9000000000L);
            accountNumber = "LL" + randomNum;
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
