package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only sales aggregates for the admin Reports window. Only PAID orders count as sales
 * (a cancelled order is a refunded sale); dates compare the calendar day of paid_at, stored
 * as an ISO local timestamp, inclusive on both ends.
 */
public class ReportDAO {

    public record Summary(int paidOrders, BigDecimal revenue, BigDecimal discounts, int cancelledOrders) {
        public BigDecimal averageOrderValue() {
            return paidOrders == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(paidOrders), 0, java.math.RoundingMode.HALF_UP);
        }
    }

    public record ProductSales(String productName, int quantity, BigDecimal revenue) {
    }

    public record DailySales(LocalDate date, int orders, BigDecimal revenue) {
    }

    public Summary summary(LocalDate from, LocalDate to) {
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN status = 'PAID' THEN 1 ELSE 0 END), 0) AS paid_count, " +
                "COALESCE(SUM(CASE WHEN status = 'PAID' THEN total ELSE 0 END), 0) AS revenue, " +
                "COALESCE(SUM(CASE WHEN status = 'PAID' THEN discount ELSE 0 END), 0) AS discounts, " +
                "COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled_count " +
                "FROM orders WHERE DATE(COALESCE(paid_at, order_date)) BETWEEN ? AND ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Summary(rs.getInt("paid_count"), nonNull(rs.getBigDecimal("revenue")),
                        nonNull(rs.getBigDecimal("discounts")), rs.getInt("cancelled_count"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load sales summary", e);
        }
    }

    /** Best sellers by units sold. Grouped by the name snapshotted on the order item. */
    public List<ProductSales> topProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT oi.product_name AS name, SUM(oi.quantity) AS qty, SUM(oi.line_total) AS revenue " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.status = 'PAID' AND DATE(o.paid_at) BETWEEN ? AND ? " +
                "GROUP BY oi.product_name ORDER BY qty DESC, revenue DESC LIMIT ?";
        List<ProductSales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            statement.setInt(3, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new ProductSales(rs.getString("name"), rs.getInt("qty"),
                            nonNull(rs.getBigDecimal("revenue"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load top products", e);
        }
        return result;
    }

    /** Only days that had at least one paid order, oldest first. */
    public List<DailySales> dailySales(LocalDate from, LocalDate to) {
        String sql = "SELECT DATE(paid_at) AS d, COUNT(*) AS orders, SUM(total) AS revenue FROM orders " +
                "WHERE status = 'PAID' AND DATE(paid_at) BETWEEN ? AND ? GROUP BY DATE(paid_at) ORDER BY d";
        List<DailySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new DailySales(LocalDate.parse(rs.getString("d")), rs.getInt("orders"),
                            nonNull(rs.getBigDecimal("revenue"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load daily sales", e);
        }
        return result;
    }

    private static void bindRange(PreparedStatement statement, LocalDate from, LocalDate to) throws SQLException {
        statement.setString(1, from.toString());
        statement.setString(2, to.toString());
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
