package com.coffeeshop.coffeeshopmanagement.model;

public enum TableStatus {
    EMPTY("Không hoạt động"),
    OCCUPIED("Đang sử dụng");

    private final String displayName;

    TableStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TableStatus fromDb(String value) {
        if (value == null) return EMPTY;
        try {
            return TableStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }
}
