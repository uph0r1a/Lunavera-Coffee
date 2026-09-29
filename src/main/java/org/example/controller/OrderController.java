package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Backs the POS/order-creation screen (quanlydonhang.fxml) - a dine-in table grid + a live
 * cart + checkout. Deliberately its own class rather than a 7th screen folded into
 * AdminController: that class was already ~1300 lines across 6 screens and had already needed
 * 5 field/method-name collision renames for the 6th (see progress.md, Session 6) - adding the
 * single largest remaining screen on top of that was the point to stop growing the shared
 * class further. The ~9 simple one-line navigation methods below are duplicated from
 * AdminController rather than factored into a shared base class; that's a deliberate, minor,
 * low-risk tradeoff for now (see progress.md) - worth revisiting if a third controller needs
 * the same methods.
 *
 * ⚠️ Decision (see progress.md): there is no dine-in table/seating entity in the schema. The
 * 16-button table grid in the FXML is kept exactly as designed, but table selection is treated
 * as decorative, in-memory-only UI state - it is never persisted and never blocks starting an
 * order. The real, persisted entity this screen builds and saves is the Order/OrderItem cart.
 */
public class OrderController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    @FXML private Label adminNameLabel;
    @FXML private Label adminRoleLabel;

    @FXML private Button table1Button;
    @FXML private Button table2Button;
    @FXML private Button table3Button;
    @FXML private Button table4Button;
    @FXML private Button table5Button;
    @FXML private Button table6Button;
    @FXML private Button table7Button;
    @FXML private Button table8Button;
    @FXML private Button table9Button;
    @FXML private Button table10Button;
    @FXML private Button table11Button;
    @FXML private Button table12Button;
    @FXML private Button table13Button;
    @FXML private Button table14Button;
    @FXML private Button table15Button;
    @FXML private Button table16Button;
    @FXML private Label selectedTableLabel;
    @FXML private Label selectedTableStatusLabel;
    @FXML private Label selectedTableCapacityLabel;
    @FXML private Button startOrderButton;
    @FXML private Button tableDetailButton;

    @FXML private Label orderStatusLabel;
    @FXML private Label orderIdLabel;
    @FXML private Label customerLabel;
    @FXML private Label orderTableLabel;
    @FXML private Label orderTimeLabel;

    @FXML private TableView<OrderItem> orderDetailTable;
    @FXML private TableColumn<OrderItem, Number> detailIndexColumn;
    @FXML private TableColumn<OrderItem, String> productNameColumn;
    @FXML private TableColumn<OrderItem, Void> quantityColumn;
    @FXML private TableColumn<OrderItem, String> unitPriceColumn;
    @FXML private TableColumn<OrderItem, String> subtotalColumn;

    @FXML private TextField productSearchField;
    @FXML private Button addProductButton;
    @FXML private Label totalAmountLabel;
    @FXML private Button printOrderButton;
    @FXML private Button orderHistoryButton;
    @FXML private Button editOrderButton;
    @FXML private Button paymentButton;

    private final ObservableList<OrderItem> cart = FXCollections.observableArrayList();
    private Map<Button, Integer> tableButtons;
    private List<Product> allProducts = List.of();
    private Integer selectedTableNumber;
    private boolean orderInProgress;
    private Customer attachedCustomer;
    private Order lastCompletedOrder;
    private List<OrderItem> lastCompletedItems = List.of();

    private record PaymentResult(PaymentMethod method, BigDecimal discount, BigDecimal total) {
    }

    // =================================================================== initialize

    @FXML
    private void initialize() {
        javafx.application.Platform.runLater(this::installCloseGuard);
        if (adminNameLabel != null) {
            adminNameLabel.setText(Session.getDisplayName());
        }
        if (adminRoleLabel != null) {
            adminRoleLabel.setText(Session.isAdmin() ? "Quản trị viên" : "Nhân viên");
        }

        tableButtons = new LinkedHashMap<>();
        tableButtons.put(table1Button, 1);
        tableButtons.put(table2Button, 2);
        tableButtons.put(table3Button, 3);
        tableButtons.put(table4Button, 4);
        tableButtons.put(table5Button, 5);
        tableButtons.put(table6Button, 6);
        tableButtons.put(table7Button, 7);
        tableButtons.put(table8Button, 8);
        tableButtons.put(table9Button, 9);
        tableButtons.put(table10Button, 10);
        tableButtons.put(table11Button, 11);
        tableButtons.put(table12Button, 12);
        tableButtons.put(table13Button, 13);
        tableButtons.put(table14Button, 14);
        tableButtons.put(table15Button, 15);
        tableButtons.put(table16Button, 16);
        // The FXML ships with table2Button statically marked "selected" as mockup content;
        // clear that so the screen doesn't claim a table is picked before the user picks one.
        highlightSelectedTableButton(-1);

        detailIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                orderDetailTable.getItems().indexOf(data.getValue()) + 1));
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellFactory(col -> quantityCell());
        unitPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));

        loadProducts();
        refreshCart();
    }

    private void loadProducts() {
        try {
            allProducts = productDAO.findAll();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", "Không thể tải danh sách sản phẩm: " + e.getMessage());
        }
    }

    /** Renders "[-]  qty  [+]" inline in the quantity column - narrower than a dedicated
     *  action column would need, and doubles as the "remove an item" control: decrementing
     *  past 1 removes the line, matching common POS UX rather than needing a separate button
     *  the table's column widths don't really have room for. */
    private TableCell<OrderItem, Void> quantityCell() {
        return new TableCell<>() {
            private final Button minus = new Button("−");
            private final Button plus = new Button("+");
            private final Label qtyLabel = new Label();
            private final HBox box = new HBox(4, minus, qtyLabel, plus);

            {
                box.setAlignment(Pos.CENTER);
                minus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), -1));
                plus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), 1));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    qtyLabel.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
                    setGraphic(box);
                }
            }
        };
    }

    // =================================================================== Table selection (decorative only)

    @FXML
    public void handleTableClick(ActionEvent event) {
        Integer number = tableButtons.get(event.getSource());
        if (number != null) {
            selectTable(number);
        }
    }

    private void selectTable(int number) {
        selectedTableNumber = number;
        selectedTableLabel.setText("Bàn " + number);
        selectedTableStatusLabel.setText("Đã chọn");
        highlightSelectedTableButton(number);
    }

    private void highlightSelectedTableButton(int number) {
        for (Map.Entry<Button, Integer> entry : tableButtons.entrySet()) {
            entry.getKey().getStyleClass().remove("table-button-selected");
            if (entry.getValue() == number) {
                entry.getKey().getStyleClass().add("table-button-selected");
            }
        }
    }

    @FXML
    public void handleTableDetail() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để xem chi tiết.");
            return;
        }
        AlertUtil.info("Chi tiết " + selectedTableLabel.getText(), selectedTableCapacityLabel.getText());
    }

    @FXML
    public void handleStartOrder() {
        if (selectedTableNumber == null) {
            AlertUtil.warning("Chưa chọn bàn", "Vui lòng chọn một bàn trước khi bắt đầu đặt đơn.");
            return;
        }
        if (orderInProgress && !cart.isEmpty()) {
            boolean confirmed = AlertUtil.confirm("Bắt đầu đơn mới",
                    "Đơn hàng hiện tại chưa được thanh toán và sẽ bị hủy. Tiếp tục?");
            if (!confirmed) {
                return;
            }
        }
        orderInProgress = true;
        cart.clear();
        attachedCustomer = null;
        orderStatusLabel.setText("Đang phục vụ");
        orderIdLabel.setText("Đơn mới");
        orderTableLabel.setText(selectedTableLabel.getText());
        orderTimeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
        customerLabel.setText("Khách vãng lai");
        refreshCart();
    }

    // =================================================================== Cart

    @FXML
    public void handleAddProductToCart() {
        if (!orderInProgress) {
            AlertUtil.warning("Chưa bắt đầu đơn hàng", "Vui lòng chọn bàn và bấm \"Bắt đầu đặt đơn\" trước.");
            return;
        }
        String keyword = productSearchField.getText() == null ? "" : productSearchField.getText().trim();
        if (keyword.isEmpty()) {
            AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập tên món cần thêm.");
            return;
        }
        String lowerKeyword = keyword.toLowerCase();
        List<Product> matches = allProducts.stream()
                .filter(Product::isActive)
                .filter(p -> p.getName().toLowerCase().contains(lowerKeyword))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            AlertUtil.warning("Không tìm thấy", "Không tìm thấy sản phẩm đang bán khớp với \"" + keyword + "\".");
            return;
        }
        Product chosen;
        if (matches.size() == 1) {
            chosen = matches.get(0);
        } else {
            ChoiceDialog<Product> dialog = new ChoiceDialog<>(matches.get(0), matches);
            dialog.setTitle("Chọn sản phẩm");
            dialog.setHeaderText(null);
            dialog.setContentText("Có nhiều sản phẩm khớp, vui lòng chọn:");
            AlertUtil.configure(dialog);
            Optional<Product> result = dialog.showAndWait();
            if (result.isEmpty()) {
                return;
            }
            chosen = result.get();
        }
        addToCart(chosen);
        productSearchField.clear();
    }

    private void addToCart(Product product) {
        Optional<OrderItem> existingLine = cart.stream()
                .filter(item -> item.getProductId() != null && item.getProductId() == product.getId())
                .findFirst();
        int currentQty = existingLine.map(OrderItem::getQuantity).orElse(0);
        int newQty = currentQty + 1;
        if (newQty > product.getStock()) {
            AlertUtil.warning("Không đủ hàng",
                    "\"" + product.getName() + "\" chỉ còn " + product.getStock() + " trong kho.");
            return;
        }
        if (existingLine.isPresent()) {
            setLineQuantity(existingLine.get(), newQty);
        } else {
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(1);
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(product.getPrice());
            cart.add(item);
            refreshCart();
        }
    }

    private void adjustQuantity(OrderItem line, int delta) {
        int newQty = line.getQuantity() + delta;
        if (delta > 0) {
            Product product = findProductById(line.getProductId());
            if (product != null && newQty > product.getStock()) {
                AlertUtil.warning("Không đủ hàng",
                        "\"" + line.getProductName() + "\" chỉ còn " + product.getStock() + " trong kho.");
                return;
            }
        }
        setLineQuantity(line, newQty);
    }

    private void setLineQuantity(OrderItem item, int newQty) {
        if (newQty <= 0) {
            cart.remove(item);
        } else {
            item.setQuantity(newQty);
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newQty)));
        }
        refreshCart();
    }

    private Product findProductById(Integer id) {
        if (id == null) {
            return null;
        }
        return allProducts.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    private BigDecimal cartSubtotal() {
        return cart.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void refreshCart() {
        orderDetailTable.getItems().setAll(cart);
        totalAmountLabel.setText(CurrencyUtil.format(cartSubtotal()));
    }

    @FXML
    public void handleEditOrder() {
        if (!orderInProgress) {
            AlertUtil.warning("Chưa có đơn hàng", "Vui lòng bắt đầu đặt đơn trước.");
            return;
        }
        List<Customer> customers;
        try {
            customers = customerDAO.findAll();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", e.getMessage());
            return;
        }
        if (customers.isEmpty()) {
            AlertUtil.info("Chưa có khách hàng", "Chưa có khách hàng nào được đăng ký trong hệ thống.");
            return;
        }
        ChoiceDialog<Customer> dialog = new ChoiceDialog<>(attachedCustomer, customers);
        dialog.setTitle("Gắn khách hàng vào đơn");
        dialog.setHeaderText(null);
        dialog.setContentText("Chọn khách hàng cho đơn này (Cancel để giữ Khách vãng lai):");
        AlertUtil.configure(dialog);
        dialog.showAndWait().ifPresent(customer -> {
            attachedCustomer = customer;
            customerLabel.setText(customer.getFullName());
        });
    }

    // =================================================================== Payment / checkout

    @FXML
    public void handlePayment() {
        if (!orderInProgress) {
            AlertUtil.warning("Chưa có đơn hàng", "Vui lòng bắt đầu đặt đơn trước khi thanh toán.");
            return;
        }
        if (cart.isEmpty()) {
            AlertUtil.warning("Đơn hàng trống", "Vui lòng thêm ít nhất một món trước khi thanh toán.");
            return;
        }
        // An account locked mid-shift must not be able to complete a sale.
        if (!SessionGuard.validateNow()) {
            cart.clear();
            SessionGuard.forceLogout();
            return;
        }
        openPaymentDialog();
    }

    private void openPaymentDialog() {
        BigDecimal subtotal = cartSubtotal();

        Dialog<PaymentResult> dialog = new Dialog<>();
        dialog.setTitle("Thanh toán");
        AlertUtil.configure(dialog);
        ButtonType confirmButtonType = new ButtonType("Xác nhận thanh toán", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        ToggleGroup methodGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton("Tiền mặt");
        cashRadio.setToggleGroup(methodGroup);
        cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton("Thẻ");
        cardRadio.setToggleGroup(methodGroup);

        TextField discountField = new TextField("0");
        TextField cashReceivedField = new TextField();
        cashReceivedField.disableProperty().bind(cardRadio.selectedProperty());
        Label totalPreviewLabel = new Label(CurrencyUtil.format(subtotal));
        Label changePreviewLabel = new Label(CurrencyUtil.format(BigDecimal.ZERO));

        Runnable updatePreview = () -> {
            BigDecimal discount = parseNonNegative(discountField.getText());
            if (discount == null || discount.compareTo(subtotal) > 0) {
                discount = BigDecimal.ZERO;
            }
            BigDecimal total = subtotal.subtract(discount);
            totalPreviewLabel.setText(CurrencyUtil.format(total));
            BigDecimal received = parseNonNegative(cashReceivedField.getText());
            BigDecimal change = received != null ? received.subtract(total) : null;
            changePreviewLabel.setText(change != null && change.compareTo(BigDecimal.ZERO) >= 0
                    ? CurrencyUtil.format(change) : "-");
        };
        discountField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cashReceivedField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cardRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Tạm tính:"), new Label(CurrencyUtil.format(subtotal)));
        grid.addRow(1, new Label("Giảm giá:"), discountField);
        grid.addRow(2, new Label("Thành tiền:"), totalPreviewLabel);
        grid.addRow(3, new Label("Phương thức:"), new HBox(14, cashRadio, cardRadio));
        grid.addRow(4, new Label("Tiền khách đưa:"), cashReceivedField);
        grid.addRow(5, new Label("Tiền thối lại:"), changePreviewLabel);
        dialog.getDialogPane().setContent(grid);

        PaymentResult[] pendingResult = {null};
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            BigDecimal discount = parseNonNegative(discountField.getText());
            if (discount == null) {
                AlertUtil.warning("Giảm giá không hợp lệ", "Vui lòng nhập một số hợp lệ.");
                event.consume();
                return;
            }
            if (discount.compareTo(subtotal) > 0) {
                AlertUtil.warning("Giảm giá không hợp lệ", "Giảm giá không được lớn hơn tạm tính.");
                event.consume();
                return;
            }
            BigDecimal total = subtotal.subtract(discount);
            PaymentMethod method = cashRadio.isSelected() ? PaymentMethod.CASH : PaymentMethod.CARD;
            if (method == PaymentMethod.CASH) {
                BigDecimal received = parseNonNegative(cashReceivedField.getText());
                if (received == null) {
                    AlertUtil.warning("Số tiền không hợp lệ", "Vui lòng nhập số tiền khách đưa.");
                    event.consume();
                    return;
                }
                if (received.compareTo(total) < 0) {
                    AlertUtil.warning("Chưa đủ tiền", "Số tiền khách đưa nhỏ hơn tổng tiền cần thanh toán.");
                    event.consume();
                    return;
                }
            }
            pendingResult[0] = new PaymentResult(method, discount, total);
        });

        dialog.showAndWait();
        if (pendingResult[0] != null) {
            completeOrder(pendingResult[0]);
        }
    }

    private BigDecimal parseNonNegative(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.compareTo(BigDecimal.ZERO) >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void completeOrder(PaymentResult payment) {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setEmployeeId(Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null);
        order.setCustomerId(attachedCustomer != null ? attachedCustomer.getId() : null);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(cartSubtotal());
        order.setDiscount(payment.discount());
        order.setTotal(payment.total());
        order.setPaymentMethod(payment.method());
        order.setPaidAt(LocalDateTime.now());

        List<OrderItem> items = new ArrayList<>(cart);

        paymentButton.setDisable(true);
        Async.run(
                () -> {
                    // OrderDAO.insert also decrements stock atomically (and refuses to go negative).
                    return orderDAO.insert(order, items);
                },
                savedOrder -> {
                    paymentButton.setDisable(false);
                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = items;
                    String customerName = attachedCustomer != null ? attachedCustomer.getFullName() : null;
                    AlertUtil.info("Thanh toán thành công", "Đơn hàng #" + savedOrder.getId() + " đã được thanh toán.");
                    openInvoiceWindow(savedOrder, items, customerName);
                    loadProducts(); // stock just changed - refresh in-memory copy
                    resetOrderAfterPayment();
                },
                error -> {
                    paymentButton.setDisable(false);
                    AlertUtil.error("Lỗi thanh toán", "Không thể lưu đơn hàng: " + error.getMessage());
                    loadProducts(); // stock may have changed elsewhere - refresh the in-memory copy
                }
        );
    }

    /**
     * The unpaid cart only lives in memory, so leaving this screen (any sidebar/logout button) or
     * closing the window would silently throw it away. Ask first; on "yes" the cart is cleared so
     * the same prompt doesn't fire again on the next screen.
     */
    private boolean confirmLeaveOrder() {
        if (cart.isEmpty()) {
            removeCloseGuard();
            return true;
        }
        boolean leave = AlertUtil.confirm("Đơn hàng chưa thanh toán",
                "Đơn hiện tại có " + cart.size() + " dòng sản phẩm chưa thanh toán và sẽ bị mất nếu bạn rời " +
                        "khỏi màn hình này. Vẫn rời đi?");
        if (leave) {
            cart.clear();
            removeCloseGuard();
        }
        return leave;
    }

    private void installCloseGuard() {
        javafx.stage.Window window = paymentButton != null && paymentButton.getScene() != null
                ? paymentButton.getScene().getWindow() : null;
        if (window != null) {
            window.setOnCloseRequest(e -> {
                if (!confirmLeaveOrder()) e.consume();
            });
        }
    }

    private void removeCloseGuard() {
        javafx.stage.Window window = paymentButton != null && paymentButton.getScene() != null
                ? paymentButton.getScene().getWindow() : null;
        if (window != null) {
            window.setOnCloseRequest(null);
        }
    }

    private void resetOrderAfterPayment() {
        orderInProgress = false;
        attachedCustomer = null;
        cart.clear();
        refreshCart();
        orderStatusLabel.setText("Chưa có đơn");
        orderIdLabel.setText("-");
        customerLabel.setText("Khách vãng lai");
    }

    private void openInvoiceWindow(Order order, List<OrderItem> items, String customerName) {
        HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                "/fxml/donthanhtoan.fxml", "Hóa đơn #" + order.getId());
        if (controller != null) {
            controller.setInvoiceData(order, items, Session.getDisplayName(), customerName);
        }
    }

    @FXML
    public void handleShowOrderHistory() {
        OrderHistoryWindow.show();
    }

    @FXML
    public void handlePrintInvoice() {
        if (lastCompletedOrder == null) {
            AlertUtil.warning("Chưa có hóa đơn",
                    "Đơn hàng hiện tại chưa được thanh toán nên chưa có hóa đơn để in.");
            return;
        }
        String customerName = attachedCustomer != null ? attachedCustomer.getFullName() : null;
        openInvoiceWindow(lastCompletedOrder, lastCompletedItems, customerName);
    }

    // =================================================================== Navigation
    // Duplicated from AdminController rather than shared - see the class javadoc.

    @FXML
    public void openDashboard(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openInventoryManagement(ActionEvent event) {
        AlertUtil.info("Chưa triển khai",
                "Chức năng Quản lý kho riêng biệt chưa được xây dựng. Tồn kho hiện được " +
                        "quản lý trực tiếp trong màn hình Quản lý sản phẩm.");
    }

    @FXML
    public void openEmployeeManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        Session.clear();
        SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
    }
}
