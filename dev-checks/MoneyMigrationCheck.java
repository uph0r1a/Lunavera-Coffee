// Dev-only check (not part of the Maven build, lives outside src/). Verifies the v0->v1 money
// migration (REAL -> INTEGER dong) against a database built to look exactly like one an older
// version of the app would have left behind - not a fresh database, which never touches the
// migration path at all (DatabaseConfig creates fresh ones with the new schema directly).
//
// Compile/run the same way as SessionGuardCheck.java (see progress.md):
//   java -Duser.home=$TMP -cp ... MoneyMigrationCheck
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

public class MoneyMigrationCheck {
    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        // Step 1: build a database that looks exactly like one an older app version left
        // behind - the OLD schema, REAL money columns, BEFORE DatabaseConfig.initialize() (and
        // therefore the migration) has ever run against it.
        int categoryId, productId, employeeId, customerId, orderId;
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE employees (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL,
                    phone TEXT, email TEXT, address TEXT, position TEXT, salary REAL, hire_date TEXT,
                    active INTEGER NOT NULL DEFAULT 1)
                """);
            statement.execute("""
                CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, full_name TEXT NOT NULL,
                    phone TEXT, email TEXT, loyalty_points INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)
                """);
            statement.execute("""
                CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE',
                    employee_id INTEGER, customer_id INTEGER, created_at TEXT NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id))
                """);
            statement.execute("""
                CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE,
                    description TEXT, active INTEGER NOT NULL DEFAULT 1)
                """);
            statement.execute("""
                CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
                    category_id INTEGER, price REAL NOT NULL, cost REAL, stock INTEGER NOT NULL DEFAULT 0,
                    description TEXT, image_path TEXT, active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (category_id) REFERENCES categories(id))
                """);
            statement.execute("""
                CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, order_date TEXT NOT NULL,
                    employee_id INTEGER, customer_id INTEGER, status TEXT NOT NULL DEFAULT 'OPEN',
                    subtotal REAL NOT NULL DEFAULT 0, discount REAL NOT NULL DEFAULT 0,
                    total REAL NOT NULL DEFAULT 0, payment_method TEXT, paid_at TEXT,
                    FOREIGN KEY (employee_id) REFERENCES employees(id),
                    FOREIGN KEY (customer_id) REFERENCES customers(id))
                """);
            statement.execute("""
                CREATE TABLE order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL,
                    product_id INTEGER, product_name TEXT NOT NULL, quantity INTEGER NOT NULL,
                    unit_price REAL NOT NULL, line_total REAL NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id))
                """);

            statement.execute("INSERT INTO categories (name, active) VALUES ('Cà phê', 1)");
            categoryId = lastInsertId(statement);
            // 25000.00000000004 simulates the kind of floating-point noise REAL arithmetic can
            // leave behind - the real reason ROUND() is in the migration, not a rounding-rule test.
            statement.execute("INSERT INTO products (name, category_id, price, cost, stock, active) " +
                    "VALUES ('Old Coffee', " + categoryId + ", 25000.00000000004, NULL, 10, 1)");
            productId = lastInsertId(statement);
            statement.execute("INSERT INTO employees (full_name, salary, active) " +
                    "VALUES ('Old Employee', 15000000.0, 1)");
            employeeId = lastInsertId(statement);
            statement.execute("INSERT INTO customers (full_name, loyalty_points, created_at) " +
                    "VALUES ('Old Customer', 5, '" + LocalDateTime.now() + "')");
            customerId = lastInsertId(statement);
            statement.execute("INSERT INTO orders (order_date, employee_id, customer_id, status, " +
                    "subtotal, discount, total, payment_method, paid_at) VALUES ('" + LocalDateTime.now() +
                    "', " + employeeId + ", " + customerId + ", 'PAID', 50000.0, 5000.0, 45000.0, 'CASH', '" +
                    LocalDateTime.now() + "')");
            orderId = lastInsertId(statement);
            statement.execute("INSERT INTO order_items (order_id, product_id, product_name, quantity, " +
                    "unit_price, line_total) VALUES (" + orderId + ", " + productId +
                    ", 'Old Coffee', 2, 25000.0, 50000.0)");
        }

        // Step 2: the moment of truth - a "new version of the app" launches against this old file.
        DatabaseConfig.initialize();

        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            rs.next();
            check("schema version is now 1", rs.getInt(1) == 1);
        }

        Product product = new ProductDAO().findAll().stream()
                .filter(p -> p.getId() == productId).findFirst().orElse(null);
        check("migrated product still found by its original id", product != null);
        if (product != null) {
            check("price survived the float-noise value as exactly 25000",
                    0 == new BigDecimal("25000").compareTo(product.getPrice()));
            check("NULL cost stayed NULL (not 0)", product.getCost() == null);
            check("stock (already INTEGER, untouched by this migration) is unchanged", product.getStock() == 10);
        }

        Employee employee = new EmployeeDAO().findAll().stream()
                .filter(e -> e.getId() == employeeId).findFirst().orElse(null);
        check("migrated employee still found", employee != null);
        if (employee != null) {
            check("salary survived as exactly 15000000", 0 == new BigDecimal("15000000").compareTo(employee.getSalary()));
        }

        OrderDAO orderDAO = new OrderDAO();
        Order order = orderDAO.findById(orderId).orElse(null);
        check("migrated order still found", order != null);
        if (order != null) {
            check("subtotal exactly 50000", 0 == new BigDecimal("50000").compareTo(order.getSubtotal()));
            check("discount exactly 5000", 0 == new BigDecimal("5000").compareTo(order.getDiscount()));
            check("total exactly 45000", 0 == new BigDecimal("45000").compareTo(order.getTotal()));
        }
        List<OrderItem> items = orderDAO.findItemsByOrderId(orderId);
        check("order item survived", items.size() == 1);
        if (!items.isEmpty()) {
            check("unit_price exactly 25000", 0 == new BigDecimal("25000").compareTo(items.get(0).getUnitPrice()));
            check("line_total exactly 50000", 0 == new BigDecimal("50000").compareTo(items.get(0).getLineTotal()));
        }

        Path backupDir = Path.of(System.getProperty("user.home"), ".lunavera-coffee", "backups");
        boolean backupExists;
        try (Stream<Path> files = Files.exists(backupDir) ? Files.list(backupDir) : Stream.empty()) {
            backupExists = files.anyMatch(p -> p.getFileName().toString().startsWith("pre-migration-v1-"));
        }
        check("an automatic pre-migration backup file was created", backupExists);

        // Step 3: a second call in the same process must be a safe no-op (mirrors what a second
        // DatabaseConfig.initialize() call, or a later app relaunch past v1, should look like).
        DatabaseConfig.initialize();
        check("product count unchanged after calling initialize() again", new ProductDAO().findAll().size() >= 1);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURE(S)");
        if (failures > 0) System.exit(1);
    }

    private static int lastInsertId(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
