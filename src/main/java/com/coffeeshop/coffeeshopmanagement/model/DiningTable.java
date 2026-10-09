package com.coffeeshop.coffeeshopmanagement.model;

public class DiningTable {
    private int id;
    private int tableNumber;
    private String name;
    private TableStatus status;
    private int capacity;
    private Integer currentOrderId;

    public DiningTable() {
    }

    public DiningTable(int id, int tableNumber, String name, TableStatus status, int capacity, Integer currentOrderId) {
        this.id = id;
        this.tableNumber = tableNumber;
        this.name = name;
        this.status = status;
        this.capacity = capacity;
        this.currentOrderId = currentOrderId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    public boolean isOccupied() {
        return status == TableStatus.OCCUPIED;
    }

    public int getCapacity() {
        return capacity;
    }

    public Integer getCurrentOrderId() {
        return currentOrderId;
    }

}
