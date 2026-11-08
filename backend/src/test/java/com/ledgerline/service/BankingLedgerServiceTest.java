package com.ledgerline.service;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransactionResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.*;
import com.ledgerline.exception.AccountStatusException;
import com.ledgerline.exception.InsufficientBalanceException;
import com.ledgerline.exception.UnauthorizedAccountAccessException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.repository.TransactionRepository;
import com.ledgerline.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankingLedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BankingLedgerService bankingLedgerService;

    private User alice;
    private User bob;
    private Account aliceAccount;
    private Account bobAccount;
    private Account vaultAccount;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bankingLedgerService, "vaultAccountNumber", "SYSTEM_VAULT_INR");

        alice = new User("alice", "alice@example.com", "hash", "Alice Smith");
        alice.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        bob = new User("bob", "bob@example.com", "hash", "Bob Jones");
        bob.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        aliceAccount = new Account("LL1000000001", alice, "SAVINGS", 100000L); // ₹1,000.00
        aliceAccount.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        bobAccount = new Account("LL1000000002", bob, "SAVINGS", 50000L); // ₹500.00
        bobAccount.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

        vaultAccount = new Account("SYSTEM_VAULT_INR", null, "SYSTEM_VAULT", 100000000000L);
        vaultAccount.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("Deposit should update account and vault balances, creating paired ledger entries")
    void testDepositSuccess() {
        DepositRequest request = new DepositRequest(aliceAccount.getId(), new BigDecimal("500.00"), "Paycheck");
        when(idempotencyService.findExistingResponse(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByAccountNumberForUpdate("SYSTEM_VAULT_INR")).thenReturn(Optional.of(vaultAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TransactionResponse response = bankingLedgerService.deposit(
                request, "key-dep-1", alice.getId(), "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals(150000L, aliceAccount.getBalancePaise()); // 1000 + 500 = ₹1,500.00
        assertEquals(TransactionType.DEPOSIT, response.transactionType());

        // Verify paired Double-Entry Ledger entries were persisted (1 Debit, 1 Credit)
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        verify(idempotencyService).recordResponse(eq("key-dep-1"), eq(alice), any(), any(), eq(200), any());
    }

    @Test
    @DisplayName("Withdrawal should deduct balance and throw InsufficientBalanceException if funds inadequate")
    void testWithdrawInsufficientBalance() {
        WithdrawRequest request = new WithdrawRequest(aliceAccount.getId(), new BigDecimal("2000.00"), "ATM"); // Balance is only ₹1,000
        when(idempotencyService.findExistingResponse(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));

        assertThrows(InsufficientBalanceException.class, () ->
                bankingLedgerService.withdraw(request, "key-wth-1", alice.getId(), "127.0.0.1")
        );

        assertEquals(100000L, aliceAccount.getBalancePaise()); // Untouched balance
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inter-account transfer should move exact paise and create paired ledger entries")
    void testTransferSuccess() {
        TransferRequest request = new TransferRequest(
                aliceAccount.getId(), bobAccount.getAccountNumber(), new BigDecimal("300.00"), "Split dinner"
        );

        when(idempotencyService.findExistingResponse(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(accountRepository.findIdByAccountNumber(bobAccount.getAccountNumber())).thenReturn(Optional.of(bobAccount.getId()));
        when(accountRepository.findByIdForUpdate(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByIdForUpdate(bobAccount.getId())).thenReturn(Optional.of(bobAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TransactionResponse response = bankingLedgerService.transfer(
                request, "key-trf-1", alice.getId(), "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals(70000L, aliceAccount.getBalancePaise()); // 1000 - 300 = ₹700.00
        assertEquals(80000L, bobAccount.getBalancePaise());   // 500 + 300 = ₹800.00

        // Verify 2 ledger entries created for double-entry bookkeeping
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("IDOR Attempt: User trying to withdraw from another user's account must be rejected with 403")
    void testIdorWithdrawBlocked() {
        // Alice attempts to withdraw from Bob's account
        WithdrawRequest request = new WithdrawRequest(bobAccount.getId(), new BigDecimal("100.00"), "Malicious withdraw");
        when(idempotencyService.findExistingResponse(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(bobAccount.getId())).thenReturn(Optional.of(bobAccount));

        assertThrows(UnauthorizedAccountAccessException.class, () ->
                bankingLedgerService.withdraw(request, "key-idor-1", alice.getId(), "127.0.0.1")
        );

        verify(auditLogService).recordSecurityEvent(eq("IDOR_ATTEMPT_BLOCKED"), eq("alice"), any(), any(), eq(AuditStatus.FAILURE));
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Operations on frozen accounts must be rejected with AccountStatusException")
    void testFrozenAccountRejected() {
        aliceAccount.setStatus(AccountStatus.FROZEN);

        WithdrawRequest request = new WithdrawRequest(aliceAccount.getId(), new BigDecimal("50.00"), "ATM");
        when(idempotencyService.findExistingResponse(any(), any(), any())).thenReturn(Optional.empty());
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));

        assertThrows(AccountStatusException.class, () ->
                bankingLedgerService.withdraw(request, "key-frozen-1", alice.getId(), "127.0.0.1")
        );
    }
}
