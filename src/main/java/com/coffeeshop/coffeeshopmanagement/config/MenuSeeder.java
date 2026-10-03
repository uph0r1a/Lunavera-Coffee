package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.ImageStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Seeds the starter menu ({@link MenuSeedData}, from the shop's "menu.zip" photo set) into a
 * database exactly once, gated by a persistent flag row rather than by "is the products table
 * empty right now". The whole point of the flag is that a shop that deletes some or all of the
 * starter items never sees them come back - a current-state check could not guarantee that
 * (delete everything -> table is empty again -> a naive "seed if empty" check would fire again).
 * The flag is written in the *same transaction* as the seeded categories/products, so a crash
 * partway through leaves nothing seeded and no flag set either - the next launch retries
 * cleanly instead of leaving a half-seeded menu silently marked "done".
 *
 * Prices are placeholder round numbers I picked per category (25.000-52.000đ, roughly matching
 * real Vietnamese cafe pricing) since menu.zip is photos only, no price list - the shop should
 * review these in Product Management. Starting stock is a flat 20 per item for the same reason:
 * a reasonable default so the POS has something to sell immediately, not real inventory counts.
 */
final class MenuSeeder {

    private static final Logger LOGGER = Logger.getLogger(MenuSeeder.class.getName());
    private static final String FLAG_NAME = "starter_menu_v1";
    private static final int STARTING_STOCK = 20;

    private MenuSeeder() {
    }

    static void seedIfNeeded(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS app_flags (name TEXT PRIMARY KEY)");
        }
        try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
            check.setString(1, FLAG_NAME);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    return; // already seeded (or the shop deleted some/all of it since) - never run again
                }
            }
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Map<String, Integer> categoryIds = new HashMap<>();
            for (String categoryName : distinctCategoryNames()) {
                categoryIds.put(categoryName, findOrCreateCategory(connection, categoryName));
            }

            Path imagesDir = ImageStorage.directory();
            for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
                String storedImageName = copySeedImage(item.imageResource(), imagesDir);
                insertProduct(connection, item, categoryIds.get(item.category()), storedImageName);
            }

            try (PreparedStatement insertFlag = connection.prepareStatement(
                    "INSERT INTO app_flags (name) VALUES (?)")) {
                insertFlag.setString(1, FLAG_NAME);
                insertFlag.executeUpdate();
            }
            connection.commit();
            LOGGER.info("Seeded the starter menu (" + MenuSeedData.ITEMS.size() + " products).");
        } catch (SQLException | IOException | RuntimeException e) {
            connection.rollback();
            LOGGER.log(Level.WARNING, "Starter menu seeding failed; will retry on next launch", e);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static List<String> distinctCategoryNames() {
        return MenuSeedData.ITEMS.stream().map(MenuSeedData.MenuItem::category).distinct().toList();
    }

    /** Reuses a category the shop may have already created by hand with the same name, instead
     *  of failing on the categories.name UNIQUE constraint or creating a confusing duplicate. */
    private static int findOrCreateCategory(Connection connection, String name) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT id FROM categories WHERE name = ?")) {
            select.setString(1, name);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO categories (name, description, active) VALUES (?, NULL, 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Copies a bundled image (packaged under src/main/resources/seed-menu/) into the managed
     *  images directory, prefixed so it's obviously part of the starter menu if anyone looks. */
    private static String copySeedImage(String resourceName, Path imagesDir) throws IOException {
        String storedName = "seed_" + resourceName;
        try (InputStream in = MenuSeeder.class.getResourceAsStream("/seed-menu/" + resourceName)) {
            if (in == null) {
                throw new IOException("Bundled seed image missing from the jar: " + resourceName);
            }
            Files.copy(in, imagesDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        }
        return storedName;
    }

    private static void insertProduct(Connection connection, MenuSeedData.MenuItem item, int categoryId,
                                       String storedImageName) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO products (name, category_id, price, cost, stock, description, image_path, active) " +
                        "VALUES (?, ?, ?, NULL, ?, NULL, ?, 1)")) {
            insert.setString(1, item.name());
            insert.setInt(2, categoryId);
            insert.setLong(3, item.price());
            insert.setInt(4, STARTING_STOCK);
            insert.setString(5, storedImageName);
            insert.executeUpdate();
        }
    }
}
