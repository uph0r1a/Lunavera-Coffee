package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;

/**
 * Holds the currently logged-in account for the lifetime of the running application.
 * A single desktop process serves one signed-in user at a time, so a simple static holder
 * (rather than passing the user through every controller constructor) is the pragmatic
 * choice here; controllers read it after navigation instead of each other's fields.
 */
public final class Session {

    private static User currentUser;
    private static Employee currentEmployee; // populated for ADMIN/EMPLOYEE accounts

    private Session() {
    }

    public static void start(User user, Employee employee) {
        currentUser = user;
        currentEmployee = employee;
    }

    public static void clear() {
        currentUser = null;
        currentEmployee = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static Employee getCurrentEmployee() {
        return currentEmployee;
    }

    public static Role getCurrentRole() {
        return currentUser != null ? currentUser.getRole() : null;
    }

    public static boolean isAdmin() {
        return getCurrentRole() == Role.ADMIN;
    }

    public static String getDisplayName() {
        if (currentEmployee != null && currentEmployee.getFullName() != null) {
            return currentEmployee.getFullName();
        }
        return currentUser != null ? currentUser.getUsername() : "";
    }
}
