# LedgerLine - Security Threat Model & Vulnerability Analysis (THREATS.md)

This document formalizes the threat landscape, attack vectors, defensive architectures, and automated test validations implemented in the LedgerLine banking application for the **Snovasys Fresher Hiring Challenge**.

---

## 1. Threat Matrix (STRIDE & OWASP Mapping)

| Threat ID | Threat Category (STRIDE) | Attack Vector & Scenario | Potential Impact | Defensive Mitigation Architecture | Automated Test Validation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **THR-01** | **Denial of Service / Tampering** | **Simultaneous Withdrawals (Race-to-Negative)**: Attacker sends 10 concurrent withdrawal requests of ₹1,000 each against an account with only ₹1,000 balance. | Overdraft / negative account balance, unbacked money creation, loss of bank solvency. | `PESSIMISTIC_WRITE` row locks (`SELECT ... FOR UPDATE`) in `BankingLedgerService.java` + DB constraint `CHECK (balance_paise >= 0)` in `V1__init_schema.sql`. | `ConcurrencyStressTest.testConcurrentWithdrawalsPreventOverdraftAndEnforceZeroSum()` |
| **THR-02** | **Denial of Service** | **Reciprocal Transfer Deadlock**: Client 1 transfers Account A -> B while Client 2 transfers B -> A at the exact same millisecond. | Thread pool starvation, HTTP 500 transaction timeouts, database locks hanging indefinitely. | **Deterministic Lock Ordering**: Row locks are always acquired in ascending lexicographical order of UUID: `min(idA, idB)` followed by `max(idA, idB)`. | `ConcurrencyStressTest.testConcurrentReciprocalTransfersNeverDeadlock()` |
| **THR-03** | **Repudiation / Tampering** | **Duplicate Request Replay**: Network timeout or malicious client resubmits an identical transfer or deposit request multiple times. | Double debit/credit, customer billed multiple times for single intent. | Mandatory `Idempotency-Key` header with SHA-256 payload hashing in `IdempotencyService.java`. Cached responses replayed without re-executing business logic. | `ConcurrencyStressTest.testConcurrentIdenticalRequestsWithSameIdempotencyKey()`, `IdempotencyServiceTest.testDuplicateRequestReplaysCachedResponse()` |
| **THR-04** | **Tampering** | **Floating-Point Rounding Drift**: Repeated transactions using standard IEEE 754 `float` or `double` accumulate fractional cent rounding errors over time. | Discrepancy between ledger debits and credits, accounting imbalance, regulatory non-compliance. | Pure integer **paise** (`BIGINT` / Java `Long`). ₹1.00 = 100 paise. Fractional inputs reject with `ArithmeticException` via `MoneyUtil.java`. | `MoneyUtilTest.testToPaiseExactConversion()`, `MoneyUtilTest.testInvalidFractionalPaiseThrowsException()` |
| **THR-05** | **Tampering** | **Direct Database Ledger Tampering**: Malicious insider or compromised database administrator attempts an `UPDATE` or `DELETE` on financial records. | Falsification of bank records, unrecorded embezzlement, audit trail destruction. | PostgreSQL trigger `prevent_ledger_tampering()` raising `EXCEPTIONS` on any SQL `UPDATE` or `DELETE` on `ledger_entries`. Complete immutability. | Flyway migration `V1__init_schema.sql` trigger definition and PostgreSQL execution guarantees. |
| **THR-06** | **Information Disclosure / Tampering** | **Insecure Direct Object Reference (IDOR)**: User A specifies User B's `accountId` in GET/POST requests to view statements or execute unauthorized withdrawals. | Confidential financial data leakage, unauthorized drain of third-party funds (critical rejection condition). | Server-side tenant isolation: `validateAccountOwnership()` verifies `account.getUser().getId().equals(callingUserId)` before any query or mutation proceeds. | `TransactionControllerIntegrationTest.testIdorAccountAccessBlocked()`, `BankingLedgerServiceTest.testIdorWithdrawBlocked()` |
| **THR-07** | **Elevation of Privilege** | **Role Escalation**: Standard account holder invokes administrative freeze (`PATCH /api/admin/accounts/{id}/status`) or auditor summary endpoints. | Unauthorized freeze of competitor accounts, corporate espionage. | Method-level authorization: `@PreAuthorize("hasRole('ADMIN')")` and `@PreAuthorize("hasRole('AUDITOR')")` enforced by Spring Security filter chain. | `SecurityAuthorizationTest.testAccountHolderCannotAccessAdminEndpoints()`, `SecurityAuthorizationTest.testAccountHolderCannotAccessAuditorEndpoints()` |
| **THR-08** | **Spoofing** | **JWT Forgery & Tampering**: Attacker tampers with JWT header or payload, altering `sub` or `roles`, or presents an expired/unsigned token. | Complete account takeover, identity spoofing. | JJWT 0.12.6 with HMAC-SHA256 cryptographically verified signatures using a 256-bit secret key, strict expiration checks, and stateless session enforcement. | `SecurityAuthorizationTest.testSecuredEndpointWithoutJwtReturns401()`, `AuthServiceTest.testLoginSuccess()` |
| **THR-09** | **Repudiation** | **Unrecorded Financial Movements**: Bank system executes balance changes without paired ledger bookkeeping records. | Non-verifiable balances, untraceable fund movement. | Atomic `@Transactional` execution pairing each transaction with dual Debit/Credit `LedgerEntry` records against `SYSTEM_VAULT_INR` or recipient accounts. | `AuditorInvariantTest.testSystemWideDoubleEntryZeroSumInvariantAfterSeriesOfTransactions()` |
| **THR-10** | **Injection** | **SQL Injection via search or transaction filters**: Malicious payloads in query parameters or request bodies. | Database exfiltration, credential theft. | Hibernate/Spring Data JPA parameterized queries and strict strongly-typed DTO validation (`@Valid`, `@NotNull`, `@Pattern`, `@Positive`). | `TransactionControllerIntegrationTest`, `AuthControllerTest` |

