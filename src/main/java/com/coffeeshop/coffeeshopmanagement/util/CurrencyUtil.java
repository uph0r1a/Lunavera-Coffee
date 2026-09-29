package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats money the way the existing UI labels do: "12.000đ" (dot thousands separator, no decimals). */
public final class CurrencyUtil {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private CurrencyUtil() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return FORMAT.format(amount) + "đ";
    }
}
