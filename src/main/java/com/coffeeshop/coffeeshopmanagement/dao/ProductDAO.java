package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Product;

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

    /** Hard delete - callers must first confirm no order_items reference this product. */
    public void delete(int id) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete product", e);
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

    /** Blocks hard-deleting a product that historical orders still reference. */
    public int countOrderReferences(int productId) {
        String sql = "SELECT COUNT(*) FROM order_items WHERE product_id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check product order history", e);
        }
    }

    /** Stock level at or below which an active product is flagged on the dashboards. There is
     *  no per-product minimum-stock column, so this is one shop-wide threshold. */
    public static final int LOW_STOCK_THRESHOLD = 10;

    /** Active products at or below the threshold, lowest stock first. */
    public List<Product> findLowStock(int threshold, int limit) {
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_WITH_CATEGORY + " WHERE p.active = 1 AND p.stock <= ? ORDER BY p.stock ASC, p.name LIMIT ?")) {
            statement.setInt(1, threshold);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load low-stock products", e);
        }
        return result;
    }

    public int countLowStock(int threshold) {
        String sql = "SELECT COUNT(*) FROM products WHERE active = 1 AND stock <= ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, threshold);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count low-stock products", e);
        }
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

    private void bind(PreparedStatement statement, Product product) throws SQLException {
        statement.setString(1, product.getName());
        if (product.getCategoryId() != null) {
            statement.setInt(2, product.getCategoryId());
        } else {
            statement.setNull(2, Types.INTEGER);
        }
        statement.setLong(3, com.coffeeshop.coffeeshopmanagement.util.Money.toDong(product.getPrice()));
        Long costDong = com.coffeeshop.coffeeshopmanagement.util.Money.toDongOrNull(product.getCost());
        if (costDong != null) {
            statement.setLong(4, costDong);
        } else {
            statement.setNull(4, Types.INTEGER);
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
        product.setPrice(com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong("price")));
        long costDong = rs.getLong("cost");
        product.setCost(com.coffeeshop.coffeeshopmanagement.util.Money.fromDongOrNull(costDong, rs.wasNull()));
        product.setStock(rs.getInt("stock"));
        product.setDescription(rs.getString("description"));
        product.setImagePath(rs.getString("image_path"));
        product.setActive(rs.getBoolean("active"));
        return product;
    }
}
