package com.coffeeshop.coffeeshopmanagement.model;

/**
 * System roles. ADMIN has full access (referred to as "Owner" / "Quản trị viên" in the UI),
 * EMPLOYEE can operate the POS and day-to-day screens, CUSTOMER is a self-registered
 * account (created from the public registration screen) with no dashboard access yet.
 */
public enum Role {
    ADMIN,
    EMPLOYEE,
    CUSTOMER;

    public static Role fromDb(String value) {
        if (value == null) return EMPLOYEE;
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMPLOYEE;
        }
    }
}
