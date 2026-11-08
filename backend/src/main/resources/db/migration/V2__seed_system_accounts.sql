-- ==============================================================================
-- LedgerLine Core Banking Engine - Database Migration V2
-- Seed Roles and Bank System Vault Account for Double-Entry Balancing
-- ==============================================================================

-- 1. SEED DEFAULT ROLES
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_ACCOUNT_HOLDER'),
    ('ROLE_AUDITOR')
ON CONFLICT (name) DO NOTHING;

-- 2. SEED SYSTEM VAULT ACCOUNT
-- For external Deposits and Withdrawals to maintain strict Double-Entry balancing:
-- - Deposit:    Debit SYSTEM_VAULT_INR, Credit Customer Account
-- - Withdrawal: Debit Customer Account, Credit SYSTEM_VAULT_INR
-- - Transfer:   Debit Source Customer,  Credit Destination Customer
INSERT INTO accounts (
    id,
    account_number,
    user_id,
    account_type,
    balance_paise,
    currency,
    status
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SYSTEM_VAULT_INR',
    NULL,
    'SYSTEM_VAULT',
    100000000000, -- ₹10,000,000.00 Initial Vault Float Reserve
    'INR',
    'ACTIVE'
) ON CONFLICT (account_number) DO NOTHING;
