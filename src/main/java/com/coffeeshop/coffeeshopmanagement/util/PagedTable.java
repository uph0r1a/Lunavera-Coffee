package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Glue between a {@link TableView}, a {@link Pager} and a {@link PageBar}: clickable column
 * headers sort and the footer pages, with one rule that matters - <b>sorting is applied to the
 * whole filtered list first and only then cut into pages</b>. (The stock TableView sort only
 * reorders the rows it was handed, i.e. the current page, so clicking a header would sort 8 rows
 * and leave the other pages untouched.) The TableView's own sorting is therefore switched off
 * via a sort policy and replaced by {@link #render()}.
 *
 * <p>Usage: construct once in the controller's init, call {@link #setItems} every time the
 * filtered list changes. Text columns sort with the Vietnamese collator; a column whose cell text
 * doesn't sort correctly (dates, "1.200đ") can register a typed key with {@link #sortKey}.
 */
public final class PagedTable<T> {

    public static final int[] DEFAULT_SIZES = {10, 20, 50, 100};
    private static final Collator COLLATOR = Collator.getInstance(Locale.forLanguageTag("vi"));

    private final TableView<T> table;
    private final Pager<T> pager;
    private final PageBar bar;
    private final Map<TableColumn<T, ?>, Function<T, ?>> sortKeys = new HashMap<>();
    private List<T> source = List.of();
    private boolean rendering;

    public PagedTable(TableView<T> table, Pane barHost, int defaultSize) {
        this.table = table;
        this.pager = new Pager<>(defaultSize);
        this.bar = new PageBar(defaultSize, DEFAULT_SIZES);
        if (barHost != null) {
            barHost.getChildren().add(bar);
        }
        bar.setOnChange(this::render);
        table.setSortPolicy(tv -> {
            if (!rendering) {
                render();
            }
            return true; // sorted by render(), never by the TableView itself
        });
    }

    /** Sort this column by a typed key instead of its displayed text. */
    public PagedTable<T> sortKey(TableColumn<T, ?> column, Function<T, ?> key) {
        sortKeys.put(column, key);
        return this;
    }

    /** Index / action columns: sorting them is meaningless. */
    @SafeVarargs
    public final PagedTable<T> unsortable(TableColumn<T, ?>... columns) {
        for (TableColumn<T, ?> column : columns) {
            if (column != null) column.setSortable(false);
        }
        return this;
    }

    /** Supply the filtered (unsorted, unpaged) rows; keeps the user's page if it still exists. */
    public void setItems(List<T> filtered) {
        this.source = filtered != null ? filtered : List.of();
        render();
    }

    public void firstPage() {
        bar.setCurrentPage(1);
    }

    public int getTotalCount() {
        return source.size();
    }

    /** 1-based number of the first row on the current page (0 when empty) - for STT columns. */
    public int getFromIndex() {
        return pager.getFromIndex();
    }

    public int getToIndex() {
        return pager.getToIndex();
    }

    private void render() {
        if (rendering) {
            return;
        }
        rendering = true;
        try {
            List<T> sorted = new ArrayList<>(source);
            sorted.sort(buildComparator()); // stable: equal keys keep their DB order
            bar.setTotalItems(sorted.size());
            pager.setPageSize(bar.getPageSize());
            pager.setItems(sorted);
            pager.goToPage(bar.getCurrentPage());
            table.getItems().setAll(pager.getCurrentPageItems());
            table.refresh();
        } finally {
            rendering = false;
        }
    }

    private Comparator<T> buildComparator() {
        Comparator<T> result = null;
        for (TableColumn<T, ?> column : table.getSortOrder()) {
            Comparator<T> next = columnComparator(column);
            if (column.getSortType() == TableColumn.SortType.DESCENDING) {
                next = next.reversed();
            }
            result = result == null ? next : result.thenComparing(next);
        }
        return result != null ? result : (a, b) -> 0;
    }

    private Comparator<T> columnComparator(TableColumn<T, ?> column) {
        Function<T, ?> key = sortKeys.get(column);
        return (a, b) -> compareValues(
                key != null ? key.apply(a) : column.getCellData(a),
                key != null ? key.apply(b) : column.getCellData(b));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static int compareValues(Object x, Object y) {
        if (x == null || y == null) {
            return x == y ? 0 : x == null ? -1 : 1;
        }
        if (x instanceof String sx && y instanceof String sy) {
            return COLLATOR.compare(sx, sy);
        }
        if (x instanceof Comparable cx && x.getClass().isInstance(y)) {
            return cx.compareTo(y);
        }
        if (x instanceof Number nx && y instanceof Number ny) {
            return Double.compare(nx.doubleValue(), ny.doubleValue());
        }
        return COLLATOR.compare(String.valueOf(x), String.valueOf(y));
    }
}
