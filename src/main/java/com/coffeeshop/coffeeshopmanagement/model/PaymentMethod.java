package com.coffeeshop.coffeeshopmanagement.model;

public enum PaymentMethod {
    CASH,
    CARD,
    QR;

    public static PaymentMethod fromDb(String value) {
        if (value == null) return null;
        try {
            String v = value.trim().toUpperCase();
            if ("CARD".equals(v)) return QR;
            return PaymentMethod.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
