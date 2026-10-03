package com.coffeeshop.coffeeshopmanagement.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One-time migration from the original schema (money stored as REAL/floating point) to money
 * stored as INTEGER dong (schema version 1). Vietnamese dong has no fractional subunit in real
 * use, and every amount this app has ever written is already a whole number, so this conversion
 * is exact and lossless for legitimate data - {@code ROUND()} is applied only as a safety net
 * against stray floating-point representation noise (e.g. 25000.000000000004 from REAL
 * arithmetic), never because fractional dong are expected.
 *
 * Only runs against a database that already existed before schema versioning was added - see
 * {@code DatabaseConfig.initialize()}'s {@code preexisting} check. A brand-new database is
 * created directly with the INTEGER schema and never reaches this class.
 */
final class MoneyMigration {

    private static final Logger LOGGER = Logger.getLogger(MoneyMigration.class.getName());

    private MoneyMigration() {
    }

    static void runIfNeeded(Connection connection, int currentVersion) throws SQLException {
        int version = getUserVersion(connection);
        if (version >= currentVersion) {
            return;
        }
        if (version == 0) {
            migrateV0ToV1(connection);
            version = 1;
        }
        // Future versions would chain further `if (version == N) { migrateNToNPlus1(...); version = N+1; }`
        // steps here, each one small and independently testable, rather than one big jump.
        DatabaseConfig.setUserVersion(connection, version);
    }

    private static void migrateV0ToV1(Connection connection) throws SQLException {
        backupBeforeMigration();

        // SQLite only honors a change to `foreign_keys` when there is no transaction already
        // open - setting it after setAutoCommit(false) (which starts one) would silently do
        // nothing, leaving FK enforcement on and the rename/recreate sequence below failing
        // with a constraint error. Both pragmas are set on this one connection before its
        // transaction starts, and neither persists past this connection, which is closed right
        // after initialize() finishes - every other connection in the app still gets
        // foreign_keys=ON as normal from getConnection().
        try (Statement pragmaStatement = connection.createStatement()) {
            pragmaStatement.execute("PRAGMA foreign_keys = OFF");
            // legacy_alter_table=ON stops SQLite's "smart" RENAME from rewriting *other* tables'
            // foreign-key target names when the table they reference gets renamed below (e.g.
            // order_items' FK on "products" silently becoming a FK on "products_old_v0") -
            // without it, a table referenced by an FK can't safely be migrated this way at all.
            pragmaStatement.execute("PRAGMA legacy_alter_table = ON");
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            migrateTable(statement, "employees",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL, phone TEXT, email TEXT, " +
                            "address TEXT, position TEXT, salary INTEGER, hire_date TEXT, " +
                            "active INTEGER NOT NULL DEFAULT 1",
                    "id, full_name, phone, email, address, position, CAST(ROUND(salary) AS INTEGER), " +
                            "hire_date, active");

            migrateTable(statement, "products",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category_id INTEGER, " +
                            "price INTEGER NOT NULL, cost INTEGER, stock INTEGER NOT NULL DEFAULT 0, " +
                            "description TEXT, image_path TEXT, active INTEGER NOT NULL DEFAULT 1, " +
                            "FOREIGN KEY (category_id) REFERENCES categories(id)",
                    "id, name, category_id, CAST(ROUND(price) AS INTEGER), CAST(ROUND(cost) AS INTEGER), " +
                            "stock, description, image_path, active");

            migrateTable(statement, "orders",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, order_date TEXT NOT NULL, employee_id INTEGER, " +
                            "customer_id INTEGER, status TEXT NOT NULL DEFAULT 'OPEN', " +
                            "subtotal INTEGER NOT NULL DEFAULT 0, discount INTEGER NOT NULL DEFAULT 0, " +
                            "total INTEGER NOT NULL DEFAULT 0, payment_method TEXT, paid_at TEXT, " +
                            "FOREIGN KEY (employee_id) REFERENCES employees(id), " +
                            "FOREIGN KEY (customer_id) REFERENCES customers(id)",
                    "id, order_date, employee_id, customer_id, status, CAST(ROUND(subtotal) AS INTEGER), " +
                            "CAST(ROUND(discount) AS INTEGER), CAST(ROUND(total) AS INTEGER), payment_method, paid_at");

            migrateTable(statement, "order_items",
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL, product_id INTEGER, " +
                            "product_name TEXT NOT NULL, quantity INTEGER NOT NULL, unit_price INTEGER NOT NULL, " +
                            "line_total INTEGER NOT NULL, FOREIGN KEY (order_id) REFERENCES orders(id), " +
                            "FOREIGN KEY (product_id) REFERENCES products(id)",
                    "id, order_id, product_id, product_name, quantity, CAST(ROUND(unit_price) AS INTEGER), " +
                            "CAST(ROUND(line_total) AS INTEGER)");

            connection.commit();
            LOGGER.info("Migrated money columns from REAL to INTEGER dong (schema v0 -> v1).");
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void migrateTable(Statement statement, String table, String newColumns, String selectExpr)
            throws SQLException {
        String oldTable = table + "_old_v0";
        statement.execute("ALTER TABLE " + table + " RENAME TO " + oldTable);
        statement.execute("CREATE TABLE " + table + " (" + newColumns + ")");
        statement.execute("INSERT INTO " + table + " SELECT " + selectExpr + " FROM " + oldTable);
        statement.execute("DROP TABLE " + oldTable);
    }

    /**
     * On top of whatever backup the shop may have taken via the admin dashboard's "Sao lưu dữ
     * liệu" button, this one happens automatically and can't be skipped by forgetting to click
     * it. Failure here is logged but never blocks the migration itself - an unmigrated database
     * stuck on the old REAL schema forever is worse than a migration that proceeds without this
     * extra safety net (the user's own manual backups, if any, remain the fallback).
     */
    private static void backupBeforeMigration() {
        try {
            Path backupDir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "backups");
            Files.createDirectories(backupDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path target = backupDir.resolve("pre-migration-v1-" + stamp + ".db");
            DatabaseConfig.backupTo(target);
            LOGGER.info("Backed up database before money-column migration: " + target);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not create an automatic pre-migration backup; proceeding anyway", e);
        }
    }

    private static int getUserVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
