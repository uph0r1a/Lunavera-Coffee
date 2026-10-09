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
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * The v0 -> v1 money migration (REAL -> INTEGER dong), run on a private database built to look
 * exactly like one an older version of the app left behind (a fresh database never takes this path).
 */
public class MoneyMigrationTest {

    @BeforeClass
    public static void isolateHome() {
        TestDatabaseSupport.ensureReady(); // the pre-migration backup goes to a temp home
    }

    private static void buildOldDatabase(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL, "
                    + "phone TEXT, email TEXT, address TEXT, position TEXT, salary REAL, hire_date TEXT, active INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL, "
                    + "phone TEXT, email TEXT, loyalty_points INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)");
            st.execute("CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE', "
                    + "employee_id INTEGER, customer_id INTEGER, created_at TEXT NOT NULL, "
                    + "FOREIGN KEY (employee_id) REFERENCES employees(id), FOREIGN KEY (customer_id) REFERENCES customers(id))");
            st.execute("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "description TEXT, active INTEGER NOT NULL DEFAULT 1)");
            st.execute("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category_id INTEGER, "
                    + "price REAL NOT NULL, cost REAL, stock INTEGER NOT NULL DEFAULT 0, description TEXT, image_path TEXT, "
                    + "active INTEGER NOT NULL DEFAULT 1, FOREIGN KEY (category_id) REFERENCES categories(id))");
            st.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, order_date TEXT NOT NULL, employee_id INTEGER, "
                    + "customer_id INTEGER, status TEXT NOT NULL DEFAULT 'OPEN', subtotal REAL NOT NULL DEFAULT 0, "
                    + "discount REAL NOT NULL DEFAULT 0, total REAL NOT NULL DEFAULT 0, payment_method TEXT, paid_at TEXT, "
                    + "FOREIGN KEY (employee_id) REFERENCES employees(id), FOREIGN KEY (customer_id) REFERENCES customers(id))");
            st.execute("CREATE TABLE order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, product_id INTEGER, "
                    + "product_name TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price REAL NOT NULL, line_total REAL NOT NULL, "
                    + "FOREIGN KEY (order_id) REFERENCES orders(id), FOREIGN KEY (product_id) REFERENCES products(id))");

            String now = LocalDateTime.now().toString();
            st.execute("INSERT INTO categories (name, active) VALUES ('Cà phê', 1)");
            // 25000.00000000004 is the floating-point noise REAL arithmetic can leave: the reason ROUND() is in the migration.
            st.execute("INSERT INTO products (name, category_id, price, cost, stock, active) VALUES ('Old Coffee', 1, 25000.00000000004, NULL, 10, 1)");
            st.execute("INSERT INTO employees (full_name, salary, active) VALUES ('Old Employee', 15000000.0, 1)");
            st.execute("INSERT INTO customers (full_name, loyalty_points, created_at) VALUES ('Old Customer', 5, '" + now + "')");
            st.execute("INSERT INTO orders (order_date, employee_id, customer_id, status, subtotal, discount, total, payment_method, paid_at) "
                    + "VALUES ('" + now + "', 1, 1, 'PAID', 50000.0, 5000.0, 45000.0, 'CASH', '" + now + "')");
            st.execute("INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total) "
                    + "VALUES (1, 1, 'Old Coffee', 2, 25000.0, 50000.0)");
        }
    }

    private static long one(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void migratesAnOldDatabaseToIntegerDongWithoutLosingAnything() throws Exception {
        Path file = Files.createTempFile("lunavera-old-db", ".db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            buildOldDatabase(c);
            assertEquals(0, one(c, "PRAGMA user_version"));

            MoneyMigration.runIfNeeded(c, 1);

            assertEquals("schema version is now 1", 1, one(c, "PRAGMA user_version"));
            assertEquals("float noise became exactly 25000", 25000, one(c, "SELECT price FROM products WHERE id = 1"));
            assertEquals("price is stored as an integer", 1, one(c, "SELECT typeof(price) = 'integer' FROM products WHERE id = 1"));
            assertEquals("a NULL cost stays NULL, not 0", 1, one(c, "SELECT cost IS NULL FROM products WHERE id = 1"));
            assertEquals("stock untouched", 10, one(c, "SELECT stock FROM products WHERE id = 1"));
            assertEquals(15000000, one(c, "SELECT salary FROM employees WHERE id = 1"));
            assertEquals(50000, one(c, "SELECT subtotal FROM orders WHERE id = 1"));
            assertEquals(5000, one(c, "SELECT discount FROM orders WHERE id = 1"));
            assertEquals(45000, one(c, "SELECT total FROM orders WHERE id = 1"));
            assertEquals(25000, one(c, "SELECT unit_price FROM order_items WHERE order_id = 1"));
            assertEquals(50000, one(c, "SELECT line_total FROM order_items WHERE order_id = 1"));
            assertEquals("the order still points at its product, customer and employee", 1,
                    one(c, "SELECT COUNT(*) FROM orders o JOIN products p ON 1=1 JOIN customers cu ON cu.id = o.customer_id "
                            + "JOIN employees e ON e.id = o.employee_id WHERE o.id = 1 AND p.id = 1"));
            assertEquals("loyalty points kept", 5, one(c, "SELECT loyalty_points FROM customers WHERE id = 1"));

            Path backupDir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "backups");
            try (Stream<Path> files = Files.exists(backupDir) ? Files.list(backupDir) : Stream.empty()) {
                assertTrue("an automatic pre-migration backup was written",
                        files.anyMatch(p -> p.getFileName().toString().startsWith("pre-migration-v1-")));
            }

            // a second run must be a harmless no-op
            MoneyMigration.runIfNeeded(c, 1);
            assertEquals(25000, one(c, "SELECT price FROM products WHERE id = 1"));
            assertEquals(1, one(c, "PRAGMA user_version"));
        }
    }
}
