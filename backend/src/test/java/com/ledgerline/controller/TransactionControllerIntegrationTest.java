package com.ledgerline.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerline.dto.*;
import com.ledgerline.entity.Account;
import com.ledgerline.entity.Role;
import com.ledgerline.entity.RoleName;
import com.ledgerline.entity.User;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.RoleRepository;
import com.ledgerline.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AccountRepository accountRepository;

    private String aliceToken;
    private UUID aliceAccountId;
    private String aliceAccountNumber;

    private String bobToken;
    private UUID bobAccountId;
    private String bobAccountNumber;

    private String auditorToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure SYSTEM_VAULT_INR exists
        if (accountRepository.findByAccountNumber("SYSTEM_VAULT_INR").isEmpty()) {
            Account vault = new Account("SYSTEM_VAULT_INR", null, "SYSTEM_VAULT", 100000000000L);
            accountRepository.save(vault);
        }

        // 1. Setup Alice
        String aliceUser = "alice_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(aliceUser, aliceUser + "@test.com", "Password123!", "Alice Test");
        aliceToken = loginUser(aliceUser, "Password123!");

        Account aliceAcc = fetchFirstAccount(aliceToken);
        aliceAccountId = aliceAcc.getId();
        aliceAccountNumber = aliceAcc.getAccountNumber();

        // 2. Setup Bob
        String bobUser = "bob_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(bobUser, bobUser + "@test.com", "Password123!", "Bob Test");
        bobToken = loginUser(bobUser, "Password123!");

        Account bobAcc = fetchFirstAccount(bobToken);
        bobAccountId = bobAcc.getId();
        bobAccountNumber = bobAcc.getAccountNumber();

        // 3. Setup Auditor
        Role auditorRole = roleRepository.findByName(RoleName.ROLE_AUDITOR)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_AUDITOR)));
        String auditorUser = "auditor_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(auditorUser, auditorUser + "@test.com", "Password123!", "Auditor Official");
        User auditor = userRepository.findByUsername(auditorUser).orElseThrow();
        auditor.setRoles(Set.of(auditorRole));
        userRepository.save(auditor);
        auditorToken = loginUser(auditorUser, "Password123!");

        // 4. Setup Admin
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN)));
        String adminUser = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        registerUser(adminUser, adminUser + "@test.com", "Password123!", "Admin Official");
        User admin = userRepository.findByUsername(adminUser).orElseThrow();
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);
        adminToken = loginUser(adminUser, "Password123!");
    }

    @Test
    @DisplayName("Deposit endpoint should increase balance and cache idempotency responses")
    void testDepositAndIdempotencyReplay() throws Exception {
        DepositRequest req = new DepositRequest(aliceAccountId, new BigDecimal("1000.00"), "Deposit 1");

        // 1. Initial Deposit
        MvcResult res1 = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "idemp-dep-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountBalanceAfterPaise").value(100000L))
                .andReturn();

        // 2. Replay duplicate request with same key
        MvcResult res2 = mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "idemp-dep-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accountBalanceAfterPaise").value(100000L))
                .andReturn();

        // Verify balance in database did not double
        Account acc = accountRepository.findById(aliceAccountId).orElseThrow();
        assertEquals(100000L, acc.getBalancePaise(), "Balance must remain ₹1,000.00 (not duplicate credited)");
    }

    @Test
    @DisplayName("Full financial flow: Deposit -> Withdraw -> Transfer with statement verification")
    void testEndToEndFinancialOperations() throws Exception {
        // 1. Deposit ₹2,000 to Alice
        DepositRequest depReq = new DepositRequest(aliceAccountId, new BigDecimal("2000.00"), "Initial Fund");
        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "idemp-dep-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depReq)))
                .andExpect(status().isOk());

        // 2. Withdraw ₹500 from Alice
        WithdrawRequest wthReq = new WithdrawRequest(aliceAccountId, new BigDecimal("500.00"), "ATM cash");
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "idemp-wth-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wthReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountBalanceAfterPaise").value(150000L)); // 2000 - 500 = 1500

        // 3. Transfer ₹350 from Alice to Bob
        TransferRequest trfReq = new TransferRequest(aliceAccountId, bobAccountNumber, new BigDecimal("350.00"), "Split dinner");
        mockMvc.perform(post("/api/transactions/transfer")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "idemp-trf-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(trfReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountBalanceAfterPaise").value(115000L)); // 1500 - 350 = 1150

        // 4. Verify Bob's balance
        Account bobAcc = accountRepository.findById(bobAccountId).orElseThrow();
        assertEquals(35000L, bobAcc.getBalancePaise()); // 0 + 350 = ₹350.00

        // 5. Generate Statement for Alice
        mockMvc.perform(get("/api/accounts/" + aliceAccountId + "/statement")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentBalancePaise").value(115000L))
                .andExpect(jsonPath("$.data.entries").isArray());
    }

    @Test
    @DisplayName("Security: IDOR attack blocked when Alice attempts to withdraw from Bob's account")
    void testIdorBlockedOnWithdrawal() throws Exception {
        WithdrawRequest attackReq = new WithdrawRequest(bobAccountId, new BigDecimal("100.00"), "IDOR theft attempt");

        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + aliceToken) // Alice's token against Bob's accountId
                        .header("Idempotency-Key", "idor-attack-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Access Denied"));
    }

    @Test
    @DisplayName("Security: Auditor endpoint blocked for Account Holder (403), allowed for Auditor (200)")
    void testAuditorRbac() throws Exception {
        // Account holder blocked
        mockMvc.perform(get("/api/auditor/ledger-summary")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        // Auditor permitted
        mockMvc.perform(get("/api/auditor/ledger-summary")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.booksBalanced").value(true));
    }

    @Test
    @DisplayName("Security: Admin can freeze an account, after which withdrawals are rejected")
    void testAdminFreezeAccountWorkflow() throws Exception {
        // Deposit initial fund to Alice
        DepositRequest depReq = new DepositRequest(aliceAccountId, new BigDecimal("1000.00"), "Fund");
        mockMvc.perform(post("/api/transactions/deposit")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "freeze-test-dep")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(depReq)))
                .andExpect(status().isOk());

        // Admin freezes Alice's account
        UpdateAccountStatusRequest freezeReq = new UpdateAccountStatusRequest(
                com.ledgerline.entity.AccountStatus.FROZEN, "Fraud investigation"
        );
        mockMvc.perform(patch("/api/admin/accounts/" + aliceAccountId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freezeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));

        // Alice attempts withdrawal on frozen account -> rejected
        WithdrawRequest wthReq = new WithdrawRequest(aliceAccountId, new BigDecimal("100.00"), "ATM");
        mockMvc.perform(post("/api/transactions/withdraw")
                        .header("Authorization", "Bearer " + aliceToken)
                        .header("Idempotency-Key", "freeze-test-wth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wthReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("is currently FROZEN")));
    }

    private void registerUser(String username, String email, String password, String fullName) throws Exception {
        RegisterRequest req = new RegisterRequest(username, email, password, fullName);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private String loginUser(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("token").asText();
    }

    private Account fetchFirstAccount(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String accIdStr = json.get("data").get(0).get("id").asText();
        String accNumStr = json.get("data").get(0).get("accountNumber").asText();

        Account acc = new Account();
        acc.setId(UUID.fromString(accIdStr));
        acc.setAccountNumber(accNumStr);
        return acc;
    }
}
