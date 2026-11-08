-- ==============================================================================
-- LedgerLine Core Banking Engine - Database Migration V1
-- Comprehensive Schema with Relational Invariants and Double-Entry Constraints
-- ==============================================================================

-- Enable UUID generator extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. ROLES TABLE
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- 2. USERS TABLE
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. USER ROLES JUNCTION TABLE
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 4. ACCOUNTS TABLE
-- Note: balance_paise is stored in paise (1 INR = 100 paise)
-- Hard constraint: balance_paise CAN NEVER be negative
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(34) NOT NULL UNIQUE,
    user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    account_type VARCHAR(30) NOT NULL,
    balance_paise BIGINT NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_account_balance_non_negative CHECK (balance_paise >= 0)
);

-- 5. TRANSACTIONS TABLE
-- High-level representation of financial movements
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number VARCHAR(64) NOT NULL UNIQUE,
    transaction_type VARCHAR(30) NOT NULL,
    source_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    destination_account_id UUID REFERENCES accounts(id) ON DELETE RESTRICT,
    amount_paise BIGINT NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255),
    idempotency_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tx_amount_positive CHECK (amount_paise > 0)
);

-- 6. LEDGER ENTRIES TABLE (DOUBLE-ENTRY BOOKKEEPING)
-- Every financial transaction creates matching DEBIT and CREDIT records.
-- Total Debits MUST equal Total Credits.
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    entry_type VARCHAR(10) NOT NULL,
    amount_paise BIGINT NOT NULL,
    balance_after_paise BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount_paise > 0)
);

-- Immutable Ledger Trigger: Strictly forbids UPDATE or DELETE on ledger_entries
CREATE OR REPLACE FUNCTION prevent_ledger_tampering()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are strictly immutable. Modifications and deletions are prohibited.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_immutable_ledger
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW EXECUTE FUNCTION prevent_ledger_tampering();

-- 7. IDEMPOTENCY KEYS TABLE
-- Prevents duplicate payment requests and concurrent double-submits
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_path VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status_code INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 8. SECURITY AUDIT LOGS TABLE
-- Records authentication, authorization failures, and administrative actions
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(50) NOT NULL,
    principal VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    details TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================================================
-- INDEXES FOR PERFORMANCE AND DATA RETRIEVAL
-- ==============================================================================
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_transactions_source ON transactions(source_account_id);
CREATE INDEX idx_transactions_dest ON transactions(destination_account_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_ledger_tx_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);
CREATE INDEX idx_idempotency_key_lookup ON idempotency_keys(idempotency_key);
CREATE INDEX idx_audit_logs_event ON audit_logs(event_type, created_at);
