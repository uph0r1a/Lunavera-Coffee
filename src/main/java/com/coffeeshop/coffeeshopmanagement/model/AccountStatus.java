package com.coffeeshop.coffeeshopmanagement.model;

public enum AccountStatus {
    ACTIVE,
    LOCKED;

    public static AccountStatus fromDb(String value) {
        if (value == null) return ACTIVE;
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ACTIVE;
        }
    }
}
