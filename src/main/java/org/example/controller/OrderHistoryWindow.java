

package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.HistoryFilter;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.PageBar;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
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

/**
 * Order / invoice history, opened from the POS screen's header. Built in code as its own plain
 * (non-modal) window rather than a new FXML screen: it needs no sidebar of its own, and a
 * non-modal window sidesteps the dialog-modality problems seen on Linux (progress.md, Sessions
 * 4-5). Lists past orders, re-opens any receipt, and lets an admin cancel (refund) a paid order.
 *
 * Filtering and paging both happen in SQL now (progress.md, "order-history paging" session) -
 * the previous version loaded up to 500 rows into memory and filtered client-side, so a date
 * range further back than the most recent 500 orders silently showed nothing even when matching
 * orders existed. There is no upper bound on total history size anymore.
 */
public final class OrderHistoryWindow {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final String ALL_STATUSES = "Tất cả trạng thái";
    private static final String STATUS_PAID = "Đã thanh toán";
    private static final String STATUS_CANCELLED = "Đã hủy";

    private record HistoryPage(List<OrderSummary> rows, int totalCount) {
    }

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableView<OrderSummary> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final Label countLabel = new Label();
    private final PageBar pageBar = new PageBar(50, 20, 50, 100, 200);
    private final java.util.Map<TableColumn<OrderSummary, ?>, OrderDAO.HistorySortKey> sortKeys = new java.util.HashMap<>();
    private OrderDAO.HistorySort sort = OrderDAO.HistorySort.NEWEST_FIRST;
    private final Button viewButton = new Button("Xem hóa đơn");
    private final Button cancelButton = new Button("Hủy đơn (hoàn tiền)");
    private int totalCount = 0;

    private OrderHistoryWindow() {
    }

    public static void show() {
        new OrderHistoryWindow().open();
    }

    private void open() {
        buildColumns();

        searchField.setPromptText("Tìm theo mã đơn, nhân viên, khách hàng...");
        searchField.setPrefWidth(340);
        TextLengthLimiter.limit(searchField, TextLengthLimiter.SEARCH_MAX);
        searchField.textProperty().addListener((obs, old, value) -> resetAndReload());
        statusFilter.getItems().setAll(ALL_STATUSES, STATUS_PAID, STATUS_CANCELLED);
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> resetAndReload());

        viewButton.setDisable(true);
        cancelButton.setDisable(true);
        viewButton.setOnAction(e -> viewSelectedReceipt());
        cancelButton.setOnAction(e -> cancelSelectedOrder());
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtons(selected));
        table.setPlaceholder(new Label("Không có đơn hàng nào khớp"));
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) viewSelectedReceipt();
        });

        fromPicker.setPromptText("Từ ngày");
        toPicker.setPromptText("Đến ngày");
        fromPicker.setOnAction(e -> resetAndReload());
        toPicker.setOnAction(e -> resetAndReload());
        Button clearDatesButton = new Button("Xóa lọc ngày");
        clearDatesButton.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            resetAndReload();
        });

        pageBar.setOnChange(this::reload);
        // Sorting is done by the database over ALL matching orders, then paged - the TableView's
        // own sort would only reorder the rows currently on screen.
        table.setSortPolicy(tv -> {
            TableColumn<OrderSummary, ?> first = tv.getSortOrder().isEmpty() ? null : tv.getSortOrder().get(0);
            OrderDAO.HistorySortKey key = first != null ? sortKeys.get(first) : null;
            OrderDAO.HistorySort next = key == null ? OrderDAO.HistorySort.NEWEST_FIRST
                    : new OrderDAO.HistorySort(key, first.getSortType() == TableColumn.SortType.DESCENDING);
            if (!next.equals(sort)) {
                sort = next;
                pageBar.setCurrentPage(1);
                reload();
            }
            return true;
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, searchField, statusFilter, fromPicker, toPicker, clearDatesButton, spacer, countLabel);
        HBox bottom = new HBox(10, viewButton, cancelButton, new Region(), pageBar);
        HBox.setHgrow(bottom.getChildren().get(2), Priority.ALWAYS);
        VBox root = new VBox(12, top, table, bottom);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Lịch sử đơn hàng");
        stage.setScene(new Scene(root, 960, 600));
        AlertUtil.closeOnEscape(stage.getScene());
        stage.show();

        reload();
    }

    private void buildColumns() {
        addColumn(OrderDAO.HistorySortKey.ID, column("Mã đơn", 80, s -> "#" + s.order().getId()));
        addColumn(OrderDAO.HistorySortKey.DATE, column("Thời gian", 150, s -> s.order().getOrderDate() != null
                ? s.order().getOrderDate().format(TIME_FORMAT) : "-"));
        addColumn(OrderDAO.HistorySortKey.EMPLOYEE, column("Nhân viên", 150, s -> orDash(s.employeeName())));
        addColumn(OrderDAO.HistorySortKey.CUSTOMER, column("Khách hàng", 150, s -> s.customerName() != null ? s.customerName() : "Khách lẻ"));
        addColumn(OrderDAO.HistorySortKey.PAYMENT, column("Thanh toán", 100, s -> s.order().getPaymentMethod() == PaymentMethod.CARD
                ? "Thẻ" : s.order().getPaymentMethod() == PaymentMethod.CASH ? "Tiền mặt" : "-"));
        addColumn(OrderDAO.HistorySortKey.TOTAL, column("Tổng tiền", 120, s -> CurrencyUtil.format(s.order().getTotal())));
        addColumn(OrderDAO.HistorySortKey.STATUS, column("Trạng thái", 120, s -> statusLabel(s.order().getStatus())));
    }

    private void addColumn(OrderDAO.HistorySortKey key, TableColumn<OrderSummary, String> column) {
        sortKeys.put(column, key);
        table.getColumns().add(column);
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

    private HistoryFilter currentFilter() {
        String statusChoice = statusFilter.getValue();
        OrderStatus status = STATUS_PAID.equals(statusChoice) ? OrderStatus.PAID
                : STATUS_CANCELLED.equals(statusChoice) ? OrderStatus.CANCELLED : null;
        return new HistoryFilter(searchField.getText(), status, fromPicker.getValue(), toPicker.getValue());
    }

    /** A search/status/date change invalidates whatever page we were on (there may not even be
     *  that many pages of the new, narrower result set), so always jump back to page 1. */
    private void resetAndReload() {
        pageBar.setCurrentPage(1);
        reload();
    }

    private void reload() {
        HistoryFilter filter = currentFilter();
        OrderDAO.HistorySort order = sort;
        int pageSize = pageBar.getPageSize();
        int page = pageBar.getCurrentPage();
        table.setDisable(true);
        Async.run(
                () -> new HistoryPage(
                        orderDAO.findHistoryPage(filter, order, pageSize, (int) Math.min(Integer.MAX_VALUE, (long) (page - 1) * pageSize)),
                        orderDAO.countHistory(filter)),
                result -> {
                    table.setDisable(false);
                    table.getItems().setAll(result.rows());
                    totalCount = result.totalCount();
                    countLabel.setText(totalCount + " đơn khớp bộ lọc");
                    pageBar.setTotalItems(totalCount);
                    if (pageBar.getCurrentPage() != page) {
                        reload(); // the page we asked for no longer exists (e.g. after a cancel) - load the clamped one
                    }
                },
                error -> {
                    table.setDisable(false);
                    AlertUtil.error("Lỗi tải dữ liệu", "Không thể tải lịch sử đơn hàng: " + error.getMessage());
                }
        );
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

