package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

public class Customer {
    private int id;
    private String fullName;
    private String phone;
    private String email;
    private int loyaltyPoints;
    private LocalDateTime createdAt;

    public Customer() {
    }

    public Customer(int id, String fullName, String phone, String email, int loyaltyPoints,
                     LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.loyaltyPoints = loyaltyPoints;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getLoyaltyPoints() { return loyaltyPoints; }
    public void setLoyaltyPoints(int loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        // Used by ChoiceDialog/ComboBox cells elsewhere in the UI (e.g. attaching a customer
        // to an order in the POS screen).
        return phone != null && !phone.isBlank() ? fullName + " - " + phone : fullName;
    }
}
