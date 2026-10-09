package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

/**
 * The product name and unit price are copied ("snapshotted") onto the order item at the
 * time of sale, so that editing or deactivating a product later never changes the
 * historical record of what a past order actually contained and charged.
 */
public class OrderItem {
    private int id;
    private Integer productId;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItem() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }


    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
