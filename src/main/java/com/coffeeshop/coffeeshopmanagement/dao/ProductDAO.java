package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    private static final String SELECT_WITH_CATEGORY =
            "SELECT p.*, c.name AS category_name FROM products p LEFT JOIN categories c ON p.category_id = c.id";

    public Product insert(Product product) {
        String sql = "INSERT INTO products (name, category_id, price, cost, stock, description, image_path, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, product);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setId(keys.getInt(1));
                }
            }
            return product;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create product", e);
        }
    }

    public void update(Product product) {
        String sql = "UPDATE products SET name=?, category_id=?, price=?, cost=?, stock=?, description=?, " +
                "image_path=?, active=? WHERE id=?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, product);
            statement.setInt(9, product.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update product", e);
        }
    }

    public void setActive(int id, boolean active) {
        String sql = "UPDATE products SET active = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change product status", e);
        }
    }

    public void adjustStock(int id, int delta) {
        String sql = "UPDATE products SET stock = stock + ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, delta);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to adjust stock", e);
        }
    }

    public List<Product> findAll() {
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_WITH_CATEGORY + " ORDER BY p.id")) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load products", e);
        }
        return result;
    }

    public List<Product> findByCategory(int categoryId) {
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_WITH_CATEGORY + " WHERE p.category_id = ? ORDER BY p.id")) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load products by category", e);
        }
        return result;
    }

    public int countAll() {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM products")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products", e);
        }
    }

    public int countByCategory(int categoryId) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE category_id = ?")) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products by category", e);
        }
    }

    private void bind(PreparedStatement statement, Product product) throws SQLException {
        statement.setString(1, product.getName());
        if (product.getCategoryId() != null) {
            statement.setInt(2, product.getCategoryId());
        } else {
            statement.setNull(2, Types.INTEGER);
        }
        statement.setBigDecimal(3, product.getPrice());
        if (product.getCost() != null) {
            statement.setBigDecimal(4, product.getCost());
        } else {
            statement.setNull(4, Types.DECIMAL);
        }
        statement.setInt(5, product.getStock());
        statement.setString(6, product.getDescription());
        statement.setString(7, product.getImagePath());
        statement.setBoolean(8, product.isActive());
    }

    private Product map(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setId(rs.getInt("id"));
        product.setName(rs.getString("name"));
        int categoryId = rs.getInt("category_id");
        product.setCategoryId(rs.wasNull() ? null : categoryId);
        product.setCategoryName(rs.getString("category_name"));
        product.setPrice(rs.getBigDecimal("price"));
        BigDecimal cost = rs.getBigDecimal("cost");
        product.setCost(cost);
        product.setStock(rs.getInt("stock"));
        product.setDescription(rs.getString("description"));
        product.setImagePath(rs.getString("image_path"));
        product.setActive(rs.getBoolean("active"));
        return product;
    }
}
