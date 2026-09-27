package com.coffeeshop.coffeeshopmanagement.model;

import java.time.LocalDateTime;

/**
 * A login account. Every account is either linked to an Employee record (staff/admin
 * accounts created from Account Management) or a Customer record (self-registration
 * screen), never both.
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private Role role;
    private AccountStatus status;
    private Integer employeeId;
    private Integer customerId;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(int id, String username, String passwordHash, Role role, AccountStatus status,
                Integer employeeId, Integer customerId, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.employeeId = employeeId;
        this.customerId = customerId;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
