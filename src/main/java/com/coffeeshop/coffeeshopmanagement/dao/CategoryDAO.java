package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CategoryDAO {

    public Category insert(Category category) {
        String sql = "INSERT INTO categories (name, description, active) VALUES (?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getDescription());
            statement.setBoolean(3, category.isActive());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getInt(1));
                }
            }
            return category;
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DataAccessException("Đã tồn tại danh mục với tên này");
            }
            throw new DataAccessException("Failed to create category", e);
        }
    }

    public void update(Category category) {
        String sql = "UPDATE categories SET name = ?, description = ?, active = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getDescription());
            statement.setBoolean(3, category.isActive());
            statement.setInt(4, category.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DataAccessException("Đã tồn tại danh mục với tên này");
            }
            throw new DataAccessException("Failed to update category", e);
        }
    }

    /**
     * Deletes a category only when no product references it, to avoid orphaning products
     * or invalidating historical order data. Returns false (and deletes nothing) if products
     * still depend on it; the caller should offer to deactivate instead.
     */
    public boolean deleteIfUnused(int categoryId) {
        String countSql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                count.setInt(1, categoryId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return false;
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM categories WHERE id = ?")) {
                delete.setInt(1, categoryId);
                delete.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete category", e);
        }
    }

    public Optional<Category> findById(int id) {
        String sql = "SELECT * FROM categories WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load category", e);
        }
        return Optional.empty();
    }

    public List<Category> findAll() {
        String sql = "SELECT * FROM categories ORDER BY id";
        List<Category> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load categories", e);
        }
        return result;
    }

    public int countProductsInCategory(int categoryId) {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products in category", e);
        }
    }

    /**
     * Product count for every category in one query, keyed by category id. Prefer this over
     * calling {@link #countProductsInCategory(int)} once per category in a loop (e.g. from a
     * table's cell-value-factory) - that pattern re-opens a connection per category, per
     * refresh, and gets noticeably slow as soon as a screen re-renders on every keystroke.
     */
    public Map<Integer, Integer> countProductsByCategory() {
        String sql = "SELECT category_id, COUNT(*) AS cnt FROM products " +
                "WHERE category_id IS NOT NULL GROUP BY category_id";
        Map<Integer, Integer> result = new HashMap<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getInt("category_id"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products by category", e);
        }
        return result;
    }

    private boolean isUniqueConstraintViolation(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toUpperCase().contains("UNIQUE");
    }

    private Category map(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getInt("id"));
        category.setName(rs.getString("name"));
        category.setDescription(rs.getString("description"));
        category.setActive(rs.getBoolean("active"));
        return category;
    }
}
