package com.grepr.takehome.auction.util;

import java.math.BigDecimal;

/**
 * Small helpers for {@link BigDecimal} validation.
 *
 * <p>These helpers are null-safe to simplify request validation code.
 */
public final class DecimalUtils {
    private DecimalUtils() {
    }


    /**
     * @return true if value is null or value &lt;= 0
     */
    public static boolean isNonPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    /**
     * @return true if value is null or value &lt; 0
     */
    public static boolean isNegative(BigDecimal value) {
        return value == null || value.signum() < 0;
    }
}

