package com.coffeeshop.coffeeshopmanagement.model;

public enum OrderStatus {
    OPEN,
    PAID,
    CANCELLED;

    public static OrderStatus fromDb(String value) {
        if (value == null) return OPEN;
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
