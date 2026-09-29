package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One place that decides how many loyalty points a paid order earns, so the rule isn't
 * scattered across DAOs/controllers. Currently: 1 point per full 10.000đ of the final total.
 * Tier thresholds (100 = Bạc, 500 = Vàng) live in CustomerController.tierFor().
 */
public final class LoyaltyPolicy {

    private static final BigDecimal VND_PER_POINT = new BigDecimal("10000");

    private LoyaltyPolicy() {
    }

    public static int pointsFor(BigDecimal orderTotal) {
        if (orderTotal == null || orderTotal.signum() <= 0) {
            return 0;
        }
        return orderTotal.divide(VND_PER_POINT, 0, RoundingMode.DOWN).intValue();
    }
}
