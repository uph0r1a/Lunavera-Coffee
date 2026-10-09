package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Order persistence, plus the aggregate/reporting queries the dashboards need. Order
 * creation (POS checkout) is not wired to any screen yet - see progress.md - but the
 * schema and this DAO are ready for that screen to be built against.
 */
public class OrderDAO {

    public Order insert(Order order, List<OrderItem> items) {
        String orderSql = "INSERT INTO orders (order_date, employee_id, customer_id, status, subtotal, discount, " +
                "total, payment_method, paid_at, table_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            int orderId;
            try (PreparedStatement statement = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, order.getOrderDate().toString());
                setNullableInt(statement, 2, order.getEmployeeId());
                setNullableInt(statement, 3, order.getCustomerId());
                statement.setString(4, order.getStatus().name());
                statement.setLong(5, Money.toDong(order.getSubtotal()));
                statement.setLong(6, Money.toDong(order.getDiscount()));
                statement.setLong(7, Money.toDong(order.getTotal()));
                statement.setString(8, order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
                statement.setString(9, order.getPaidAt() != null ? order.getPaidAt().toString() : null);
                setNullableInt(statement, 10, order.getTableNumber());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getInt(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
                for (OrderItem item : items) {
                    statement.setInt(1, orderId);
                    setNullableInt(statement, 2, item.getProductId());
                    statement.setString(3, item.getProductName());
                    statement.setInt(4, item.getQuantity());
                    statement.setLong(5, Money.toDong(item.getUnitPrice()));
                    statement.setLong(6, Money.toDong(item.getLineTotal()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            // Stock is taken inside the same transaction, and only if enough is left: a sale that
            // would drive stock below zero fails as a whole (nothing saved) instead of going negative.
            if (order.getStatus() == OrderStatus.PAID) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
                    for (OrderItem item : items) {
                        if (item.getProductId() == null) continue;
                        statement.setInt(1, item.getQuantity());
                        statement.setInt(2, item.getProductId());
                        statement.setInt(3, item.getQuantity());
                        if (statement.executeUpdate() == 0) {
                            connection.rollback();
                            throw new DataAccessException("\"" + item.getProductName()
                                    + "\" không đủ tồn kho để hoàn tất đơn. Vui lòng kiểm tra lại số lượng.");
                        }
                    }
                }
            }
            // Loyalty points are awarded inside the same transaction as the order itself, so a
            // failed save can never leave points granted for an order that doesn't exist.
            if (order.getCustomerId() != null && order.getStatus() == OrderStatus.PAID) {
                int points = LoyaltyPolicy.pointsFor(order.getTotal());
                if (points > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?")) {
                        statement.setInt(1, points);
                        statement.setInt(2, order.getCustomerId());
                        statement.executeUpdate();
                    }
                }
            }
            connection.commit();
            order.setId(orderId);
            return order;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save order", e);
        }
    }

    public Order saveOrUpdateOpenOrder(Order order, List<OrderItem> items) {
        if (order.getId() <= 0) {
            order.setStatus(OrderStatus.OPEN);
            Order saved = insert(order, items);
            if (saved.getTableNumber() != null) {
                new TableDAO().updateStatus(saved.getTableNumber(), com.coffeeshop.coffeeshopmanagement.model.TableStatus.OCCUPIED, saved.getId());
            }
            return saved;
        }
        String updateOrderSql = "UPDATE orders SET subtotal = ?, discount = ?, total = ?, customer_id = ? WHERE id = ? AND status = 'OPEN'";
        String deleteItemsSql = "DELETE FROM order_items WHERE order_id = ?";
        String insertItemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(updateOrderSql)) {
                statement.setLong(1, Money.toDong(order.getSubtotal()));
                statement.setLong(2, Money.toDong(order.getDiscount()));
                statement.setLong(3, Money.toDong(order.getTotal()));
                setNullableInt(statement, 4, order.getCustomerId());
                statement.setInt(5, order.getId());
                if (statement.executeUpdate() == 0) {
                    // The order is no longer OPEN (paid or cancelled in the meantime). A late autosave
                    // must not touch the items of a finished order.
                    connection.rollback();
                    return order;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteItemsSql)) {
                statement.setInt(1, order.getId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(insertItemSql)) {
                for (OrderItem item : items) {
                    statement.setInt(1, order.getId());
                    setNullableInt(statement, 2, item.getProductId());
                    statement.setString(3, item.getProductName());
                    statement.setInt(4, item.getQuantity());
                    statement.setLong(5, Money.toDong(item.getUnitPrice()));
                    statement.setLong(6, Money.toDong(item.getLineTotal()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            return order;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update open order #" + order.getId(), e);
        }
    }

    /**
     * Pays an OPEN order in one transaction: marks it PAID (only if it is still OPEN), links the
     * customer, rewrites the items, takes stock, awards loyalty points and frees the table.
     *
     * @param customerId customer to attach to the order, or {@code null} to keep whatever the order has
     * @throws DataAccessException if the order is not OPEN any more, stock is short, or the save fails -
     *                             in every case nothing is saved
     */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  BigDecimal total, List<OrderItem> items) {
        String orderSql = "UPDATE orders SET status = 'PAID', paid_at = ?, payment_method = ?, discount = ?, total = ?, "
                + "customer_id = COALESCE(?, customer_id) WHERE id = ? AND status = 'OPEN'";
        String deleteItemsSql = "DELETE FROM order_items WHERE order_id = ?";
        String itemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                statement.setString(1, LocalDateTime.now().toString());
                statement.setString(2, method != null ? method.name() : null);
                statement.setLong(3, Money.toDong(discount));
                statement.setLong(4, Money.toDong(total));
                setNullableInt(statement, 5, customerId);
                statement.setInt(6, orderId);
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    throw new DataAccessException("Đơn #" + orderId
                            + " không còn ở trạng thái đang mở (đã thanh toán hoặc đã hủy), không thể thanh toán lại.");
                }
            }
            Integer finalCustomerId = null;
            Integer tableNumber = null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, table_number FROM orders WHERE id = ?")) {
                statement.setInt(1, orderId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        int c = rs.getInt(1);
                        finalCustomerId = rs.wasNull() ? null : c;
                        int t = rs.getInt(2);
                        tableNumber = rs.wasNull() ? null : t;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteItemsSql)) {
                statement.setInt(1, orderId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
                for (OrderItem item : items) {
                    statement.setInt(1, orderId);
                    setNullableInt(statement, 2, item.getProductId());
                    statement.setString(3, item.getProductName());
                    statement.setInt(4, item.getQuantity());
                    statement.setLong(5, Money.toDong(item.getUnitPrice()));
                    statement.setLong(6, Money.toDong(item.getLineTotal()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
                for (OrderItem item : items) {
                    if (item.getProductId() == null) continue;
                    statement.setInt(1, item.getQuantity());
                    statement.setInt(2, item.getProductId());
                    statement.setInt(3, item.getQuantity());
                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        throw new DataAccessException("\"" + item.getProductName()
                                + "\" không đủ tồn kho để hoàn tất đơn. Vui lòng kiểm tra lại số lượng.");
                    }
                }
            }
            if (finalCustomerId != null) {
                int points = LoyaltyPolicy.pointsFor(total);
                if (points > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?")) {
                        statement.setInt(1, points);
                        statement.setInt(2, finalCustomerId);
                        statement.executeUpdate();
                    }
                }
            }
            if (tableNumber != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE dining_tables SET status = 'EMPTY', current_order_id = NULL WHERE table_number = ?")) {
                    statement.setInt(1, tableNumber);
                    statement.executeUpdate();
                }
            }
            connection.commit();
            return findById(orderId).orElse(null);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to complete payment for order #" + orderId, e);
        }
    }

