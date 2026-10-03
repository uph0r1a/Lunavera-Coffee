package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
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

    public static void fillRecentOrders(VBox container, List<Order> orders) {
        container.getChildren().clear();
        if (orders.isEmpty()) {
            container.getChildren().add(styled(new Label("Chưa có đơn hàng nào."), "inventory-description"));
            return;
        }
        for (Order order : orders) {
            LocalDateTime when = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
            StringBuilder detail = new StringBuilder(when != null ? when.format(TIME_FORMAT) : "-");
            detail.append(" • ").append(order.getPaymentMethod() == PaymentMethod.CARD ? "Thẻ" : "Tiền mặt");
            if (order.getStatus() == OrderStatus.CANCELLED) {
                detail.append(" • Đã hủy");
            }
            container.getChildren().add(row(
                    "Đơn #" + order.getId(), detail.toString(),
                    CurrencyUtil.format(order.getTotal()), "inventory-name"));
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
