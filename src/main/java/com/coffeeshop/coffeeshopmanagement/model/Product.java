package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

public class Product {
    private int id;
    private String name;
    private Integer categoryId;
    private String categoryName; // populated by joins for display; not a DB column here
    private BigDecimal price;
    private BigDecimal cost;
    private int stock;
    private String description;
    private String imagePath;
    private boolean active;

    public Product() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
