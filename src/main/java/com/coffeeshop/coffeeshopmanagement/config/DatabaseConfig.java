package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the SQLite connection string and one-time schema/seed setup.
 *
 * The database file lives under the user's home directory (~/.lunavera-coffee/lunavera.db)
 * rather than the project folder, so data survives regardless of the working directory the
 * app is launched from and is not accidentally wiped by a Maven "clean".
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String DB_FILE_NAME = "lunavera.db";
    private static volatile boolean initialized = false;

    private DatabaseConfig() {
    }

    private static String resolveDbUrl() {
        Path dir = Path.of(System.getProperty("user.home"), ".lunavera-coffee");
        File dirFile = dir.toFile();
        if (!dirFile.exists() && !dirFile.mkdirs()) {
            LOGGER.warning("Could not create data directory " + dir + "; falling back to working directory.");
            return "jdbc:sqlite:" + DB_FILE_NAME;
        }
        return "jdbc:sqlite:" + dir.resolve(DB_FILE_NAME);
    }

    private static final String DB_URL = resolveDbUrl();

    /**
     * Opens a new short-lived connection. SQLite is file-based and handles many short
     * connections from a single-user desktop app well; callers should use try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /**
     * Creates all tables if they do not already exist, and seeds one default admin account
     * the very first time the application runs against an empty database. Safe to call every
     * time the application starts.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    address TEXT,
                    position TEXT,
                    salary REAL,
                    hire_date TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    loyalty_points INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    employee_id INTEGER,
                    customer_id INTEGER,
                    created_at TEXT NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    category_id INTEGER,
                    price REAL NOT NULL,
                    cost REAL,
                    stock INTEGER NOT NULL DEFAULT 0,
                    description TEXT,
                    image_path TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (category_id) REFERENCES categories(id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_date TEXT NOT NULL,
                    employee_id INTEGER,
                    customer_id INTEGER,
                    status TEXT NOT NULL DEFAULT 'OPEN',
                    subtotal REAL NOT NULL DEFAULT 0,
                    discount REAL NOT NULL DEFAULT 0,
                    total REAL NOT NULL DEFAULT 0,
                    payment_method TEXT,
                    paid_at TEXT,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS order_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    product_id INTEGER,
                    product_name TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    unit_price REAL NOT NULL,
                    line_total REAL NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
                """);

            seedDefaultAdmin(connection);
            initialized = true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", e);
            throw new IllegalStateException("Could not initialize the database", e);
        }
    }

    private static void seedDefaultAdmin(Connection connection) throws SQLException {
        try (Statement check = connection.createStatement()) {
            var rs = check.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            if (rs.getInt(1) > 0) {
                return; // already has data, never overwrite
            }
        }

        int employeeId;
        try (var insertEmployee = connection.prepareStatement(
                "INSERT INTO employees (full_name, phone, email, address, position, salary, hire_date, active) " +
                        "VALUES (?, NULL, NULL, NULL, ?, NULL, ?, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            insertEmployee.setString(1, "Quản trị viên hệ thống");
            insertEmployee.setString(2, "Owner");
            insertEmployee.setString(3, LocalDateTime.now().toLocalDate().toString());
            insertEmployee.executeUpdate();
            var keys = insertEmployee.getGeneratedKeys();
            keys.next();
            employeeId = keys.getInt(1);
        }

        try (var insertUser = connection.prepareStatement(
                "INSERT INTO users (username, password_hash, role, status, employee_id, created_at) " +
                        "VALUES (?, ?, ?, 'ACTIVE', ?, ?)")) {
            insertUser.setString(1, "admin");
            insertUser.setString(2, PasswordUtil.hash("Admin@123"));
            insertUser.setString(3, Role.ADMIN.name());
            insertUser.setInt(4, employeeId);
            insertUser.setString(5, LocalDateTime.now().toString());
            insertUser.executeUpdate();
        }

        LOGGER.info("Seeded default admin account (username: admin / password: Admin@123). " +
                "Change this password after first login.");
    }
}
