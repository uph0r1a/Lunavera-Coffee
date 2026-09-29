package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class CategoryDAOTest {

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private Category newCategory(String description, boolean active) {
        Category category = new Category();
        category.setName("test-cat-" + UUID.randomUUID());
        category.setDescription(description);
        category.setActive(active);
        return categoryDAO.insert(category);
    }

    @Test
    public void insertAssignsIdAndFindByIdReturnsSameData() {
        Category inserted = newCategory("mô tả test", true);
        assertTrue("insert should assign a positive id", inserted.getId() > 0);

        Optional<Category> found = categoryDAO.findById(inserted.getId());
        assertTrue(found.isPresent());
        assertEquals(inserted.getName(), found.get().getName());
        assertEquals("mô tả test", found.get().getDescription());
        assertTrue(found.get().isActive());
    }

    @Test
    public void updateChangesArePersisted() {
        Category category = newCategory("before", true);
        category.setDescription("after");
        category.setActive(false);
        categoryDAO.update(category);

        Category reloaded = categoryDAO.findById(category.getId()).orElseThrow();
        assertEquals("after", reloaded.getDescription());
        assertFalse(reloaded.isActive());
    }

    @Test
    public void findAllIncludesEveryInsertedCategory() {
        Category category = newCategory(null, true);
        boolean present = categoryDAO.findAll().stream().anyMatch(c -> c.getId() == category.getId());
        assertTrue("findAll() should include the category just inserted", present);
    }

    @Test
    public void duplicateNameIsRejectedWithFriendlyMessage() {
        Category first = newCategory(null, true);
        Category duplicate = new Category();
        duplicate.setName(first.getName()); // same name -> violates the UNIQUE constraint
        duplicate.setActive(true);
        try {
            categoryDAO.insert(duplicate);
            fail("inserting a second category with the same name should have thrown");
        } catch (DataAccessException e) {
            assertTrue("message should be the friendly Vietnamese one, not a raw SQL error",
                    e.getMessage().contains("Đã tồn tại"));
        }
    }

    @Test
    public void deleteIfUnusedRemovesACategoryWithNoProducts() {
        Category category = newCategory(null, true);
        boolean deleted = categoryDAO.deleteIfUnused(category.getId());
        assertTrue(deleted);
        assertTrue(categoryDAO.findById(category.getId()).isEmpty());
    }

    @Test
    public void deleteIfUnusedRefusesACategoryWithAProduct() {
        Category category = newCategory(null, true);
        com.coffeeshop.coffeeshopmanagement.model.Product product = new com.coffeeshop.coffeeshopmanagement.model.Product();
        product.setName("test-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new java.math.BigDecimal("10000"));
        product.setStock(1);
        product.setActive(true);
        productDAO.insert(product);

        boolean deleted = categoryDAO.deleteIfUnused(category.getId());
        assertFalse("a category with a product should not be deletable", deleted);
        assertTrue("the category should still be there", categoryDAO.findById(category.getId()).isPresent());
    }

    @Test
    public void countProductsByCategoryMatchesActualProductCount() {
        Category category = newCategory(null, true);
        for (int i = 0; i < 3; i++) {
            com.coffeeshop.coffeeshopmanagement.model.Product product = new com.coffeeshop.coffeeshopmanagement.model.Product();
            product.setName("test-product-" + UUID.randomUUID());
            product.setCategoryId(category.getId());
            product.setPrice(new java.math.BigDecimal("5000"));
            product.setStock(0);
            product.setActive(true);
            productDAO.insert(product);
        }
        Map<Integer, Integer> counts = categoryDAO.countProductsByCategory();
        assertEquals(Integer.valueOf(3), counts.get(category.getId()));
    }
}
