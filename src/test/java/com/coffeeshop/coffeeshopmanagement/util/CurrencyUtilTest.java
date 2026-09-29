package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class CurrencyUtilTest {

    @Test
    public void formatsWithDotThousandsSeparatorAndDongSuffix() {
        assertEquals("12.000đ", CurrencyUtil.format(new BigDecimal("12000")));
        assertEquals("1.500.000đ", CurrencyUtil.format(new BigDecimal("1500000")));
    }

    @Test
    public void noThousandsSeparatorBelowOneThousand() {
        assertEquals("500đ", CurrencyUtil.format(new BigDecimal("500")));
    }

    @Test
    public void zeroAndNullBothRenderAsZero() {
        // A null total (e.g. a field not yet loaded) must render as an honest 0đ, not throw
        // and not silently show a blank label.
        assertEquals("0đ", CurrencyUtil.format(BigDecimal.ZERO));
        assertEquals("0đ", CurrencyUtil.format(null));
    }

    @Test
    public void noDecimalPlacesEvenWithFractionalInput() {
        // Money in this app is whole VND; a stray fractional value from a calculation must
        // still display as a clean integer amount.
        assertEquals("100đ", CurrencyUtil.format(new BigDecimal("99.6")));
    }
}
