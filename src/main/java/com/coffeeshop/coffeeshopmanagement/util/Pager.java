package com.coffeeshop.coffeeshopmanagement.util;

import java.util.Collections;
import java.util.List;

/**
 * Small in-memory pagination helper shared by the admin table screens (categories,
 * accounts, ...), so each screen doesn't reimplement its own page-index bookkeeping.
 * Pages entirely in memory; fine for the list sizes a single coffee shop deals with.
 */
public class Pager<T> {

    private final int pageSize;
    private List<T> items = Collections.emptyList();
    private int currentPage = 1; // 1-based, matches the "1 / 2 / ›" buttons in the FXML

    public Pager(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setItems(List<T> items) {
        this.items = items != null ? items : Collections.emptyList();
        int max = getTotalPages();
        if (currentPage > max) {
            currentPage = max;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }
    }

    public List<T> getCurrentPageItems() {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        int from = (currentPage - 1) * pageSize;
        int to = Math.min(from + pageSize, items.size());
        if (from >= to) {
            return Collections.emptyList();
        }
        return items.subList(from, to);
    }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil(items.size() / (double) pageSize));
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void goToPage(int page) {
        currentPage = Math.max(1, Math.min(page, getTotalPages()));
    }

    public void nextPage() {
        goToPage(currentPage + 1);
    }

    public void previousPage() {
        goToPage(currentPage - 1);
    }

    public int getTotalCount() {
        return items.size();
    }

    public int getFromIndex() {
        return items.isEmpty() ? 0 : (currentPage - 1) * pageSize + 1;
    }

    public int getToIndex() {
        return Math.min(currentPage * pageSize, items.size());
    }
}
