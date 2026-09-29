package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class LoyaltyPolicyTest {

    @Test
    public void oneEarnedPointPerFullTenThousand() {
        assertEquals(1, LoyaltyPolicy.pointsFor(new BigDecimal("10000")));
        assertEquals(5, LoyaltyPolicy.pointsFor(new BigDecimal("50000")));
    }

    @Test
    public void roundsDownRatherThanUp() {
        // 19.999đ must NOT earn a 2nd point - rounding the wrong way here would be giving
        // away loyalty points the order didn't actually qualify for.
        assertEquals(1, LoyaltyPolicy.pointsFor(new BigDecimal("19999")));
    }

    @Test
    public void belowThresholdEarnsNothing() {
        assertEquals(0, LoyaltyPolicy.pointsFor(new BigDecimal("9999")));
        assertEquals(0, LoyaltyPolicy.pointsFor(BigDecimal.ZERO));
    }

    @Test
    public void negativeOrNullNeverEarnsPoints() {
        // A cancelled/refunded order total, or a missing total, must not produce negative or
        // fabricated points.
        assertEquals(0, LoyaltyPolicy.pointsFor(new BigDecimal("-5000")));
        assertEquals(0, LoyaltyPolicy.pointsFor(null));
    }
}
