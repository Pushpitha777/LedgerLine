# LedgerLine Database Documentation

## 1. Schema Overview

LedgerLine uses PostgreSQL 18 with Flyway version-controlled migrations (`db/migration/`). The schema strictly separates customer identity, bank accounts, financial movements, and immutable ledger entries.

```
+-----------+       +------------+       +-------------------+
|   USERS   | <---> |  ACCOUNTS  | <---> |  LEDGER_ENTRIES   | (Immutable)
+-----------+       +------------+       +-------------------+
                          ^                        ^
                          |                        |
                          +--- [TRANSACTIONS] -----+
```

---

## 2. Table Specifications

### `users`
Stores user profile information, authentication credentials, and account activation state.
- `id` (UUID, Primary Key)
- `username` (VARCHAR(50), Unique, Not Null)
- `email` (VARCHAR(100), Unique, Not Null)
- `password_hash` (VARCHAR(255), Not Null) - BCrypt hash (strength 12)
- `full_name` (VARCHAR(100), Not Null)
- `status` (VARCHAR(20), Default 'ACTIVE')
- `created_at`, `updated_at` (TIMESTAMPTZ)

### `roles` & `user_roles`
Implements normalized Role-Based Access Control (RBAC).
- `roles`: `id` (SERIAL PK), `name` (VARCHAR(50) UNIQUE) -> `ROLE_ADMIN`, `ROLE_ACCOUNT_HOLDER`, `ROLE_AUDITOR`
- `user_roles`: `user_id` (FK), `role_id` (FK) composite PK.

### `accounts`
Stores bank accounts. Supports customer savings/checking accounts and bank system vaults.
- `id` (UUID, Primary Key)
- `account_number` (VARCHAR(34), Unique, Not Null)
- `user_id` (UUID, Nullable FK to `users`) - System vault accounts have `user_id = NULL`.
- `account_type` (VARCHAR(30), Not Null) - e.g. `SAVINGS`, `SYSTEM_VAULT`.
- `balance_paise` (BIGINT, Not Null, Default 0) - Stored in integer paise (1 INR = 100 paise).
- `currency` (VARCHAR(3), Default 'INR')
- `status` (VARCHAR(20), Default 'ACTIVE') - `ACTIVE`, `FROZEN`, `CLOSED`.
- `version` (BIGINT, Default 0) - Optimistic locking version.
- **Critical Constraint**: `CONSTRAINT chk_account_balance_non_negative CHECK (balance_paise >= 0)`

### `transactions`
Represents intent and lifecycle of fund movements.
- `id` (UUID, Primary Key)
- `reference_number` (VARCHAR(64), Unique, Not Null) - Public transaction ID (e.g., `TXN-...`)
- `transaction_type` (VARCHAR(30), Not Null) - `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`.
- `source_account_id` (UUID, FK `accounts`, Nullable for external deposit).
- `destination_account_id` (UUID, FK `accounts`, Nullable for external withdrawal).
- `amount_paise` (BIGINT, Not Null) - `CHECK (amount_paise > 0)`.
- `description` (VARCHAR(255))
- `status` (VARCHAR(20), Not Null) - `PENDING`, `COMPLETED`, `FAILED`.
- `failure_reason` (VARCHAR(255))
- `idempotency_key` (VARCHAR(128), Unique) - Prevents double execution.
- `created_at` (TIMESTAMPTZ)

### `ledger_entries`
The source of financial truth. Strictly implements Double-Entry Bookkeeping.
- `id` (UUID, Primary Key)
- `transaction_id` (UUID, FK `transactions`, Not Null)
- `account_id` (UUID, FK `accounts`, Not Null)
- `entry_type` (VARCHAR(10), Not Null) - `DEBIT` or `CREDIT`.
- `amount_paise` (BIGINT, Not Null) - `CHECK (amount_paise > 0)`.
- `balance_after_paise` (BIGINT, Not Null) - Balance snapshot immediately following this entry.
- `created_at` (TIMESTAMPTZ)
- **Immutability Enforcement**: Trigger `trg_immutable_ledger` blocks any SQL `UPDATE` or `DELETE`.

### `idempotency_keys`
Stores state-changing request tokens to guarantee strict idempotency (never pay twice).
- `id` (UUID, Primary Key)
- `idempotency_key` (VARCHAR(128), Unique, Not Null) - Client supplied UUIDv4 token
- `user_id` (UUID, FK `users`, Not Null)
- `request_path` (VARCHAR(255), Not Null)
- `request_hash` (VARCHAR(64), Not Null) - SHA-256 of request payload
- `status_code` (INT, Not Null) - HTTP status code (200, 201, 400, etc.)
- `response_body` (TEXT, Not Null) - Cached JSON response body
- `created_at`, `expires_at` (TIMESTAMPTZ)

### `audit_logs`
Records security and authorization events for administrative and auditor review.
- `id` (UUID, Primary Key)
- `event_type` (VARCHAR(50), Not Null) - e.g. `LOGIN_SUCCESS`, `LOGIN_FAILED`, `TRANSFER_EXECUTED`, `IDOR_ATTEMPT_BLOCKED`.
- `principal` (VARCHAR(100), Not Null) - User or caller identity.
- `ip_address` (VARCHAR(45))
- `details` (TEXT)
- `status` (VARCHAR(20), Not Null) - `SUCCESS`, `FAILURE`.
- `created_at` (TIMESTAMPTZ)

---

## 3. Double-Entry Accounting Balancing Rules

1. **Deposit (₹1,000 to Alice)**:
   - Debit: `SYSTEM_VAULT_INR` (+₹1,000 cash in vault)
   - Credit: `Alice's Account` (+₹1,000 customer deposit liability)
   - Total Debits (100,000 paise) == Total Credits (100,000 paise).
2. **Withdrawal (₹500 from Alice)**:
   - Debit: `Alice's Account` (-₹500 customer deposit liability)
   - Credit: `SYSTEM_VAULT_INR` (-₹500 cash from vault)
   - Total Debits (50,000 paise) == Total Credits (50,000 paise).
3. **Internal Transfer (₹200 from Alice to Bob)**:
   - Debit: `Alice's Account` (-₹200)
   - Credit: `Bob's Account` (+₹200)
   - Total Debits (20,000 paise) == Total Credits (20,000 paise).
