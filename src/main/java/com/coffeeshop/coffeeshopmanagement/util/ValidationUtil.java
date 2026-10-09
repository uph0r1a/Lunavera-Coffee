package com.coffeeshop.coffeeshopmanagement.util;

import java.util.regex.Pattern;

public final class ValidationUtil {

    // Vietnamese-style mobile numbers: optional +84 / 0 prefix, then 9-10 digits.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?84|0)\\d{9,10}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]{4,32}$");

    private ValidationUtil() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * One canonical form for a Vietnamese phone number, so the same person is never stored twice:
     * spaces, dots and dashes are dropped and a leading {@code +84} or {@code 84} becomes {@code 0}
     * ({@code +84 901 111 222} and {@code 0901111222} are the same customer). Anything that is not
     * a phone number is returned trimmed and otherwise unchanged; null stays null.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String compact = phone.replaceAll("[\\s.\\-()]", "");
        if (!PHONE_PATTERN.matcher(compact).matches()) {
            return phone.trim();
        }
        if (compact.startsWith("+84")) {
            return "0" + compact.substring(3);
        }
        if (compact.startsWith("84")) {
            return "0" + compact.substring(2);
        }
        return compact;
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /** At least 6 characters, with at least one letter and one digit. */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
