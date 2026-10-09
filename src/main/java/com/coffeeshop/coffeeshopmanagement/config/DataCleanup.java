package com.coffeeshop.coffeeshopmanagement.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * One-time removal of the leftover example/test data from the shop's database: products "a" and
 * "c", the categories "A" and "Coffee", every customer, and the test accounts "Adoft" and
 * "ancdef" (the seeded "admin" account is never touched).
 *
 * <p>Gated by a persistent flag in {@code app_flags}, exactly like {@link MenuSeeder}, so it runs
 * once per database and never deletes customers or products the shop adds afterwards. The flag is
 * written in the same transaction as the deletes: a failure rolls everything back and the next
 * launch retries.
 *
 * <p>History is kept rather than cascaded away: paid orders stay in the order history (their
 * lines keep the product name, so {@code order_items.product_id} just becomes NULL, and a deleted
 * customer or employee just becomes "Khách lẻ" / "-"), and any product that was filed under a
 * removed category stays on sale with no category instead of being deleted.
 */
final class DataCleanup {

    private static final Logger LOGGER = Logger.getLogger(DataCleanup.class.getName());
    static final String FLAG_NAME = "cleanup_example_data_v1";

    private static final List<String> PRODUCT_NAMES = List.of("a", "c");
    private static final List<String> CATEGORY_NAMES = List.of("a", "coffee");
    private static final List<String> ACCOUNT_NAMES = List.of("adoft", "ancdef");

    private DataCleanup() {
    }

    static void runIfNeeded(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS app_flags (name TEXT PRIMARY KEY)");
        }
        try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, FLAG_NAME);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int products = removeProducts(connection);
            int categories = removeCategories(connection);
            int customers = removeCustomers(connection);
            int accounts = removeAccounts(connection);
            try (PreparedStatement flag = connection.prepareStatement("INSERT INTO app_flags (name) VALUES (?)")) {
                flag.setString(1, FLAG_NAME);
                flag.executeUpdate();
            }
            connection.commit();
            LOGGER.info("Example-data cleanup done: " + products + " products, " + categories
                    + " categories, " + customers + " customers, " + accounts + " accounts removed.");
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static int removeProducts(Connection c) throws SQLException {
        List<Integer> ids = ids(c, "SELECT id FROM products WHERE LOWER(TRIM(name)) IN (" + marks(PRODUCT_NAMES) + ")", PRODUCT_NAMES);
        for (int id : ids) {
            update(c, "UPDATE order_items SET product_id = NULL WHERE product_id = ?", id);
            update(c, "DELETE FROM products WHERE id = ?", id);
        }
        return ids.size();
    }

    private static int removeCategories(Connection c) throws SQLException {
        List<Integer> ids = ids(c, "SELECT id FROM categories WHERE LOWER(TRIM(name)) IN (" + marks(CATEGORY_NAMES) + ")", CATEGORY_NAMES);
        for (int id : ids) {
            update(c, "UPDATE products SET category_id = NULL WHERE category_id = ?", id);
            update(c, "DELETE FROM categories WHERE id = ?", id);
        }
        return ids.size();
    }

    private static int removeCustomers(Connection c) throws SQLException {
        List<Integer> ids = ids(c, "SELECT id FROM customers", List.of());
        try (Statement statement = c.createStatement()) {
            statement.executeUpdate("UPDATE orders SET customer_id = NULL WHERE customer_id IS NOT NULL");
            // Self-registered customer accounts point at a customer row, which is going away.
            statement.executeUpdate("DELETE FROM users WHERE customer_id IS NOT NULL AND LOWER(username) <> 'admin'");
            statement.executeUpdate("DELETE FROM customers");
        }
        return ids.size();
    }

    private static int removeAccounts(Connection c) throws SQLException {
        List<Integer> employeeIds = new ArrayList<>();
        int removed = 0;
        try (PreparedStatement select = c.prepareStatement(
                "SELECT id, employee_id FROM users WHERE LOWER(TRIM(username)) IN (" + marks(ACCOUNT_NAMES) + ")")) {
            bind(select, ACCOUNT_NAMES);
            try (ResultSet rs = select.executeQuery()) {
                List<Integer> userIds = new ArrayList<>();
                while (rs.next()) {
                    userIds.add(rs.getInt(1));
                    int employeeId = rs.getInt(2);
                    if (!rs.wasNull()) {
                        employeeIds.add(employeeId);
                    }
                }
                for (int userId : userIds) {
                    update(c, "DELETE FROM users WHERE id = ?", userId);
                    removed++;
                }
            }
        }
        for (int employeeId : employeeIds) {
            // Keep the row (hidden) when past orders still point at it, so history keeps its name.
            try (PreparedStatement used = c.prepareStatement(
                    "SELECT 1 FROM orders WHERE employee_id = ? LIMIT 1")) {
                used.setInt(1, employeeId);
                try (ResultSet rs = used.executeQuery()) {
                    if (rs.next()) {
                        update(c, "UPDATE employees SET active = 0 WHERE id = ?", employeeId);
                        continue;
                    }
                }
            }
            // Another account may still use this employee record - only delete when none does.
            try (PreparedStatement other = c.prepareStatement(
                    "SELECT 1 FROM users WHERE employee_id = ? LIMIT 1")) {
                other.setInt(1, employeeId);
                try (ResultSet rs = other.executeQuery()) {
                    if (!rs.next()) {
                        update(c, "DELETE FROM employees WHERE id = ?", employeeId);
                    }
                }
            }
        }
        return removed;
    }

    private static String marks(List<String> values) {
        return String.join(",", values.stream().map(v -> "?").toList());
    }

    private static void bind(PreparedStatement statement, List<String> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setString(i + 1, values.get(i));
        }
    }

    private static List<Integer> ids(Connection c, String sql, List<String> params) throws SQLException {
        List<Integer> result = new ArrayList<>();
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt(1));
                }
            }
        }
        return result;
    }

    private static void update(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }
}
