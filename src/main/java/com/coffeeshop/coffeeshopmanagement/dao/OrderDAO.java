package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;

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
                statement.setBigDecimal(5, order.getSubtotal());
                statement.setBigDecimal(6, order.getDiscount());
                statement.setBigDecimal(7, order.getTotal());
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
                    statement.setBigDecimal(5, item.getUnitPrice());
                    statement.setBigDecimal(6, item.getLineTotal());
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

    /** Newest orders first (by id, which is monotonic - more reliable than comparing dates). */
    public List<Order> findRecent(int limit) {
        List<Order> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load recent orders", e);
        }
        return result;
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

    /** Returns the next order id (1 if empty, or max(id) + 1). */
    public int getNextOrderId() {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 FROM orders";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 1;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get next order id", e);
        }
    }

    /** An order plus the display names the history screen needs (avoids N+1 lookups per row). */
    public record OrderSummary(Order order, String employeeName, String customerName) {
    }

    /** Newest first. Capped so the history window stays fast on a long-running database. */
    public List<OrderSummary> findHistory(int limit) {
        String sql = "SELECT o.*, e.full_name AS employee_name, c.full_name AS customer_name FROM orders o " +
                "LEFT JOIN employees e ON o.employee_id = e.id " +
                "LEFT JOIN customers c ON o.customer_id = c.id ORDER BY o.id DESC LIMIT ?";
        List<OrderSummary> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
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
                        total = rs.getBigDecimal("total");
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
                    item.setOrderId(rs.getInt("order_id"));
                    int productId = rs.getInt("product_id");
                    item.setProductId(rs.wasNull() ? null : productId);
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setLineTotal(rs.getBigDecimal("line_total"));
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
            BigDecimal value = rs.getBigDecimal(1);
            return value != null ? value : BigDecimal.ZERO;
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
                byDate.put(date, rs.getBigDecimal("revenue"));
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
        order.setSubtotal(rs.getBigDecimal("subtotal"));
        order.setDiscount(rs.getBigDecimal("discount"));
        order.setTotal(rs.getBigDecimal("total"));
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
