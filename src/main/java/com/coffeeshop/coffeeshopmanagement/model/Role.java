package com.coffeeshop.coffeeshopmanagement.model;

/**
 * System roles. ADMIN has full access (referred to as "Owner" / "Quản trị viên" in the UI),
 * EMPLOYEE can operate the POS and day-to-day screens, CUSTOMER is a legacy value for
 * accounts that came from the removed self-registration screen: they have no dashboard and the
 * login screen just says so. Nothing in the app creates CUSTOMER accounts any more.
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
