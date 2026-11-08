package com.ledgerline.service;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.LedgerSummaryResponse;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditorInvariantTest {

    @Autowired
    private BankingLedgerService bankingLedgerService;

    @Autowired
    private AuditorService auditorService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    private User alice;
    private User bob;
    private Account aliceAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        // Ensure SYSTEM_VAULT_INR exists
        if (accountRepository.findByAccountNumber("SYSTEM_VAULT_INR").isEmpty()) {
            Account vault = new Account("SYSTEM_VAULT_INR", null, "SYSTEM_VAULT", 100000000000L);
            accountRepository.save(vault);
        }

        alice = userRepository.save(new User("alice_inv", "alice_inv@example.com", "pass", "Alice Invariant"));
        bob = userRepository.save(new User("bob_inv", "bob_inv@example.com", "pass", "Bob Invariant"));

        aliceAccount = accountRepository.save(new Account("LL1000000010", alice, "SAVINGS", 0L));
        bobAccount = accountRepository.save(new Account("LL1000000020", bob, "SAVINGS", 0L));
    }

    @Test
    @DisplayName("Mathematical Invariant: Sum of Debits MUST equal Sum of Credits after deposits, transfers, and withdrawals")
    void testDoubleEntryZeroSumBalanceInvariant() {
        // 1. Initial deposit to Alice (₹1,000.00)
        bankingLedgerService.deposit(
                new DepositRequest(aliceAccount.getId(), new BigDecimal("1000.00"), "Deposit Alice"),
                "dep-inv-1", alice.getId(), "127.0.0.1"
        );

        // 2. Initial deposit to Bob (₹2,500.00)
        bankingLedgerService.deposit(
                new DepositRequest(bobAccount.getId(), new BigDecimal("2500.00"), "Deposit Bob"),
                "dep-inv-2", bob.getId(), "127.0.0.1"
        );

        // 3. Inter-account transfer: Bob -> Alice (₹450.00)
        bankingLedgerService.transfer(
                new TransferRequest(bobAccount.getId(), aliceAccount.getAccountNumber(), new BigDecimal("450.00"), "Gift"),
                "trf-inv-1", bob.getId(), "127.0.0.1"
        );

        // 4. Withdrawal from Alice (₹200.00)
        bankingLedgerService.withdraw(
                new WithdrawRequest(aliceAccount.getId(), new BigDecimal("200.00"), "ATM cash"),
                "wth-inv-1", alice.getId(), "127.0.0.1"
        );

        // Verify with Auditor Service
        LedgerSummaryResponse summary = auditorService.verifyBooksBalanced("auditor_agent");

        assertNotNull(summary);
        assertTrue(summary.booksBalanced(), "Books must be strictly balanced: Sum(Debits) == Sum(Credits)");
        assertEquals(0L, summary.differencePaise(), "Difference between total debits and credits must be exactly 0 paise");
        assertEquals(summary.totalDebitsPaise(), summary.totalCreditsPaise());
        assertTrue(summary.totalLedgerEntries() >= 8, "Expected at least 8 ledger entries (2 per transaction)");
    }
}
