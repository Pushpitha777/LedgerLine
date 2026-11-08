package com.ledgerline.service;

import com.ledgerline.dto.DepositRequest;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.dto.WithdrawRequest;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.User;
import com.ledgerline.exception.InsufficientBalanceException;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyStressTest {

    @Autowired
    private BankingLedgerService bankingLedgerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        if (accountRepository.findByAccountNumber("SYSTEM_VAULT_INR").isEmpty()) {
            Account vault = new Account("SYSTEM_VAULT_INR", null, "SYSTEM_VAULT", 100000000000L);
            accountRepository.save(vault);
        }
    }

    @Test
    @DisplayName("Race-Condition Test: 10 parallel threads withdrawing ₹200 from a ₹500 balance must strictly prevent overdraft")
    void testConcurrentWithdrawalsPreventOverdraft() throws InterruptedException {
        // Setup user with an initial balance of ₹500.00 (50,000 paise)
        String uname = "stress_user_" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User(uname, uname + "@test.com", "pass", "Stress Tester"));
        Account account = accountRepository.save(new Account("LL" + System.nanoTime(), user, "SAVINGS", 50000L));

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final String idempKey = "stress-wth-" + i + "-" + UUID.randomUUID();
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Wait until all threads are prepped to strike at the exact same millisecond
                    WithdrawRequest req = new WithdrawRequest(account.getId(), new BigDecimal("200.00"), "Concurrent ATM");
                    bankingLedgerService.withdraw(req, idempKey, user.getId(), "127.0.0.1");
                    successCount.incrementAndGet();
                } catch (InsufficientBalanceException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // FIRE ALL THREADS SIMULTANEOUSLY
        finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Verification
        Account updatedAccount = accountRepository.findById(account.getId()).orElseThrow();

        // Since balance was ₹500 and each withdrawal is ₹200:
        // Exactly 2 withdrawals can succeed (2 * 200 = ₹400 spent).
        // The remaining 8 must be rejected because balance drops to ₹100, which is < ₹200.
        assertEquals(2, successCount.get(), "Exactly 2 withdrawals must succeed");
        assertEquals(8, failureCount.get(), "Exactly 8 withdrawals must fail with InsufficientBalanceException");
        assertEquals(10000L, updatedAccount.getBalancePaise(), "Final balance must be exactly ₹100.00 (10,000 paise)");
        assertTrue(updatedAccount.getBalancePaise() >= 0L, "Account balance must NEVER become negative!");
    }

    @Test
    @DisplayName("Deadlock Prevention Test: 20 simultaneous reciprocal transfers between Account A and B must execute with zero deadlocks")
    void testConcurrentReciprocalTransfersNoDeadlock() throws InterruptedException, ExecutionException {
        // Setup two users with ₹10,000.00 (1,000,000 paise) each
        String u1Name = "user_a_" + UUID.randomUUID().toString().substring(0, 8);
        String u2Name = "user_b_" + UUID.randomUUID().toString().substring(0, 8);

        User userA = userRepository.save(new User(u1Name, u1Name + "@test.com", "pass", "User A"));
        User userB = userRepository.save(new User(u2Name, u2Name + "@test.com", "pass", "User B"));

        Account accountA = accountRepository.save(new Account("LL" + System.nanoTime() + "1", userA, "SAVINGS", 1000000L));
        Account accountB = accountRepository.save(new Account("LL" + System.nanoTime() + "2", userB, "SAVINGS", 1000000L));

        int threadsPerDirection = 10;
        int totalThreads = threadsPerDirection * 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Boolean>> futures = new ArrayList<>();

        // 10 threads transfer from A -> B (₹50.00 each)
        for (int i = 0; i < threadsPerDirection; i++) {
            final String key = "recip-a2b-" + i + "-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                TransferRequest req = new TransferRequest(accountA.getId(), accountB.getAccountNumber(), new BigDecimal("50.00"), "A to B");
                bankingLedgerService.transfer(req, key, userA.getId(), "127.0.0.1");
                return true;
            }));
        }

        // 10 threads transfer from B -> A (₹50.00 each)
        for (int i = 0; i < threadsPerDirection; i++) {
            final String key = "recip-b2a-" + i + "-" + UUID.randomUUID();
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                TransferRequest req = new TransferRequest(accountB.getId(), accountA.getAccountNumber(), new BigDecimal("50.00"), "B to A");
                bankingLedgerService.transfer(req, key, userB.getId(), "127.0.0.1");
                return true;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Strike simultaneously from both directions

        int successfulTransfers = 0;
        for (Future<Boolean> f : futures) {
            try {
                if (f.get(10, TimeUnit.SECONDS)) {
                    successfulTransfers++;
                }
            } catch (TimeoutException e) {
                fail("Deadlock detected! Transfer thread timed out waiting for lock.");
            }
        }
        executor.shutdown();

        assertEquals(20, successfulTransfers, "All 20 reciprocal transfers must succeed without deadlock");

        Account refreshedA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account refreshedB = accountRepository.findById(accountB.getId()).orElseThrow();

        // Conservation of money invariant: Total money in the closed system remains exactly ₹20,000.00
        assertEquals(2000000L, refreshedA.getBalancePaise() + refreshedB.getBalancePaise(),
                "Total money across accounts must be conserved (₹20,000.00)");
        assertEquals(1000000L, refreshedA.getBalancePaise(), "Account A net balance should equal original balance");
        assertEquals(1000000L, refreshedB.getBalancePaise(), "Account B net balance should equal original balance");
    }

    @Test
    @DisplayName("Concurrent Idempotency Test: Parallel requests using the exact same Idempotency-Key update balance only once")
    void testConcurrentDuplicateRequestsIdempotency() throws InterruptedException {
        String uname = "idemp_user_" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(new User(uname, uname + "@test.com", "pass", "Idemp User"));
        Account account = accountRepository.save(new Account("LL" + System.nanoTime(), user, "SAVINGS", 100000L)); // ₹1,000.00

        final String sharedIdempotencyKey = "shared-duplicate-key-" + UUID.randomUUID();
        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger completedCalls = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    DepositRequest req = new DepositRequest(account.getId(), new BigDecimal("250.00"), "Shared Key Deposit");
                    bankingLedgerService.deposit(req, sharedIdempotencyKey, user.getId(), "127.0.0.1");
                    completedCalls.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Launch all threads with identical idempotency key
        finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Account refreshed = accountRepository.findById(account.getId()).orElseThrow();

        // Initial balance was ₹1,000. Exactly 1 deposit of ₹250 should be credited.
        // Balance must be ₹1,250.00 (125,000 paise), NOT ₹3,000.00 (which would mean 8 duplicate deposits)!
        assertEquals(125000L, refreshed.getBalancePaise(),
                "Balance must reflect only ONE successful deposit (₹1,250.00), preventing duplicate payments under concurrent bursts");
    }
}
