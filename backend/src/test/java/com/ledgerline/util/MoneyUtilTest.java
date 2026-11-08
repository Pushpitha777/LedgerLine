package com.ledgerline.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyUtilTest {

    @Test
    @DisplayName("Should convert rupees accurately to integer paise without floating point error")
    void testToPaise() {
        assertEquals(10050L, MoneyUtil.toPaise(new BigDecimal("100.50")));
        assertEquals(1L, MoneyUtil.toPaise(new BigDecimal("0.01")));
        assertEquals(100000000L, MoneyUtil.toPaise(new BigDecimal("1000000.00")));
    }

    @Test
    @DisplayName("Should convert paise to BigDecimal rupees with 2 decimal places")
    void testToRupees() {
        assertEquals(new BigDecimal("100.50"), MoneyUtil.toRupees(10050L));
        assertEquals(new BigDecimal("0.01"), MoneyUtil.toRupees(1L));
        assertEquals(new BigDecimal("0.00"), MoneyUtil.toRupees(0L));
    }

    @Test
    @DisplayName("Should reject null, zero, and negative amounts when converting to paise")
    void testInvalidRupeesThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.toPaise(null));
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.toPaise(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.toPaise(new BigDecimal("-50.00")));
    }

    @Test
    @DisplayName("Should format paise into INR currency representation")
    void testFormatInr() {
        assertEquals("₹150.50", MoneyUtil.formatInr(15050L));
        assertEquals("₹0.00", MoneyUtil.formatInr(0L));
    }
}
