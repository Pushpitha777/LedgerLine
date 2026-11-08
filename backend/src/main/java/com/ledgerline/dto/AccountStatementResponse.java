package com.ledgerline.dto;

import com.ledgerline.entity.Account;
import com.ledgerline.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AccountStatementResponse(
    String accountNumber,
    String accountType,
    String accountHolderName,
    long currentBalancePaise,
    BigDecimal currentBalanceRupees,
    String currency,
    List<StatementEntry> entries,
    OffsetDateTime generatedAt
) {
    public static AccountStatementResponse of(Account account, List<StatementEntry> entries) {
        String holderName = account.getUser() != null ? account.getUser().getFullName() : "Bank System";
        return new AccountStatementResponse(
                account.getAccountNumber(),
                account.getAccountType(),
                holderName,
                account.getBalancePaise(),
                MoneyUtil.toRupees(account.getBalancePaise()),
                account.getCurrency(),
                entries,
                OffsetDateTime.now()
        );
    }
}
