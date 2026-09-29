package com.coffeeshop.coffeeshopmanagement.model;

public enum PaymentMethod {
    CASH,
    CARD;

    public static PaymentMethod fromDb(String value) {
        if (value == null) return null;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
