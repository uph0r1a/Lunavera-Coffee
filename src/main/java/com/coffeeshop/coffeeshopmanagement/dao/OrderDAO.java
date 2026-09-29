package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

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
                "total, payment_method, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
        return countWhere("DATE(order_date) = DATE('now', 'localtime')");
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
                "WHERE customer_id IS NOT NULL AND DATE(order_date) = DATE('now', 'localtime')";
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
        return order;
    }
}
