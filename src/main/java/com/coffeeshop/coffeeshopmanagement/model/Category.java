package com.coffeeshop.coffeeshopmanagement.model;

public class Category {
    private int id;
    private String name;
    private String description;
    private boolean active;

    public Category() {
    }

    public Category(int id, String name, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        // Used by ComboBox cells elsewhere in the UI.
        return name;
    }
}
