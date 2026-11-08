package com.ledgerline.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility class to guarantee monetary precision.
 * In LedgerLine, all financial calculations and database storage use integer paise
 * (1 INR = 100 paise) to avoid floating-point rounding errors.
 */
public final class MoneyUtil {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MoneyUtil() {
        // Prevent instantiation
    }

    /**
     * Converts rupees (e.g. 150.50) to paise (15050).
     */
    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) {
            throw new IllegalArgumentException("Rupees amount cannot be null");
        }
        if (rupees.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monetary amount must be strictly positive");
        }
        return rupees.multiply(HUNDRED)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    /**
     * Converts paise (e.g. 15050) to rupees (150.50).
     */
    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise)
                .divide(HUNDRED, 2, RoundingMode.UNNECESSARY);
    }

    /**
     * Formats paise as human-readable INR string (e.g. ₹1,500.50).
     */
    public static String formatInr(long paise) {
        return "₹" + toRupees(paise).toPlainString();
    }
}
