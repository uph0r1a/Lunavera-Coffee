package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * The starter menu on a private database file. "A restart" is a second, separate connection to the
 * same file: the seeder's own persistent flag (not DatabaseConfig's once-per-JVM flag) is what must
 * stop a deleted item from coming back.
 */
public class MenuSeederTest {

    @BeforeClass
    public static void isolateHome() {
        TestDatabaseSupport.ensureReady(); // sends the seeded images to a temp home, never the real one
    }

    private static String url(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath();
    }

    private static void createSchema(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "description TEXT, active INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category_id INTEGER, "
                    + "price INTEGER NOT NULL, cost INTEGER, stock INTEGER NOT NULL DEFAULT 0, description TEXT, "
                    + "image_path TEXT, active INTEGER NOT NULL DEFAULT 1, FOREIGN KEY (category_id) REFERENCES categories(id))");
        }
    }

    private static long count(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void seedsTheStarterMenuOnceAndADeletedItemStaysDeletedAfterARestart() throws Exception {
        Path file = Files.createTempFile("lunavera-menu-seed", ".db");
        try (Connection first = DriverManager.getConnection(url(file))) {
            createSchema(first);
            MenuSeeder.seedIfNeeded(first);

            assertEquals(33, count(first, "SELECT COUNT(*) FROM products"));
            assertEquals(4, count(first, "SELECT COUNT(*) FROM categories WHERE name IN "
                    + "('Cà phê','Bánh ngọt','Nước ép & Sinh tố','Trà')"));
            assertEquals("every product has an image", 0, count(first, "SELECT COUNT(*) FROM products WHERE image_path IS NULL"));
            assertEquals("every product has a positive price", 0, count(first, "SELECT COUNT(*) FROM products WHERE price <= 0"));
            assertEquals(1, count(first, "SELECT COUNT(*) FROM products WHERE name = 'Cafe đen'"));

            try (Statement st = first.createStatement()) {
                st.executeUpdate("DELETE FROM products WHERE name = 'Cafe đen'");
            }
        }
        try (Connection restarted = DriverManager.getConnection(url(file))) {
            MenuSeeder.seedIfNeeded(restarted);
            assertEquals("not back to 33", 32, count(restarted, "SELECT COUNT(*) FROM products"));
            assertEquals(0, count(restarted, "SELECT COUNT(*) FROM products WHERE name = 'Cafe đen'"));
        }
    }
}
