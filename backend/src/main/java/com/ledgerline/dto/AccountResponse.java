package com.ledgerline.dto;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    String accountType,
    long balancePaise,
    BigDecimal balanceRupees,
    String currency,
    AccountStatus status,
    OffsetDateTime createdAt
) {
    public static AccountResponse of(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalancePaise(),
                MoneyUtil.toRupees(account.getBalancePaise()),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
