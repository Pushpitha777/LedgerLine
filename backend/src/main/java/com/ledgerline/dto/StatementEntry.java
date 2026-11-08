package com.ledgerline.dto;

import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.entity.LedgerEntryType;
import com.ledgerline.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StatementEntry(
    String transactionReference,
    String transactionType,
    LedgerEntryType entryType,
    long amountPaise,
    BigDecimal amountRupees,
    long balanceAfterPaise,
    BigDecimal balanceAfterRupees,
    String description,
    OffsetDateTime date
) {
    public static StatementEntry of(LedgerEntry entry) {
        return new StatementEntry(
                entry.getTransaction().getReferenceNumber(),
                entry.getTransaction().getTransactionType().name(),
                entry.getEntryType(),
                entry.getAmountPaise(),
                MoneyUtil.toRupees(entry.getAmountPaise()),
                entry.getBalanceAfterPaise(),
                MoneyUtil.toRupees(entry.getBalanceAfterPaise()),
                entry.getTransaction().getDescription(),
                entry.getCreatedAt()
        );
    }
}
