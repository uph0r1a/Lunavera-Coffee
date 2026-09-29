package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Order / invoice history, opened from the POS screen's header. Built in code as its own plain
 * (non-modal) window rather than a new FXML screen: it needs no sidebar of its own, and a
 * non-modal window sidesteps the dialog-modality problems seen on Linux (progress.md, Sessions
 * 4-5). Lists past orders, re-opens any receipt, and lets an admin cancel (refund) a paid order.
 */
public final class OrderHistoryWindow {

    private static final int HISTORY_LIMIT = 500;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final String ALL_STATUSES = "Tất cả trạng thái";
    private static final String STATUS_PAID = "Đã thanh toán";
    private static final String STATUS_CANCELLED = "Đã hủy";

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableView<OrderSummary> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final Label countLabel = new Label();
    private final Button viewButton = new Button("Xem hóa đơn");
    private final Button cancelButton = new Button("Hủy đơn (hoàn tiền)");
    private List<OrderSummary> all = List.of();

    private OrderHistoryWindow() {
    }

    public static void show() {
        new OrderHistoryWindow().open();
    }

    private void open() {
        buildColumns();

        searchField.setPromptText("Tìm theo mã đơn, nhân viên, khách hàng...");
        searchField.setPrefWidth(340);
        searchField.textProperty().addListener((obs, old, value) -> applyFilter());
        statusFilter.getItems().setAll(ALL_STATUSES, STATUS_PAID, STATUS_CANCELLED);
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> applyFilter());

        viewButton.setDisable(true);
        cancelButton.setDisable(true);
        viewButton.setOnAction(e -> viewSelectedReceipt());
        cancelButton.setOnAction(e -> cancelSelectedOrder());
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtons(selected));
        table.setPlaceholder(new Label("Chưa có đơn hàng nào"));
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) viewSelectedReceipt();
        });

        fromPicker.setPromptText("Từ ngày");
        toPicker.setPromptText("Đến ngày");
        fromPicker.setOnAction(e -> applyFilter());
        toPicker.setOnAction(e -> applyFilter());
        Button clearDatesButton = new Button("Xóa lọc ngày");
        clearDatesButton.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            applyFilter();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, searchField, statusFilter, fromPicker, toPicker, clearDatesButton, spacer, countLabel);
        HBox bottom = new HBox(10, viewButton, cancelButton);
        VBox root = new VBox(12, top, table, bottom);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Lịch sử đơn hàng");
        stage.setScene(new Scene(root, 960, 560));
        stage.show();

        reload();
    }

    private void buildColumns() {
        table.getColumns().add(column("Mã đơn", 80, s -> "#" + s.order().getId()));
        table.getColumns().add(column("Thời gian", 150, s -> s.order().getOrderDate() != null
                ? s.order().getOrderDate().format(TIME_FORMAT) : "-"));
        table.getColumns().add(column("Nhân viên", 150, s -> orDash(s.employeeName())));
        table.getColumns().add(column("Khách hàng", 150, s -> s.customerName() != null ? s.customerName() : "Khách lẻ"));
        table.getColumns().add(column("Thanh toán", 100, s -> s.order().getPaymentMethod() == PaymentMethod.CARD
                ? "Thẻ" : s.order().getPaymentMethod() == PaymentMethod.CASH ? "Tiền mặt" : "-"));
        table.getColumns().add(column("Tổng tiền", 120, s -> CurrencyUtil.format(s.order().getTotal())));
        table.getColumns().add(column("Trạng thái", 120, s -> statusLabel(s.order().getStatus())));
    }

    private TableColumn<OrderSummary, String> column(String title, double width,
                                                     java.util.function.Function<OrderSummary, String> text) {
        TableColumn<OrderSummary, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }

    private static String orDash(String value) {
        return value != null ? value : "-";
    }

    private static String statusLabel(OrderStatus status) {
        return switch (status) {
            case PAID -> STATUS_PAID;
            case CANCELLED -> STATUS_CANCELLED;
            case OPEN -> "Đang mở";
        };
    }

    private void reload() {
        Async.run(
                () -> orderDAO.findHistory(HISTORY_LIMIT),
                result -> { all = result; applyFilter(); },
                error -> AlertUtil.error("Lỗi tải dữ liệu", "Không thể tải lịch sử đơn hàng: " + error.getMessage())
        );
    }

    private void applyFilter() {
        String keyword = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String statusChoice = statusFilter.getValue();
        java.time.LocalDate from = fromPicker.getValue();
        java.time.LocalDate to = toPicker.getValue();
        List<OrderSummary> filtered = all.stream()
                .filter(s -> statusChoice == null || ALL_STATUSES.equals(statusChoice)
                        || statusLabel(s.order().getStatus()).equals(statusChoice))
                .filter(s -> from == null || (s.order().getOrderDate() != null
                        && !s.order().getOrderDate().toLocalDate().isBefore(from)))
                .filter(s -> to == null || (s.order().getOrderDate() != null
                        && !s.order().getOrderDate().toLocalDate().isAfter(to)))
                .filter(s -> keyword.isEmpty()
                        || String.valueOf(s.order().getId()).contains(keyword.replace("#", ""))
                        || orDash(s.employeeName()).toLowerCase().contains(keyword)
                        || (s.customerName() != null && s.customerName().toLowerCase().contains(keyword)))
                .collect(Collectors.toList());
        table.getItems().setAll(filtered);
        countLabel.setText(filtered.size() + " đơn" + (all.size() >= HISTORY_LIMIT
                ? " (trong " + HISTORY_LIMIT + " đơn mới nhất)" : ""));
    }

    private void updateButtons(OrderSummary selected) {
        viewButton.setDisable(selected == null);
        cancelButton.setDisable(selected == null || !Session.isAdmin()
                || selected.order().getStatus() != OrderStatus.PAID);
    }

    private void viewSelectedReceipt() {
        OrderSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Async.run(
                () -> orderDAO.findItemsByOrderId(selected.order().getId()),
                (List<OrderItem> items) -> {
                    HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                            "/fxml/donthanhtoan.fxml", "Hóa đơn #" + selected.order().getId());
                    if (controller != null) {
                        controller.setInvoiceData(selected.order(), items,
                                selected.employeeName(), selected.customerName());
                    }
                },
                error -> AlertUtil.error("Lỗi", "Không thể tải chi tiết đơn hàng: " + error.getMessage())
        );
    }

    private void cancelSelectedOrder() {
        OrderSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chỉ quản trị viên mới có thể hủy đơn hàng.");
            return;
        }
        boolean confirmed = AlertUtil.confirm("Hủy đơn hàng",
                "Hủy đơn #" + selected.order().getId() + " (" + CurrencyUtil.format(selected.order().getTotal()) +
                        ")?\nTồn kho sẽ được hoàn lại và điểm tích lũy của khách (nếu có) sẽ bị trừ. " +
                        "Hành động này không thể hoàn tác.");
        if (!confirmed) return;
        cancelButton.setDisable(true);
        Async.run(
                () -> { orderDAO.cancelPaidOrder(selected.order().getId()); return Boolean.TRUE; },
                done -> reload(),
                error -> {
                    AlertUtil.error("Không thể hủy đơn", error.getMessage());
                    reload();
                }
        );
    }
}
