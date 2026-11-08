package com.ledgerline.dto;

import com.ledgerline.entity.Transaction;
import com.ledgerline.entity.TransactionStatus;
import com.ledgerline.entity.TransactionType;
import com.ledgerline.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    String referenceNumber,
    TransactionType transactionType,
    String sourceAccountNumber,
    String destinationAccountNumber,
    long amountPaise,
    BigDecimal amountRupees,
    String description,
    TransactionStatus status,
    long accountBalanceAfterPaise,
    BigDecimal accountBalanceAfterRupees,
    OffsetDateTime createdAt
) {
    public static TransactionResponse of(Transaction tx, long balanceAfterPaise) {
        return new TransactionResponse(
                tx.getId(),
                tx.getReferenceNumber(),
                tx.getTransactionType(),
                tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : "CASH_VAULT",
                tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : "CASH_VAULT",
                tx.getAmountPaise(),
                MoneyUtil.toRupees(tx.getAmountPaise()),
                tx.getDescription(),
                tx.getStatus(),
                balanceAfterPaise,
                MoneyUtil.toRupees(balanceAfterPaise),
                tx.getCreatedAt()
        );
    }
}