    /** Makes the text match literally in a LIKE ... ESCAPE '\\' query: a typed % or _ is not a wildcard. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public Optional<Order> findById(int id) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order", e);
        }
        return Optional.empty();
    }

    /** All orders created today, newest first. */
    public List<Order> findTodayOrders() {
        List<Order> result = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE DATE(order_date) = DATE('now', 'localtime') ORDER BY id DESC";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load today's orders", e);
        }
        return result;
    }

    /** Find open order for a table if exists. */
    public Optional<Order> findOpenOrderByTable(int tableNumber) {
        String sql = "SELECT * FROM orders WHERE table_number = ? AND status = 'OPEN' ORDER BY id DESC LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load open order for table " + tableNumber, e);
        }
        return Optional.empty();
    }

    /** An order plus the display names the history screen needs (avoids N+1 lookups per row). */
    public record OrderSummary(Order order, String employeeName, String customerName) {
    }

    /** Search/status/date-range criteria for the history screen. Any field left null/blank is
     *  not applied. Its own type rather than passing four loose parameters, since
     *  {@link #findHistoryPage} and {@link #countHistory} both need to build the exact same
     *  WHERE clause from it and must never drift apart. */
    public record HistoryFilter(String keyword, OrderStatus status, LocalDate from, LocalDate to) {
        public static final HistoryFilter NONE = new HistoryFilter(null, null, null, null);
    }

    private static final class FilterSql {
        final String whereClause;
        final List<Object> params = new ArrayList<>();

        FilterSql(HistoryFilter filter) {
            List<String> conditions = new ArrayList<>();
            if (filter.status() != null) {
                conditions.add("o.status = ?");
                params.add(filter.status().name());
            }
            if (filter.from() != null) {
                conditions.add("DATE(o.order_date) >= ?");
                params.add(filter.from().toString());
            }
            if (filter.to() != null) {
                conditions.add("DATE(o.order_date) <= ?");
                params.add(filter.to().toString());
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                conditions.add("(CAST(o.id AS TEXT) LIKE ? ESCAPE '\\' OR e.full_name LIKE ? ESCAPE '\\' OR c.full_name LIKE ? ESCAPE '\\')");
                String like = "%" + escapeLike(filter.keyword().trim().replace("#", "")) + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
            whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }

        int bind(PreparedStatement statement) throws SQLException {
            int index = 1;
            for (Object param : params) {
                statement.setString(index++, (String) param);
            }
            return index;
        }
    }

    private static final String HISTORY_FROM =
            " FROM orders o LEFT JOIN employees e ON o.employee_id = e.id LEFT JOIN customers c ON o.customer_id = c.id";

    /** One page of order history, newest first, matching {@code filter} - real server-side
     *  paging (TODO.md: the old {@code findHistory(int limit)} loaded up to 500 rows into
     *  memory and filtered client-side, so a date range further back than the most recent 500
     *  orders silently showed nothing even when matching orders existed). Use with
     *  {@link #countHistory} for the total row count driving the page controls. */
    public List<OrderSummary> findHistoryPage(HistoryFilter filter, int pageSize, int offset) {
        return findHistoryPage(filter, HistorySort.NEWEST_FIRST, pageSize, offset);
    }

    /** Sortable columns of the history screen. Whitelisted here (never built from UI text) so
     *  the ORDER BY can't be used for SQL injection. */
    public enum HistorySortKey {
        ID("o.id"), DATE("o.order_date"), EMPLOYEE("e.full_name COLLATE NOCASE"),
        CUSTOMER("c.full_name COLLATE NOCASE"), PAYMENT("o.payment_method"),
        TOTAL("o.total"), STATUS("o.status");

        private final String sql;

        HistorySortKey(String sql) {
            this.sql = sql;
        }
    }

    /** Sorting is done in SQL, over the whole filtered result, <em>before</em> LIMIT/OFFSET cuts a page. */
    public record HistorySort(HistorySortKey key, boolean descending) {
        public static final HistorySort NEWEST_FIRST = new HistorySort(HistorySortKey.ID, true);
    }

    public List<OrderSummary> findHistoryPage(HistoryFilter filter, HistorySort sort, int pageSize, int offset) {
        FilterSql filterSql = new FilterSql(filter);
        HistorySort effective = sort != null ? sort : HistorySort.NEWEST_FIRST;
        String sql = "SELECT o.*, e.full_name AS employee_name, c.full_name AS customer_name"
                + HISTORY_FROM + filterSql.whereClause
                + " ORDER BY " + effective.key().sql + (effective.descending() ? " DESC" : " ASC")
                + ", o.id DESC LIMIT ? OFFSET ?";
        List<OrderSummary> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = filterSql.bind(statement);
            statement.setInt(index++, pageSize);
            statement.setInt(index, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrderSummary(map(rs), rs.getString("employee_name"), rs.getString("customer_name")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order history", e);
        }
        return result;
    }

    /** Total rows matching {@code filter}, ignoring paging - for the page-count/"N đơn" label. */
    public int countHistory(HistoryFilter filter) {
        FilterSql filterSql = new FilterSql(filter);
        String sql = "SELECT COUNT(*)" + HISTORY_FROM + filterSql.whereClause;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            filterSql.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count order history", e);
        }
    }

    /**
     * Cancels a PAID order as a full refund, in one transaction: stock is put back, the loyalty
     * points that order earned are taken back (never below zero), and the status becomes
     * CANCELLED (which the dashboard's revenue query already excludes). Nothing changes if any
     * step fails. The order row and its items are kept, so the history stays intact.
     */
    public void cancelPaidOrder(int orderId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer customerId;
                BigDecimal total;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status, customer_id, total FROM orders WHERE id = ?")) {
                    statement.setInt(1, orderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            throw new DataAccessException("Không tìm thấy đơn hàng #" + orderId);
                        }
                        if (OrderStatus.fromDb(rs.getString("status")) != OrderStatus.PAID) {
                            throw new DataAccessException("Chỉ có thể hủy đơn hàng đã thanh toán.");
                        }
                        int cid = rs.getInt("customer_id");
                        customerId = rs.wasNull() ? null : cid;
                        total = Money.fromDong(rs.getLong("total"));
                    }
                }
                try (PreparedStatement items = connection.prepareStatement(
                        "SELECT product_id, quantity FROM order_items WHERE order_id = ? AND product_id IS NOT NULL");
                     PreparedStatement restock = connection.prepareStatement(
                             "UPDATE products SET stock = stock + ? WHERE id = ?")) {
                    items.setInt(1, orderId);
                    try (ResultSet rs = items.executeQuery()) {
                        while (rs.next()) {
                            restock.setInt(1, rs.getInt("quantity"));
                            restock.setInt(2, rs.getInt("product_id"));
                            restock.addBatch();
                        }
                    }
                    restock.executeBatch();
                }
                if (customerId != null) {
                    int points = LoyaltyPolicy.pointsFor(total);
                    if (points > 0) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE customers SET loyalty_points = MAX(0, loyalty_points - ?) WHERE id = ?")) {
                            statement.setInt(1, points);
                            statement.setInt(2, customerId);
                            statement.executeUpdate();
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET status = 'CANCELLED' WHERE id = ?")) {
                    statement.setInt(1, orderId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to cancel order", e);
        }
    }

    public List<OrderItem> findItemsByOrderId(int orderId) {
        String sql = "SELECT * FROM order_items WHERE order_id = ? ORDER BY id";
        List<OrderItem> items = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    int productId = rs.getInt("product_id");
                    item.setProductId(rs.wasNull() ? null : productId);
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(Money.fromDong(rs.getLong("unit_price")));
                    item.setLineTotal(Money.fromDong(rs.getLong("line_total")));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order items", e);
        }
        return items;
    }

    public int countToday() {
        return countWhere("status <> 'CANCELLED' AND DATE(order_date) = DATE('now', 'localtime')");
    }

    public int countByStatus(OrderStatus status) {
        String sql = "SELECT COUNT(*) FROM orders WHERE status = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count orders by status", e);
        }
    }

    public BigDecimal sumRevenueToday() {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM orders WHERE status = 'PAID' " +
                "AND DATE(paid_at) = DATE('now', 'localtime')";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return Money.fromDong(rs.getLong(1)); // COALESCE(...,0) guarantees a non-null row
        } catch (SQLException e) {
            throw new DataAccessException("Failed to sum today's revenue", e);
        }
    }

    public int countDistinctCustomersToday() {
        String sql = "SELECT COUNT(DISTINCT customer_id) FROM orders " +
                "WHERE customer_id IS NOT NULL AND status <> 'CANCELLED' AND DATE(order_date) = DATE('now', 'localtime')";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count today's customers", e);
        }
    }

    /**
     * Paid revenue for each of the last 7 days (oldest first), keyed by date. Days with no
     * paid orders are included with a value of zero so charts don't skip gaps.
     */
    public Map<LocalDate, BigDecimal> revenueForLast7Days() {
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            byDate.put(today.minusDays(i), BigDecimal.ZERO);
        }
        String sql = "SELECT DATE(paid_at) AS d, SUM(total) AS revenue FROM orders " +
                "WHERE status = 'PAID' AND paid_at IS NOT NULL AND DATE(paid_at) >= DATE('now', '-6 days', 'localtime') " +
                "GROUP BY DATE(paid_at)";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                LocalDate date = LocalDate.parse(rs.getString("d"));
                byDate.put(date, Money.fromDong(rs.getLong("revenue")));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load 7-day revenue", e);
        }
        return byDate;
    }

    private int countWhere(String whereClause) {
        String sql = "SELECT COUNT(*) FROM orders WHERE " + whereClause;
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count orders", e);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private Order map(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setId(rs.getInt("id"));
        order.setOrderDate(LocalDateTime.parse(rs.getString("order_date")));
        int employeeId = rs.getInt("employee_id");
        order.setEmployeeId(rs.wasNull() ? null : employeeId);
        int customerId = rs.getInt("customer_id");
        order.setCustomerId(rs.wasNull() ? null : customerId);
        order.setStatus(OrderStatus.fromDb(rs.getString("status")));
        order.setSubtotal(Money.fromDong(rs.getLong("subtotal")));
        order.setDiscount(Money.fromDong(rs.getLong("discount")));
        order.setTotal(Money.fromDong(rs.getLong("total")));
        order.setPaymentMethod(PaymentMethod.fromDb(rs.getString("payment_method")));
        String paidAt = rs.getString("paid_at");
        order.setPaidAt(paidAt != null ? LocalDateTime.parse(paidAt) : null);
        try {
            int tableNumber = rs.getInt("table_number");
            order.setTableNumber(rs.wasNull() ? null : tableNumber);
        } catch (SQLException ignored) {
        }
        return order;
    }
}
