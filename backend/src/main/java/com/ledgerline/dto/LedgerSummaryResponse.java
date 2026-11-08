package com.ledgerline.dto;

import com.ledgerline.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LedgerSummaryResponse(
    long totalDebitsPaise,
    BigDecimal totalDebitsRupees,
    long totalCreditsPaise,
    BigDecimal totalCreditsRupees,
    long differencePaise,
    boolean booksBalanced,
    long totalLedgerEntries,
    OffsetDateTime verifiedAt
) {
    public static LedgerSummaryResponse of(long totalDebitsPaise, long totalCreditsPaise, long count) {
        long difference = Math.abs(totalDebitsPaise - totalCreditsPaise);
        boolean balanced = (difference == 0);

        return new LedgerSummaryResponse(
                totalDebitsPaise,
                MoneyUtil.toRupees(totalDebitsPaise),
                totalCreditsPaise,
                MoneyUtil.toRupees(totalCreditsPaise),
                difference,
                balanced,
                count,
                OffsetDateTime.now()
        );
    }
}