---

## 2. Deep-Dive Security Architectural Invariants

### 2.1 The Double-Entry Clearing Vault Model
To satisfy the strict condition that **the books always balance** ($\sum \text{Debits} \equiv \sum \text{Credits}$) even for deposits and withdrawals:
- A special system account `SYSTEM_VAULT_INR` is seeded during database migration `V2`.
- **Customer Deposit (₹1,000)**:
  - `DEBIT`: `SYSTEM_VAULT_INR` (+₹1,000 in bank vault assets)
  - `CREDIT`: Customer Account (+₹1,000 customer deposit liability)
  - Balance Impact: Both entries = 100,000 paise. $\sum \text{Debits} - \sum \text{Credits} = 0$.
- **Customer Withdrawal (₹500)**:
  - `DEBIT`: Customer Account (-₹500 customer deposit liability)
  - `CREDIT`: `SYSTEM_VAULT_INR` (-₹500 in bank vault assets)
  - Balance Impact: Both entries = 50,000 paise. $\sum \text{Debits} - \sum \text{Credits} = 0$.
- **Customer Transfer (₹200 from Alice to Bob)**:
  - `DEBIT`: Alice Account (-₹200)
  - `CREDIT`: Bob Account (+₹200)
  - Balance Impact: Both entries = 20,000 paise. $\sum \text{Debits} - \sum \text{Credits} = 0$.

### 2.2 Deterministic Concurrency & Deadlock Neutralization
In high-frequency banking systems, reciprocal transfers ($A \rightarrow B$ and $B \rightarrow A$) executed concurrently across two database connections trigger circular wait conditions (deadlocks) if locks are acquired in order of caller intention.
- **LedgerLine Solution**:
  ```java
  UUID firstId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
  UUID secondId = sourceId.compareTo(destId) < 0 ? destId : sourceId;

  // Always acquire row-level locks in identical ascending order:
  accountRepository.findByIdForUpdate(firstId);
  accountRepository.findByIdForUpdate(secondId);
  ```
- **Automated Verification**: `ConcurrencyStressTest.testConcurrentReciprocalTransfersNeverDeadlock()` launches 20 concurrent threads alternating $A \rightarrow B$ and $B \rightarrow A$ with zero deadlocks and zero transaction rollbacks.

### 2.3 Idempotency State Machine
1. Request arrives with `Idempotency-Key: <UUID>`.
2. Compute SHA-256 hash of normalized request body (`amount`, `accounts`, `description`).
3. Check `idempotency_keys` table:
   - **Key found & Hash matches**: Return cached HTTP response immediately without re-executing financial logic.
   - **Key found & Hash differs**: Throw `PayloadMismatchException` (HTTP 409 Conflict) preventing key hijacking.
   - **Key not found**: Acquire lock, execute transaction within `@Transactional` boundary, save result into `idempotency_keys`, return response.

---

## 3. Auditor Role & Live Zero-Sum Verification

The Auditor role (`ROLE_AUDITOR`) provides continuous compliance verification via `/api/auditor/ledger-summary`:
$$\Delta_{\text{system}} = \sum_{e \in \text{LedgerEntries}, \text{type}=\text{DEBIT}} e.\text{amountPaise} - \sum_{e \in \text{LedgerEntries}, \text{type}=\text{CREDIT}} e.\text{amountPaise}$$

If $\Delta_{\text{system}} \ne 0$, the system flags an immediate critical audit violation. Under all 34 automated unit, integration, and stress tests, $\Delta_{\text{system}} \equiv 0\text{ paise}$.
