package com.coffeeshop.coffeeshopmanagement.util;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * The footer shared by every paged table: a "rows per page" box plus previous / numbered / next
 * page buttons. It only holds UI state (page size, current page, how many items there are);
 * whoever owns the data reacts in {@link #setOnChange}. Pages beyond a handful collapse into
 * "1 ... 4 5 6 ... 20" so the bar never grows with the data.
 */
public class PageBar extends HBox {

    public static final String ALL_LABEL = "Tất cả";
    private static final int ALL = Integer.MAX_VALUE;

    private final ComboBox<String> sizeBox = new ComboBox<>();
    private final HBox pagesBox = new HBox(5);
    private int pageSize;
    private int currentPage = 1;
    private int totalItems;
    private boolean updating;
    private Runnable onChange = () -> { };

    public PageBar(int defaultSize, int... sizes) {
        super(8);
        setAlignment(Pos.CENTER_RIGHT);
        for (int size : sizes) {
            sizeBox.getItems().add(String.valueOf(size));
        }
        sizeBox.getItems().add(ALL_LABEL);
        sizeBox.setValue(String.valueOf(defaultSize));
        sizeBox.getStyleClass().add("filter-combo");
        pageSize = defaultSize;
        sizeBox.setOnAction(e -> {
            if (updating) return;
            pageSize = parse(sizeBox.getValue());
            currentPage = 1;
            rebuild();
            onChange.run();
        });
        Label rowsLabel = new Label("Số dòng:");
        rowsLabel.getStyleClass().add("table-footer");
        pagesBox.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(rowsLabel, sizeBox, pagesBox);
        rebuild();
    }

    private static int parse(String value) {
        if (value == null || ALL_LABEL.equals(value)) return ALL;
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return ALL;
        }
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange != null ? onChange : () -> { };
    }

    /** Rows per page; {@code Integer.MAX_VALUE} means "Tất cả" (everything on one page). */
    public int getPageSize() {
        return pageSize;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /** Moves to a page without firing the change callback (the caller is already reloading). */
    public void setCurrentPage(int page) {
        currentPage = Math.max(1, Math.min(page, totalPages()));
        rebuild();
    }

    /** Tells the bar how many items match the current filter; re-clamps the page and redraws. */
    public void setTotalItems(int totalItems) {
        this.totalItems = Math.max(0, totalItems);
        currentPage = Math.max(1, Math.min(currentPage, totalPages()));
        rebuild();
    }

    public int totalPages() {
        if (pageSize == ALL) return 1;
        return Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
    }

    private void go(int page) {
        int target = Math.max(1, Math.min(page, totalPages()));
        if (target == currentPage) return;
        currentPage = target;
        rebuild();
        onChange.run();
    }

    private void rebuild() {
        pagesBox.getChildren().clear();
        int pages = totalPages();

        Button previous = pageButton("‹", false);
        previous.setDisable(currentPage <= 1);
        previous.setOnAction(e -> go(currentPage - 1));
        pagesBox.getChildren().add(previous);

        int last = 0;
        for (int page = 1; page <= pages; page++) {
            boolean show = page == 1 || page == pages || Math.abs(page - currentPage) <= 1
                    || (currentPage <= 3 && page <= 4) || (currentPage >= pages - 2 && page >= pages - 3);
            if (!show) continue;
            if (last != 0 && page - last > 1) {
                Label dots = new Label("…");
                dots.getStyleClass().add("table-footer");
                pagesBox.getChildren().add(dots);
            }
            final int target = page;
            Button button = pageButton(String.valueOf(page), page == currentPage);
            button.setOnAction(e -> go(target));
            pagesBox.getChildren().add(button);
            last = page;
        }

        Button next = pageButton("›", false);
        next.setDisable(currentPage >= pages);
        next.setOnAction(e -> go(currentPage + 1));
        pagesBox.getChildren().add(next);
    }

    private static Button pageButton(String text, boolean active) {
        Button button = new Button(text);
        button.setMnemonicParsing(false);
        button.getStyleClass().add("page-button");
        if (active) {
            button.getStyleClass().add("page-button-active");
        }
        return button;
    }
}
