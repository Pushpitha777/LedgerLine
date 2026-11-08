# LedgerLine REST API Specification

All monetary values in requests and responses accept both `amountRupees` (e.g. `150.50`) for client convenience and store/return `amountPaise` (e.g. `15050`) for absolute precision.

---

## 1. Authentication Endpoints

### Register User
- **Method**: `POST /api/auth/register`
- **Request Body**:
  ```json
  {
    "username": "alice",
    "email": "alice@example.com",
    "password": "Password123!",
    "fullName": "Alice Smith"
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "success": true,
    "message": "User registered successfully",
    "data": {
      "id": "uuid",
      "username": "alice",
      "email": "alice@example.com",
      "fullName": "Alice Smith",
      "roles": ["ROLE_ACCOUNT_HOLDER"]
    }
  }
  ```

### Login
- **Method**: `POST /api/auth/login`
- **Request Body**:
  ```json
  {
    "username": "alice",
    "password": "Password123!"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "success": true,
    "token": "eyJhbGciOi...",
    "type": "Bearer",
    "user": {
      "id": "uuid",
      "username": "alice",
      "roles": ["ROLE_ACCOUNT_HOLDER"]
    }
  }
  ```

---

## 2. Account Endpoints

### Create Account
- **Method**: `POST /api/accounts`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
  ```json
  {
    "accountType": "SAVINGS"
  }
  ```

### List My Accounts
- **Method**: `GET /api/accounts`
- **Headers**: `Authorization: Bearer <token>`

### Get Account Details
- **Method**: `GET /api/accounts/{id}`
- **Security Check**: Enforces ownership verification (prevents IDOR). Rejects with 403 if target account does not belong to caller.

---

## 3. Banking Transaction Endpoints (Financial Core)

All mutation endpoints require the `Idempotency-Key` header.

### Deposit
- **Method**: `POST /api/transactions/deposit`
- **Headers**:
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: <unique-uuid>`
- **Request Body**:
  ```json
  {
    "destinationAccountId": "uuid",
    "amountRupees": 1000.00,
    "description": "Initial savings deposit"
  }
  ```

### Withdraw
- **Method**: `POST /api/transactions/withdraw`
- **Headers**:
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: <unique-uuid>`
- **Request Body**:
  ```json
  {
    "sourceAccountId": "uuid",
    "amountRupees": 250.00,
    "description": "ATM withdrawal"
  }
  ```

### Inter-Account Transfer
- **Method**: `POST /api/transactions/transfer`
- **Headers**:
  - `Authorization: Bearer <token>`
  - `Idempotency-Key: <unique-uuid>`
- **Request Body**:
  ```json
  {
    "sourceAccountId": "uuid",
    "destinationAccountNumber": "ACC-987654321",
    "amountRupees": 500.00,
    "description": "Dinner split reimbursement"
  }
  ```

---

## 4. Auditor Endpoints (`ROLE_AUDITOR`)

### Verify Double-Entry Balance Invariant
- **Method**: `GET /api/auditor/ledger-summary`
- **Headers**: `Authorization: Bearer <auditor-token>`
- **Response**:
  ```json
  {
    "totalDebitsPaise": 150000000,
    "totalCreditsPaise": 150000000,
    "differencePaise": 0,
    "booksBalanced": true,
    "totalLedgerEntries": 4820
  }
  ```

### Audit Logs & Security Events
- **Method**: `GET /api/auditor/audit-logs`
- **Headers**: `Authorization: Bearer <auditor-token>`

---

## 5. Admin Endpoints (`ROLE_ADMIN`)

### Account Management (Freeze / Unfreeze)
- **Method**: `PATCH /api/admin/accounts/{id}/status`
- **Headers**: `Authorization: Bearer <admin-token>`
- **Request Body**:
  ```json
  {
    "status": "FROZEN",
    "reason": "Suspicious login activity reported"
  }
  ```
