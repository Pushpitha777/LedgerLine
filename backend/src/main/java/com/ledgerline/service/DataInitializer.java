package com.ledgerline.service;

import com.ledgerline.entity.*;
import com.ledgerline.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Initializes baseline roles and pre-seeded demo user accounts for local demo execution.
 * Runs on non-test profiles when the application starts up.
 */
@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            RoleRepository roleRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[DATA-INIT] Initializing LedgerLine roles, system vault, and demo accounts...");

        // 1. Ensure Roles exist
        Role roleAccountHolder = roleRepository.findByName(RoleName.ROLE_ACCOUNT_HOLDER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ACCOUNT_HOLDER)));
        Role roleAdmin = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN)));
        Role roleAuditor = roleRepository.findByName(RoleName.ROLE_AUDITOR)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_AUDITOR)));

        // 2. Ensure System Vault Account exists
        Account vault = accountRepository.findByAccountNumber("SYSTEM_VAULT_INR")
                .orElseGet(() -> {
                    Account v = new Account("SYSTEM_VAULT_INR", null, "SYSTEM_VAULT", 1000000000L);
                    return accountRepository.save(v);
                });

        // 3. Ensure Demo Users exist
        if (!userRepository.existsByUsername("alice")) {
            User alice = new User("alice", "alice@example.com", passwordEncoder.encode("Password123!"), "Alice Smith");
            alice.setRoles(Set.of(roleAccountHolder));
            User savedAlice = userRepository.save(alice);

            Account aliceAccount = new Account("LL1000000001", savedAlice, "SAVINGS", 150000L); // ₹1,500.00
            accountRepository.save(aliceAccount);

            // Double-entry record for Alice initial balance
            Transaction tAlice = new Transaction(
                    "TXN-INIT-ALICE", TransactionType.DEPOSIT, null, aliceAccount, 150000L, "Initial Deposit", "INIT-ALICE"
            );
            tAlice.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTxAlice = transactionRepository.save(tAlice);

            ledgerEntryRepository.save(new LedgerEntry(savedTxAlice, vault, LedgerEntryType.DEBIT, 150000L, vault.getBalancePaise()));
            ledgerEntryRepository.save(new LedgerEntry(savedTxAlice, aliceAccount, LedgerEntryType.CREDIT, 150000L, 150000L));
            log.info("[DATA-INIT] Provisioned demo user 'alice' with account LL1000000001 (₹1,500.00)");
        }

        if (!userRepository.existsByUsername("bob")) {
            User bob = new User("bob", "bob@example.com", passwordEncoder.encode("Password123!"), "Bob Jones");
            bob.setRoles(Set.of(roleAccountHolder));
            User savedBob = userRepository.save(bob);

            Account bobAccount = new Account("LL1000000002", savedBob, "SAVINGS", 50000L); // ₹500.00
            accountRepository.save(bobAccount);

            // Double-entry record for Bob initial balance
            Transaction tBob = new Transaction(
                    "TXN-INIT-BOB", TransactionType.DEPOSIT, null, bobAccount, 50000L, "Initial Deposit", "INIT-BOB"
            );
            tBob.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTxBob = transactionRepository.save(tBob);

            ledgerEntryRepository.save(new LedgerEntry(savedTxBob, vault, LedgerEntryType.DEBIT, 50000L, vault.getBalancePaise()));
            ledgerEntryRepository.save(new LedgerEntry(savedTxBob, bobAccount, LedgerEntryType.CREDIT, 50000L, 50000L));
            log.info("[DATA-INIT] Provisioned demo user 'bob' with account LL1000000002 (₹500.00)");
        }

        if (!userRepository.existsByUsername("auditor")) {
            User auditor = new User("auditor", "auditor@example.com", passwordEncoder.encode("Password123!"), "Compliance Auditor");
            auditor.setRoles(Set.of(roleAuditor));
            userRepository.save(auditor);
            log.info("[DATA-INIT] Provisioned auditor user 'auditor' with ROLE_AUDITOR");
        }

        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", "admin@example.com", passwordEncoder.encode("Password123!"), "System Administrator");
            admin.setRoles(Set.of(roleAdmin));
            userRepository.save(admin);
            log.info("[DATA-INIT] Provisioned admin user 'admin' with ROLE_ADMIN");
        }

        log.info("[DATA-INIT] Baseline initialization complete. Ready to serve requests.");
    }
}
