package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the rows for the two data-driven dashboard cards (low stock, recent orders) shared by
 * the admin and employee dashboards. Reuses the inventory-row/-name/-description/-danger/
 * -warning style classes both dashboards' stylesheets already define, so the look is unchanged
 * from the original static mockup - only the content is now real.
 */
public final class DashboardWidgets {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private DashboardWidgets() {
    }

    public static void fillLowStock(VBox container, List<Product> products) {
        container.getChildren().clear();
        if (products.isEmpty()) {
            container.getChildren().add(styled(new Label("Tất cả sản phẩm đều đủ hàng."), "inventory-description"));
            return;
        }
        for (Product product : products) {
            int stock = product.getStock();
            String badge = stock == 0 ? "Hết hàng" : stock <= 5 ? "Sắp hết" : "Thấp";
            String badgeStyle = stock <= 5 ? "inventory-danger" : "inventory-warning";
            container.getChildren().add(row(
                    product.getName(),
                    "Còn " + stock + " (cảnh báo khi ≤ " + ProductDAO.LOW_STOCK_THRESHOLD + ")",
                    badge, badgeStyle));
        }
    }

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static void fillTodayOrders(VBox container, List<Order> orders) {
        container.getChildren().clear();
        if (orders == null || orders.isEmpty()) {
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new javafx.geometry.Insets(30, 10, 30, 10));
            Label icon = new Label("📋");
            icon.setStyle("-fx-font-size: 26px; -fx-opacity: 0.6;");
            Label msg = styled(new Label("Không có đơn hàng hôm nay"), "today-orders-empty");
            emptyBox.getChildren().addAll(icon, msg);
            container.getChildren().add(emptyBox);
            return;
        }

        for (Order order : orders) {
            VBox card = new VBox(5);
            card.getStyleClass().add("today-order-item");

            // Row 1: Order ID, Table info, Time
            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label idLabel = styled(new Label("#" + order.getId()), "today-order-id");
            String tableText = order.getTableNumber() != null ? "Bàn " + order.getTableNumber() : "Mang đi";
            Label tableLabel = styled(new Label("• " + tableText), "today-order-table");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            LocalDateTime when = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
            Label timeLabel = styled(new Label(when != null ? when.format(ORDER_TIME_FORMAT) : "-"), "today-order-time");
            topRow.getChildren().addAll(idLabel, tableLabel, spacer, timeLabel);

            // Row 2: Status badge and Total amount
            HBox bottomRow = new HBox(8);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            String statusText;
            String statusClass;
            switch (order.getStatus()) {
                case PAID -> {
                    statusText = "Đã thanh toán";
                    statusClass = "today-order-badge-paid";
                }
                case OPEN -> {
                    statusText = "Đang mở";
                    statusClass = "today-order-badge-open";
                }
                case CANCELLED -> {
                    statusText = "Đã hủy";
                    statusClass = "today-order-badge-cancelled";
                }
                default -> {
                    statusText = order.getStatus().name();
                    statusClass = "today-order-badge-open";
                }
            }
            Label statusBadge = styled(new Label(statusText), statusClass);

            Region bottomSpacer = new Region();
            HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

            Label totalLabel = styled(new Label(CurrencyUtil.format(order.getTotal())), "today-order-total");
            bottomRow.getChildren().addAll(statusBadge, bottomSpacer, totalLabel);

            card.getChildren().addAll(topRow, bottomRow);
            container.getChildren().add(card);
        }
    }

    public static void fillTableGrid(javafx.scene.layout.GridPane grid, List<com.coffeeshop.coffeeshopmanagement.model.DiningTable> tables, java.util.function.Consumer<com.coffeeshop.coffeeshopmanagement.model.DiningTable> onTableClick) {
        grid.getChildren().clear();
        for (com.coffeeshop.coffeeshopmanagement.model.DiningTable table : tables) {
            int num = table.getTableNumber();
            int col = (num - 1) % 3;
            int row = (num - 1) / 3;

            String statusText = table.isOccupied() ? "Đang sử dụng" : "Không hoạt động";
            javafx.scene.control.Button btn = new javafx.scene.control.Button(
                    "☕  " + table.getName() + "\n" + statusText);
            btn.getStyleClass().add(table.isOccupied() ? "table-button-occupied" : "table-button-empty");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMaxHeight(Double.MAX_VALUE);
            javafx.scene.layout.GridPane.setHgrow(btn, Priority.ALWAYS);
            javafx.scene.layout.GridPane.setVgrow(btn, Priority.ALWAYS);

            if (onTableClick != null) {
                btn.setOnAction(e -> onTableClick.accept(table));
            }
            grid.add(btn, col, row);
        }
    }

    private static HBox row(String title, String detail, String badge, String badgeStyle) {
        VBox text = new VBox(3,
                styled(new Label(title), "inventory-name"),
                styled(new Label(detail), "inventory-description"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(text, spacer, styled(new Label(badge), badgeStyle));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("inventory-row");
        return row;
    }

    private static Label styled(Label label, String styleClass) {
        label.getStyleClass().add(styleClass);
        return label;
    }
}
