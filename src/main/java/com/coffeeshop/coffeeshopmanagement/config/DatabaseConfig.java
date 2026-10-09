package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
    /** Seeded for the very first login only; the app prompts to change it (see DefaultPasswordPrompt). */
    public static final String DEFAULT_ADMIN_PASSWORD = "Admin@123";
    /** v1: money columns are INTEGER dong instead of REAL (see MoneyMigration). */
    static final int CURRENT_SCHEMA_VERSION = 1;
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
            // TODO.md item 12: without this, two connections writing at once (e.g. the 30s
            // SessionGuard background check landing mid-payment) get an immediate "database is
            // locked" SQLException instead of one of them just waiting briefly - SQLite's
            // default busy behavior is to not wait at all. 5s is generous for a single-till
            // desktop app; a real contention problem should surface as a slow UI, not a crash.
            pragma.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    /**
     * Writes a consistent copy of the whole database to {@code target} using SQLite's
     * {@code VACUUM INTO} (safe while the app is running, unlike copying the file by hand).
     * Restoring is manual: close the app and replace ~/.lunavera-coffee/lunavera.db with the backup.
     */
    public static void backupTo(java.nio.file.Path target) throws SQLException, java.io.IOException {
        java.nio.file.Files.deleteIfExists(target); // VACUUM INTO refuses to overwrite
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        }
    }

    /**
     * Creates all tables if they do not already exist, seeds one default admin account the
     * very first time the application runs against an empty database, and seeds the starter
     * menu exactly once (see {@link MenuSeeder} - gated by its own persistent flag, not by
     * current table contents, so deleting starter items never brings them back). Safe to call
     * every time the application starts.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try (Connection connection = getConnection()) {
            // Must check this *before* creating any table below: it's the only way to tell a
            // brand-new database (no migration needed - CREATE TABLE already uses the current
            // schema) apart from one that existed before schema versioning was added (PRAGMA
            // user_version defaults to 0 for both, so the version number alone can't tell them
            // apart - only "did any of our tables already exist" can).
            boolean preexisting = tableExists(connection, "users");

            try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    address TEXT,
                    position TEXT,
                    salary INTEGER,
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
                    price INTEGER NOT NULL,
                    cost INTEGER,
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
                    subtotal INTEGER NOT NULL DEFAULT 0,
                    discount INTEGER NOT NULL DEFAULT 0,
                    total INTEGER NOT NULL DEFAULT 0,
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
                    unit_price INTEGER NOT NULL,
                    line_total INTEGER NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS dining_tables (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_number INTEGER UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'EMPTY',
                    capacity INTEGER DEFAULT 4,
                    current_order_id INTEGER,
                    FOREIGN KEY (current_order_id) REFERENCES orders(id)
                )
                """);

            try {
                statement.execute("ALTER TABLE orders ADD COLUMN table_number INTEGER");
            } catch (SQLException ignored) {
                // Column already exists
            }
            }

            if (preexisting) {
                MoneyMigration.runIfNeeded(connection, CURRENT_SCHEMA_VERSION);
            } else {
                setUserVersion(connection, CURRENT_SCHEMA_VERSION);
            }

            seedDefaultAdmin(connection);
            seedDiningTables(connection);
            MenuSeeder.seedIfNeeded(connection);
            syncCategoriesAndProducts(connection);
            DataCleanup.runIfNeeded(connection);
            initialized = true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", e);
            throw new IllegalStateException("Could not initialize the database", e);
        }
    }

    private static void syncCategoriesAndProducts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            Integer caPheWithAccentId = null;
            Integer caPheNoAccentId = null;
            try (ResultSet rs = statement.executeQuery("SELECT id, name FROM categories WHERE name = 'Cà phê' OR name = 'Ca Phe'")) {
                while (rs.next()) {
                    if ("Cà phê".equals(rs.getString("name"))) {
                        caPheWithAccentId = rs.getInt("id");
                    } else if ("Ca Phe".equals(rs.getString("name"))) {
                        caPheNoAccentId = rs.getInt("id");
                    }
                }
            }
            if (caPheWithAccentId != null && caPheNoAccentId != null && !caPheWithAccentId.equals(caPheNoAccentId)) {
                statement.executeUpdate("UPDATE products SET category_id = " + caPheWithAccentId + " WHERE category_id = " + caPheNoAccentId);
                statement.executeUpdate("DELETE FROM categories WHERE id = " + caPheNoAccentId);
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (java.sql.PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** PRAGMA statements don't support `?` placeholders - safe here since version is always
     *  this class's own int constant, never anything derived from user input. */
    static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
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
            insertUser.setString(2, PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
            insertUser.setString(3, Role.ADMIN.name());
            insertUser.setInt(4, employeeId);
            insertUser.setString(5, LocalDateTime.now().toString());
            insertUser.executeUpdate();
        }

        LOGGER.info("Seeded default admin account (username: admin / password: Admin@123). " +
                "Change this password after first login.");
    }

    private static void seedDiningTables(Connection connection) throws SQLException {
        String flagName = "dining_tables_v1";
        try (Statement createFlags = connection.createStatement()) {
            createFlags.execute("CREATE TABLE IF NOT EXISTS app_flags (name TEXT PRIMARY KEY)");
        }
        try (var check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, flagName);
            try (var rs = check.executeQuery()) {
                if (rs.next()) {
                    return; // already seeded once; never re-seed if the user removed tables
                }
            }
        }

        String insertSql = "INSERT OR IGNORE INTO dining_tables (table_number, name, status, capacity) VALUES (?, ?, 'EMPTY', ?)";
        try (var insert = connection.prepareStatement(insertSql)) {
            for (int i = 1; i <= 12; i++) {
                insert.setInt(1, i);
                insert.setString(2, "Bàn " + i);
                insert.setInt(3, 4);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (var insertFlag = connection.prepareStatement("INSERT OR IGNORE INTO app_flags (name) VALUES (?)")) {
            insertFlag.setString(1, flagName);
            insertFlag.executeUpdate();
        }
        LOGGER.info("Seeded 12 dining tables into dining_tables.");
    }
}
