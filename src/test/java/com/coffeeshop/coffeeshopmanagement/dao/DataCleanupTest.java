package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DataCleanup;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * The cleanup is one-time and destructive, so it gets its own tests: it must take a backup first,
 * must not strand products of a category it would otherwise remove, and must leave later data alone.
 */
public class DataCleanupTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static long count(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long backups() throws Exception {
        Path dir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "backups");
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().startsWith("before-cleanup-")).count();
        }
    }

    @Test
    public void backsUpFirstThenRemovesExampleDataButKeepsACategoryThatStillHoldsProducts() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM app_flags WHERE name = '" + DataCleanup.FLAG_NAME + "'");
                st.executeUpdate("INSERT INTO categories (name, active) VALUES ('A', 1)");
                st.executeUpdate("INSERT INTO categories (name, active) VALUES ('Coffee', 1)");
            }
            int coffeeId;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM categories WHERE name = 'Coffee'")) {
                rs.next();
                coffeeId = rs.getInt(1);
            }
            String realProduct = "real-product-" + UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (name, category_id, price, stock, active) VALUES (?, ?, 1000, 5, 1)")) {
                ps.setString(1, realProduct);
                ps.setInt(2, coffeeId);
                ps.executeUpdate();
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO customers (full_name, phone, loyalty_points, created_at) VALUES ('Example', '0900000001', 0, '2026-01-01T00:00:00')");
            }
            long before = backups();

            DataCleanup.runIfNeeded(c);

            assertEquals("a backup is written before anything is deleted", before + 1, backups());
            assertEquals("customers are removed", 0, count(c, "SELECT COUNT(*) FROM customers"));
            assertEquals("category A held nothing: removed", 0, count(c, "SELECT COUNT(*) FROM categories WHERE name = 'A'"));
            assertEquals("category Coffee still holds a product: kept", 1, count(c, "SELECT COUNT(*) FROM categories WHERE name = 'Coffee'"));
            assertEquals("the product keeps its category", 1, count(c,
                    "SELECT COUNT(*) FROM products WHERE category_id = " + coffeeId));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM app_flags WHERE name = '" + DataCleanup.FLAG_NAME + "'"));

            // second run: flag is set, so it must not delete customers added later nor back up again
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO customers (full_name, phone, loyalty_points, created_at) VALUES ('Real', '0900000002', 0, '2026-01-01T00:00:00')");
            }
            DataCleanup.runIfNeeded(c);
            assertEquals(1, count(c, "SELECT COUNT(*) FROM customers"));
            assertEquals(before + 1, backups());
        }
    }
}
