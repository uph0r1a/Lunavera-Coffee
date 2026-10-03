package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between the app's Java-level money type (BigDecimal, used throughout models,
 * controllers, and calculations) and how it's actually stored in SQLite since the schema v1
 * migration: a plain INTEGER number of dong. Vietnamese dong has no fractional subunit in real
 * use, so this is lossless for every legitimate amount in the app - the rounding here is a
 * safety net against stray floating-point noise (the pre-v1 schema stored REAL), never because
 * fractional dong are expected or accepted going forward.
 */
public final class Money {

    private Money() {
    }

    /** For a NOT NULL money column. */
    public static long toDong(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** For a nullable money column (e.g. Product.cost, Employee.salary). */
    public static Long toDongOrNull(BigDecimal value) {
        return value == null ? null : toDong(value);
    }

    public static BigDecimal fromDong(long value) {
        return BigDecimal.valueOf(value);
    }

    /** Reads a nullable INTEGER money column - call right after rs.getLong(column). */
    public static BigDecimal fromDongOrNull(long value, boolean wasNull) {
        return wasNull ? null : fromDong(value);
    }
}
