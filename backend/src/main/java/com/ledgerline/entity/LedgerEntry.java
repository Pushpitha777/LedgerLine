package com.ledgerline.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 10, nullable = false, updatable = false)
    private LedgerEntryType entryType;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(name = "balance_after_paise", nullable = false, updatable = false)
    private long balanceAfterPaise;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public LedgerEntry() {}

    public LedgerEntry(Transaction transaction, Account account, LedgerEntryType entryType,
                       long amountPaise, long balanceAfterPaise) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be strictly positive");
        }
        this.transaction = transaction;
        this.account = account;
        this.entryType = entryType;
        this.amountPaise = amountPaise;
        this.balanceAfterPaise = balanceAfterPaise;
    }

    public UUID getId() {
        return id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public long getBalanceAfterPaise() {
        return balanceAfterPaise;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
