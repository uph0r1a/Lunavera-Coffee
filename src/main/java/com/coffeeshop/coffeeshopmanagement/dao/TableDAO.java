package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class TableDAO {

    public List<DiningTable> findAll() {
        String sql = """
            SELECT 
                d.id, 
                d.table_number, 
                d.name, 
                d.capacity, 
                o.id AS open_order_id,
                CASE 
                    WHEN o.id IS NOT NULL OR d.status = 'OCCUPIED' THEN 'OCCUPIED' 
                    ELSE 'EMPTY' 
                END AS computed_status
            FROM dining_tables d
            LEFT JOIN (
                SELECT id, table_number 
                FROM orders 
                WHERE status = 'OPEN' 
                GROUP BY table_number
            ) o ON o.table_number = d.table_number
            ORDER BY d.table_number ASC
        """;
        List<DiningTable> tables = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tables.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Không thể tải danh sách bàn: " + e.getMessage(), e);
        }
        return tables;
    }

    public void updateStatus(int tableNumber, TableStatus status, Integer orderId) {
        String sql = "UPDATE dining_tables SET status = ?, current_order_id = ? WHERE table_number = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            if (orderId != null) {
                statement.setInt(2, orderId);
            } else {
                statement.setNull(2, Types.INTEGER);
            }
            statement.setInt(3, tableNumber);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Không thể cập nhật trạng thái bàn " + tableNumber + ": " + e.getMessage(), e);
        }
    }

    public void setOccupied(int tableNumber, Integer orderId) {
        updateStatus(tableNumber, TableStatus.OCCUPIED, orderId);
    }

    /** Frees the table and cancels its OPEN order, both or neither (one transaction). */
    public void setEmpty(int tableNumber) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE dining_tables SET status = 'EMPTY', current_order_id = NULL WHERE table_number = ?")) {
                statement.setInt(1, tableNumber);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE orders SET status = 'CANCELLED' WHERE table_number = ? AND status = 'OPEN'")) {
                statement.setInt(1, tableNumber);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            throw new DataAccessException("Không thể giải phóng bàn " + tableNumber + ": " + e.getMessage(), e);
        }
    }

    private DiningTable mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int tableNumber = rs.getInt("table_number");
        String name = rs.getString("name");
        TableStatus status = TableStatus.fromDb(rs.getString("computed_status"));
        int capacity = rs.getInt("capacity");
        int orderId = rs.getInt("open_order_id");
        Integer currentOrderId = rs.wasNull() ? null : orderId;
        return new DiningTable(id, tableNumber, name, status, capacity, currentOrderId);
    }
}
