package com.ledgerline.service;

import com.ledgerline.dto.CachedResponse;
import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.AuditStatus;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerEntryType;
import com.ledgerline.entity.Transaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.entity.User;
import com.ledgerline.exception.AccountStatusException;
import com.ledgerline.exception.InsufficientBalanceException;
import com.ledgerline.exception.ResourceNotFoundException;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.TransactionRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.util.MoneyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class BankingLedgerService {

    private static final Logger log = LoggerFactory.getLogger(BankingLedgerService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;

    @Value("${ledgerline.system.vault-account-number:SYSTEM_VAULT_INR}")
    private String vaultAccountNumber;

    public BankingLedgerService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            UserRepository userRepository,
            IdempotencyService idempotencyService,
            AuditLogService auditLogService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
    }

    /**
     * Executes an external money deposit.
     * Double-Entry Model:
     * - DEBIT:  SYSTEM_VAULT_INR (increases bank cash float)
     * - CREDIT: Customer Account (increases customer liability/balance)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey, UUID callingUserId, String ipAddress) {
        String payloadHash = idempotencyService.computePayloadHash(request);

        // 1. Idempotency Check
        Optional<CachedResponse> cached = idempotencyService.findExistingResponse(idempotencyKey, callingUserId, payloadHash);
        if (cached.isPresent()) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(tx -> TransactionResponse.of(tx, getAccountBalance(tx.getDestinationAccount().getId())))
                    .orElseThrow(() -> new IllegalStateException("Cached idempotency state mismatch"));
        }

        User caller = getCaller(callingUserId);
        long amountPaise = MoneyUtil.toPaise(request.amountRupees());

        // 2. Lock Customer Account and Vault Account
        Account destinationAccount = accountRepository.findByIdForUpdate(request.destinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.destinationAccountId()));

        // Ownership verification (IDOR protection)
        validateAccountOwnership(destinationAccount, callingUserId, "deposit");
        validateAccountActive(destinationAccount);

        Account vaultAccount = accountRepository.findByAccountNumberForUpdate(vaultAccountNumber)
                .orElseThrow(() -> new IllegalStateException("System vault account not initialized: " + vaultAccountNumber));

        // 3. Update Balances
        destinationAccount.setBalancePaise(destinationAccount.getBalancePaise() + amountPaise);
        vaultAccount.setBalancePaise(vaultAccount.getBalancePaise() + amountPaise);

        accountRepository.save(destinationAccount);
        accountRepository.save(vaultAccount);

        // 4. Create Transaction Record
        String referenceNumber = generateReferenceNumber("DEP");
        Transaction transaction = new Transaction(
                referenceNumber,
                TransactionType.DEPOSIT,
                null,
                destinationAccount,
                amountPaise,
                request.description() != null ? request.description() : "External Deposit",
                idempotencyKey
        );
        transaction.setStatus(TransactionStatus.COMPLETED);
        Transaction savedTx = transactionRepository.save(transaction);

        // 5. Create Double-Entry Ledger Entries
        LedgerEntry vaultDebit = new LedgerEntry(
                savedTx, vaultAccount, LedgerEntryType.DEBIT, amountPaise, vaultAccount.getBalancePaise());
        LedgerEntry customerCredit = new LedgerEntry(
                savedTx, destinationAccount, LedgerEntryType.CREDIT, amountPaise, destinationAccount.getBalancePaise());

        ledgerEntryRepository.save(vaultDebit);
        ledgerEntryRepository.save(customerCredit);

        TransactionResponse response = TransactionResponse.of(savedTx, destinationAccount.getBalancePaise());

        // 6. Record Idempotency & Audit Log
        idempotencyService.recordResponse(idempotencyKey, caller, "/api/transactions/deposit", payloadHash, 200, response);
        auditLogService.recordSecurityEvent("DEPOSIT_COMPLETED", caller.getUsername(), ipAddress,
                "Deposited " + MoneyUtil.formatInr(amountPaise) + " to account " + destinationAccount.getAccountNumber(), AuditStatus.SUCCESS);

        return response;
    }

    /**
     * Executes an ATM / cash withdrawal.
     * Double-Entry Model:
     * - DEBIT:  Customer Account (decreases customer balance)
     * - CREDIT: SYSTEM_VAULT_INR (decreases bank vault cash)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(WithdrawRequest request, String idempotencyKey, UUID callingUserId, String ipAddress) {
        String payloadHash = idempotencyService.computePayloadHash(request);

        // 1. Idempotency Check
        Optional<CachedResponse> cached = idempotencyService.findExistingResponse(idempotencyKey, callingUserId, payloadHash);
        if (cached.isPresent()) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(tx -> TransactionResponse.of(tx, getAccountBalance(tx.getSourceAccount().getId())))
                    .orElseThrow(() -> new IllegalStateException("Cached idempotency state mismatch"));
        }

        User caller = getCaller(callingUserId);
        long amountPaise = MoneyUtil.toPaise(request.amountRupees());

        // 2. Lock Source Account
        Account sourceAccount = accountRepository.findByIdForUpdate(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + request.sourceAccountId()));

        // Ownership verification (IDOR protection)
        validateAccountOwnership(sourceAccount, callingUserId, "withdraw");
        validateAccountActive(sourceAccount);

        // Overdraft verification
        if (sourceAccount.getBalancePaise() < amountPaise) {
            auditLogService.recordSecurityEvent("WITHDRAW_INSUFFICIENT_FUNDS", caller.getUsername(), ipAddress,
                    "Attempted withdrawal of " + MoneyUtil.formatInr(amountPaise) + " with balance " +
                            MoneyUtil.formatInr(sourceAccount.getBalancePaise()), AuditStatus.FAILURE);
            throw new InsufficientBalanceException("Insufficient account balance. Available: " +
                    MoneyUtil.formatInr(sourceAccount.getBalancePaise()) + ", Requested: " + MoneyUtil.formatInr(amountPaise));
        }

        Account vaultAccount = accountRepository.findByAccountNumberForUpdate(vaultAccountNumber)
                .orElseThrow(() -> new IllegalStateException("System vault account not initialized: " + vaultAccountNumber));

        // 3. Update Balances
        sourceAccount.setBalancePaise(sourceAccount.getBalancePaise() - amountPaise);
        vaultAccount.setBalancePaise(vaultAccount.getBalancePaise() - amountPaise);

        accountRepository.save(sourceAccount);
        accountRepository.save(vaultAccount);

        // 4. Create Transaction Record
        String referenceNumber = generateReferenceNumber("WTH");
        Transaction transaction = new Transaction(
                referenceNumber,
                TransactionType.WITHDRAWAL,
                sourceAccount,
                null,
                amountPaise,
                request.description() != null ? request.description() : "Cash Withdrawal",
                idempotencyKey
        );
        transaction.setStatus(TransactionStatus.COMPLETED);
        Transaction savedTx = transactionRepository.save(transaction);

        // 5. Create Double-Entry Ledger Entries
        LedgerEntry customerDebit = new LedgerEntry(
                savedTx, sourceAccount, LedgerEntryType.DEBIT, amountPaise, sourceAccount.getBalancePaise());
        LedgerEntry vaultCredit = new LedgerEntry(
                savedTx, vaultAccount, LedgerEntryType.CREDIT, amountPaise, vaultAccount.getBalancePaise());

        ledgerEntryRepository.save(customerDebit);
        ledgerEntryRepository.save(vaultCredit);

        TransactionResponse response = TransactionResponse.of(savedTx, sourceAccount.getBalancePaise());

        // 6. Record Idempotency & Audit Log
        idempotencyService.recordResponse(idempotencyKey, caller, "/api/transactions/withdraw", payloadHash, 200, response);
        auditLogService.recordSecurityEvent("WITHDRAW_COMPLETED", caller.getUsername(), ipAddress,
                "Withdrew " + MoneyUtil.formatInr(amountPaise) + " from account " + sourceAccount.getAccountNumber(), AuditStatus.SUCCESS);

        return response;
    }

    /**
     * Executes an inter-account money transfer.
     * Double-Entry Model:
     * - DEBIT:  Source Account (decreases sender balance)
     * - CREDIT: Destination Account (increases recipient balance)
     *
     * Concurrency & Deadlock Protocol:
     * Deterministically locks accounts in ascending order of Account ID:
     * min(sourceId, destId) followed by max(sourceId, destId).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey, UUID callingUserId, String ipAddress) {
        String payloadHash = idempotencyService.computePayloadHash(request);

        // 1. Idempotency Check
        Optional<CachedResponse> cached = idempotencyService.findExistingResponse(idempotencyKey, callingUserId, payloadHash);
        if (cached.isPresent()) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(tx -> TransactionResponse.of(tx, getAccountBalance(tx.getSourceAccount().getId())))
                    .orElseThrow(() -> new IllegalStateException("Cached idempotency state mismatch"));
        }

        User caller = getCaller(callingUserId);
        long amountPaise = MoneyUtil.toPaise(request.amountRupees());

        // 2. Resolve destination account ID without loading entity into session cache prior to lock
        UUID sourceId = request.sourceAccountId();
        UUID destId = accountRepository.findIdByAccountNumber(request.destinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.destinationAccountNumber()));

        if (sourceId.equals(destId)) {
            throw new IllegalArgumentException("Cannot transfer funds to the same account.");
        }

        // 3. Acquire Row Locks in Deterministic Order (Eliminates Deadlocks)
        Account sourceAccount;
        Account destinationAccount;

        if (sourceId.compareTo(destId) < 0) {
            sourceAccount = accountRepository.findByIdForUpdate(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceId));
            destinationAccount = accountRepository.findByIdForUpdate(destId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destId));
        } else {
            destinationAccount = accountRepository.findByIdForUpdate(destId)
                    .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + destId));
            sourceAccount = accountRepository.findByIdForUpdate(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + sourceId));
        }

        // 4. Validations
        validateAccountOwnership(sourceAccount, callingUserId, "transfer");
        validateAccountActive(sourceAccount);
        validateAccountActive(destinationAccount);

        if (sourceAccount.getBalancePaise() < amountPaise) {
            auditLogService.recordSecurityEvent("TRANSFER_INSUFFICIENT_FUNDS", caller.getUsername(), ipAddress,
                    "Transfer failed: Available " + MoneyUtil.formatInr(sourceAccount.getBalancePaise()) +
                            ", Requested " + MoneyUtil.formatInr(amountPaise), AuditStatus.FAILURE);
            throw new InsufficientBalanceException("Insufficient funds. Available: " +
                    MoneyUtil.formatInr(sourceAccount.getBalancePaise()) + ", Required: " + MoneyUtil.formatInr(amountPaise));
        }

        // 5. Apply Balances
        sourceAccount.setBalancePaise(sourceAccount.getBalancePaise() - amountPaise);
        destinationAccount.setBalancePaise(destinationAccount.getBalancePaise() + amountPaise);

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // 6. Create Transaction Record
        String referenceNumber = generateReferenceNumber("TRF");
        Transaction transaction = new Transaction(
                referenceNumber,
                TransactionType.TRANSFER,
                sourceAccount,
                destinationAccount,
                amountPaise,
                request.description() != null ? request.description() : "Inter-Account Transfer",
                idempotencyKey
        );
        transaction.setStatus(TransactionStatus.COMPLETED);
        Transaction savedTx = transactionRepository.save(transaction);

        // 7. Create Double-Entry Ledger Entries (Total Debits == Total Credits)
        LedgerEntry debitEntry = new LedgerEntry(
                savedTx, sourceAccount, LedgerEntryType.DEBIT, amountPaise, sourceAccount.getBalancePaise());
        LedgerEntry creditEntry = new LedgerEntry(
                savedTx, destinationAccount, LedgerEntryType.CREDIT, amountPaise, destinationAccount.getBalancePaise());

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        TransactionResponse response = TransactionResponse.of(savedTx, sourceAccount.getBalancePaise());

        // 8. Record Idempotency & Audit Log
        idempotencyService.recordResponse(idempotencyKey, caller, "/api/transactions/transfer", payloadHash, 200, response);
        auditLogService.recordSecurityEvent("TRANSFER_COMPLETED", caller.getUsername(), ipAddress,
                "Transferred " + MoneyUtil.formatInr(amountPaise) + " from " + sourceAccount.getAccountNumber() +
                        " to " + destinationAccount.getAccountNumber() + " [Ref: " + referenceNumber + "]", AuditStatus.SUCCESS);

        return response;
    }

    private void validateAccountOwnership(Account account, UUID callingUserId, String operation) {
        if (account.getUser() == null || !account.getUser().getId().equals(callingUserId)) {
            String username = userRepository.findById(callingUserId).map(User::getUsername).orElse(callingUserId.toString());
            auditLogService.recordSecurityEvent("IDOR_ATTEMPT_BLOCKED", username, "N/A",
                    "Unauthorized " + operation + " attempt on account ID: " + account.getId(), AuditStatus.FAILURE);
            throw new UnauthorizedAccountAccessException("Access Denied: You do not have permission to access account " + account.getAccountNumber());
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountStatusException("Operation rejected: Account " + account.getAccountNumber() +
                    " is currently " + account.getStatus());
        }
    }

    private User getCaller(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private long getAccountBalance(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getBalancePaise)
                .orElse(0L);
    }

    private String generateReferenceNumber(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
