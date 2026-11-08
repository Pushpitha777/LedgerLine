package com.ledgerline.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityInvariantTest {

    @Test
    @DisplayName("Account balance must never be allowed to become negative at domain entity level")
    void testAccountBalanceCannotBeNegative() {
        Account account = new Account("ACC-123", null, "SAVINGS", 1000L);
        assertEquals(1000L, account.getBalancePaise());

        // Valid positive adjustment
        account.setBalancePaise(500L);
        assertEquals(500L, account.getBalancePaise());

        // Zero balance allowed
        account.setBalancePaise(0L);
        assertEquals(0L, account.getBalancePaise());

        // Negative balance must be rejected
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> account.setBalancePaise(-1L));
        assertTrue(ex.getMessage().contains("cannot be negative"));
    }

    @Test
    @DisplayName("Transaction amount must be strictly positive")
    void testTransactionAmountMustBePositive() {
        Transaction tx = new Transaction();
        assertThrows(IllegalArgumentException.class, () -> tx.setAmountPaise(0L));
        assertThrows(IllegalArgumentException.class, () -> tx.setAmountPaise(-500L));

        tx.setAmountPaise(100L);
        assertEquals(100L, tx.getAmountPaise());
    }

    @Test
    @DisplayName("LedgerEntry constructor must reject non-positive amounts")
    void testLedgerEntryAmountValidation() {
        Transaction tx = new Transaction();
        Account acc = new Account();

        assertThrows(IllegalArgumentException.class, () ->
            new LedgerEntry(tx, acc, LedgerEntryType.CREDIT, 0L, 500L)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new LedgerEntry(tx, acc, LedgerEntryType.DEBIT, -100L, 400L)
        );

        LedgerEntry validEntry = new LedgerEntry(tx, acc, LedgerEntryType.CREDIT, 1000L, 1000L);
        assertEquals(1000L, validEntry.getAmountPaise());
        assertEquals(1000L, validEntry.getBalanceAfterPaise());
        assertEquals(LedgerEntryType.CREDIT, validEntry.getEntryType());
    }
}
