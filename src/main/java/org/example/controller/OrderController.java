package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

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
 * cart + checkout.
 */
public class OrderController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    @FXML private Label adminNameLabel;
    @FXML private Label adminRoleLabel;

    @FXML private GridPane tableGrid;
    @FXML private Label selectedTableLabel;
    @FXML private Label selectedTableStatusLabel;
    @FXML private Label selectedTableCapacityLabel;
    @FXML private Button startOrderButton;
    @FXML private Button toggleStatusButton;
    @FXML private Button tableDetailButton;

    @FXML private Label orderStatusLabel;
    @FXML private Label orderIdLabel;
    @FXML private Label customerLabel;
    @FXML private Label orderTableLabel;
    @FXML private Label orderTimeLabel;

    @FXML private TextField customerNameField;
    @FXML private TextField customerPhoneField;
    @FXML private Label cartItemCountLabel;

    @FXML private TableView<OrderItem> orderDetailTable;
    @FXML private TableColumn<OrderItem, Number> detailIndexColumn;
    @FXML private TableColumn<OrderItem, String> productNameColumn;
    @FXML private TableColumn<OrderItem, Void> quantityColumn;
    @FXML private TableColumn<OrderItem, String> unitPriceColumn;
    @FXML private TableColumn<OrderItem, String> subtotalColumn;

    @FXML private TextField productSearchField;
    @FXML private Button addProductButton;
    @FXML private HBox categoryTabBox;
    @FXML private FlowPane productCatalogFlow;

    @FXML private Label totalAmountLabel;
    @FXML private Button printOrderButton;
    @FXML private Button orderHistoryButton;
    @FXML private Button editOrderButton;
    @FXML private Button paymentButton;

    @FXML private Button productMenuButton;
    @FXML private Button categoryMenuButton;
    @FXML private Button accountMenuButton;
    @FXML private javafx.scene.control.Separator categorySeparator;
    @FXML private javafx.scene.layout.VBox adminMenuBox;

    private final ObservableList<OrderItem> cart = FXCollections.observableArrayList();
    private final Map<Integer, Button> tableButtonByNumber = new LinkedHashMap<>();
    private final Map<Integer, DiningTable> tableDataByNumber = new LinkedHashMap<>();
    private List<Product> allProducts = List.of();
    private List<Category> allCategories = List.of();
    private Integer selectedCategoryId = null;
    private Integer selectedTableNumber;
    private boolean orderInProgress;
    private Customer attachedCustomer;
    private Timeline realTimeClock;
    private Order activeOpenOrder;
    private Order lastCompletedOrder;
    private List<OrderItem> lastCompletedItems = List.of();

    private record TableOrderData(Order order, List<OrderItem> items, Customer customer) {
    }

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

        if (!Session.isAdmin()) {
            if (productMenuButton != null) {
                productMenuButton.setVisible(false);
                productMenuButton.setManaged(false);
            }
            if (categorySeparator != null) {
                categorySeparator.setVisible(false);
                categorySeparator.setManaged(false);
            }
            if (adminMenuBox != null) {
                adminMenuBox.setVisible(false);
                adminMenuBox.setManaged(false);
            }
        }

        if (orderStatusLabel != null) {
            orderStatusLabel.setText("Không hoạt động");
            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
        }
        if (orderIdLabel != null) orderIdLabel.setText("Chưa có đơn");
        if (customerLabel != null) customerLabel.setText("-");
        if (orderTableLabel != null) orderTableLabel.setText("-");
        if (totalAmountLabel != null) totalAmountLabel.setText("0 đ");

        initRealTimeClock();
        initCustomerInputs();
        loadTablesFromDb();

        detailIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                orderDetailTable.getItems().indexOf(data.getValue()) + 1));
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellFactory(col -> quantityCell());
        unitPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));

        if (productSearchField != null) {
            TextLengthLimiter.limit(productSearchField, TextLengthLimiter.SEARCH_MAX);
            productSearchField.textProperty().addListener((obs, o, n) -> renderProductCatalog());
        }

        loadCategoriesAndProducts();
        refreshCart();
    }

    // =================================================================== Real-Time Clock
    private void initRealTimeClock() {
        realTimeClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClockDisplay()));
        realTimeClock.setCycleCount(Animation.INDEFINITE);
        realTimeClock.play();
        updateClockDisplay();
    }

    private void updateClockDisplay() {
        if (orderTimeLabel != null) {
            String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
            if (activeOpenOrder != null && activeOpenOrder.getOrderDate() != null) {
                orderTimeLabel.setText(nowStr + " (Đơn: " + activeOpenOrder.getOrderDate().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
            } else {
                orderTimeLabel.setText(nowStr);
            }
        }
    }

    // =================================================================== Customer Auto-Link
    private void initCustomerInputs() {
        TextLengthLimiter.limit(customerNameField, TextLengthLimiter.NAME_MAX);
        TextLengthLimiter.limit(customerPhoneField, 15);
        if (customerPhoneField != null) {
            customerPhoneField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.trim().length() >= 9) {
                    lookupCustomerByPhone(newVal.trim());
                }
            });
        }
        if (customerNameField != null) {
            customerNameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    handleCustomerInfoChanged();
                }
            });
        }
    }

    private void lookupCustomerByPhone(String phone) {
        Async.run(
                () -> customerDAO.findByPhone(phone),
                optCust -> {
                    if (optCust.isPresent()) {
                        attachedCustomer = optCust.get();
                        if (customerNameField != null && (customerNameField.getText() == null || customerNameField.getText().isBlank() || "Khách vãng lai".equals(customerNameField.getText()))) {
                            customerNameField.setText(attachedCustomer.getFullName());
                        }
                        if (customerLabel != null) {
                            customerLabel.setText(attachedCustomer.getFullName());
                        }
                        syncOpenOrderToDb();
                    }
                },
                err -> {}
        );
    }

    private void handleCustomerInfoChanged() {
        String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        if (name.isEmpty() && phone.isEmpty()) return;

        Async.run(
                () -> ensureCustomerInDb(name, phone),
                cust -> {
                    if (cust != null) {
                        attachedCustomer = cust;
                        if (customerLabel != null) {
                            customerLabel.setText(cust.getFullName());
                        }
                        syncOpenOrderToDb();
                    }
                },
                err -> {}
        );
    }

    // Serialises find-or-create so two async callers (name-field focus-lost + start/checkout)
    // can never both miss the lookup and insert the same phone twice.
    private static final Object CUSTOMER_LOCK = new Object();

    /**
     * Customers are identified by phone number. With no valid phone the order is a walk-in
     * (no customer row is created), so a customer is never added twice for one checkout.
     */
    private Customer ensureCustomerInDb(String name, String phone) {
        if (!ValidationUtil.isValidPhone(phone)) return null;
        synchronized (CUSTOMER_LOCK) {
            Optional<Customer> existing = customerDAO.findByPhone(phone);
            if (existing.isPresent()) {
                Customer c = existing.get();
                if (!name.isEmpty() && !"Khách vãng lai".equals(name) && !name.equals(c.getFullName())) {
                    c.setFullName(name);
                    try { customerDAO.update(c); } catch (Exception ignored) {}
                }
                return c;
            }
            Customer newCust = new Customer();
            newCust.setFullName(name.isEmpty() ? "Khách vãng lai" : name);
            newCust.setPhone(phone);
            newCust.setLoyaltyPoints(0);
            try {
                return customerDAO.insert(newCust);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private Customer ensureCustomerFromFields() {
        String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        return ensureCustomerInDb(name, phone);
    }

    // =================================================================== Category & Product Catalog
    private void loadCategoriesAndProducts() {
        Async.run(
                () -> {
                    List<Category> categories = categoryDAO.findAll();
                    List<Product> products = productDAO.findAll();
                    return Map.entry(categories, products);
                },
                entry -> {
                    allCategories = entry.getKey();
                    allProducts = entry.getValue();
                    renderCategoryChips();
                    renderProductCatalog();
                },
                err -> AlertUtil.error("Lỗi", "Không thể tải danh mục và sản phẩm: " + err.getMessage())
        );
    }

    private void renderCategoryChips() {
        if (categoryTabBox == null) return;
        categoryTabBox.getChildren().clear();

        Button allBtn = new Button("Tất cả");
        allBtn.getStyleClass().add("category-chip");
        if (selectedCategoryId == null) allBtn.getStyleClass().add("category-chip-active");
        allBtn.setOnAction(e -> {
            selectedCategoryId = null;
            renderCategoryChips();
            renderProductCatalog();
        });
        categoryTabBox.getChildren().add(allBtn);

        for (Category cat : allCategories) {
            if (!cat.isActive()) continue;
            Button catBtn = new Button(cat.getName());
            boolean isSelected = selectedCategoryId != null && selectedCategoryId == cat.getId();
            catBtn.getStyleClass().add("category-chip");
            if (isSelected) catBtn.getStyleClass().add("category-chip-active");
            catBtn.setOnAction(e -> {
                selectedCategoryId = cat.getId();
                renderCategoryChips();
                renderProductCatalog();
            });
            categoryTabBox.getChildren().add(catBtn);
        }
    }

    private void renderProductCatalog() {
        if (productCatalogFlow == null) return;
        productCatalogFlow.getChildren().clear();

        String keyword = productSearchField != null && productSearchField.getText() != null
                ? productSearchField.getText().trim().toLowerCase() : "";

        List<Product> filtered = allProducts.stream()
                .filter(Product::isActive)
                .filter(p -> selectedCategoryId == null || p.getCategoryId() == selectedCategoryId)
                .filter(p -> keyword.isEmpty() || p.getName().toLowerCase().contains(keyword))
                .collect(Collectors.toList());

        for (Product product : filtered) {
            VBox card = new VBox(4);
            card.getStyleClass().add("product-card-item");

            Label nameLabel = new Label(product.getName());
            nameLabel.getStyleClass().add("product-card-name");
            nameLabel.setMaxWidth(130);
            nameLabel.setWrapText(true);

            Label priceLabel = new Label(CurrencyUtil.format(product.getPrice()));
            priceLabel.getStyleClass().add("product-card-price");

            card.getChildren().addAll(nameLabel, priceLabel);

            if (product.getStock() <= 0) {
                Label outOfStock = new Label("Hết hàng");
                outOfStock.setStyle("-fx-text-fill: #b33939; -fx-font-size: 10px; -fx-font-weight: bold;");
                card.getChildren().add(outOfStock);
                card.setOpacity(0.55);
            } else {
                card.setOnMouseClicked(e -> handleQuickAddProduct(product));
            }

            productCatalogFlow.getChildren().add(card);
        }
    }

    private void handleQuickAddProduct(Product product) {
        if (selectedTableNumber == null) {
            AlertUtil.warning("Chưa chọn bàn", "Vui lòng chọn một bàn ở sơ đồ bên trái trước khi gọi món.");
            return;
        }

        if (!orderInProgress || activeOpenOrder == null) {
            final int tNum = selectedTableNumber;
            Customer cust = ensureCustomerFromFields();
            attachedCustomer = cust;
            Async.run(
                    () -> {
                        tableDAO.setOccupied(tNum, null);
                        Order order = new Order();
                        order.setOrderDate(LocalDateTime.now());
                        order.setEmployeeId(Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null);
                        order.setCustomerId(cust != null ? cust.getId() : null);
                        order.setStatus(OrderStatus.OPEN);
                        order.setSubtotal(BigDecimal.ZERO);
                        order.setDiscount(BigDecimal.ZERO);
                        order.setTotal(BigDecimal.ZERO);
                        order.setTableNumber(tNum);
                        Order saved = orderDAO.insert(order, List.of());
                        tableDAO.updateStatus(tNum, TableStatus.OCCUPIED, saved.getId());
                        return saved;
                    },
                    savedOrder -> {
                        activeOpenOrder = savedOrder;
                        orderInProgress = true;
                        cart.clear();
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("#DH" + String.format("%06d", savedOrder.getId()));
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + tNum + " (Tầng 1)");
                        }
                        updateClockDisplay();
                        loadTablesFromDb();
                        addToCart(product);
                    },
                    error -> AlertUtil.error("Lỗi", "Không thể tạo đơn hàng cho bàn " + tNum + ": " + error.getMessage())
            );
        } else {
            addToCart(product);
        }
    }

    private void loadProducts() {
        try {
            allProducts = productDAO.findAll();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", "Không thể tải danh sách sản phẩm: " + e.getMessage());
        }
    }

    /** Renders "[-]  qty  [+]" inline in the quantity column */
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

    // =================================================================== Table selection & management (3 CỘT x 4 DÒNG)

    public void loadTablesFromDb() {
        if (tableGrid == null) return;
        Async.run(
                tableDAO::findAll,
                tables -> {
                    tableGrid.getChildren().clear();
                    tableButtonByNumber.clear();
                    tableDataByNumber.clear();
                    for (DiningTable table : tables) {
                        int num = table.getTableNumber();
                        tableDataByNumber.put(num, table);
                        int col = (num - 1) % 3;
                        int row = (num - 1) / 3;

                        String statusText = table.isOccupied() ? "Đang sử dụng" : "Không hoạt động";
                        Button btn = new Button("☕  " + table.getName() + "\n" + statusText);
                        btn.setMaxWidth(Double.MAX_VALUE);
                        btn.setMaxHeight(Double.MAX_VALUE);
                        btn.getStyleClass().add(table.isOccupied() ? "table-button-occupied" : "table-button-empty");
                        if (selectedTableNumber != null && selectedTableNumber == num) {
                            btn.getStyleClass().add("table-button-selected");
                        }
                        GridPane.setHgrow(btn, Priority.ALWAYS);
                        GridPane.setVgrow(btn, Priority.ALWAYS);
                        btn.setOnAction(e -> selectTable(num));
                        tableGrid.add(btn, col, row);
                        tableButtonByNumber.put(num, btn);
                    }
                    if (selectedTableNumber == null && !tables.isEmpty()) {
                        selectTable(tables.get(0).getTableNumber());
                    } else if (selectedTableNumber != null) {
                        updateSelectedTableInfo(selectedTableNumber);
                    }
                },
                error -> AlertUtil.error("Lỗi", "Không thể tải danh sách bàn: " + error.getMessage())
        );
    }

    public void selectTable(int number) {
        selectedTableNumber = number;
        highlightSelectedTableButton(number);
        updateSelectedTableInfo(number);
        loadTableOrderFromDb(number);
    }

    private void updateSelectedTableInfo(int number) {
        if (selectedTableLabel != null) {
            selectedTableLabel.setText("Bàn " + number);
        }
        DiningTable table = tableDataByNumber.get(number);
        boolean occupied = table != null && table.isOccupied();
        if (selectedTableStatusLabel != null) {
            selectedTableStatusLabel.setText(occupied ? "Đang sử dụng" : "Không hoạt động");
            selectedTableStatusLabel.getStyleClass().setAll(occupied ? "occupied-badge" : "empty-badge");
        }
        if (selectedTableCapacityLabel != null && table != null) {
            selectedTableCapacityLabel.setText("Sức chứa: " + table.getCapacity() + " người   •   Khu vực: Tầng 1");
        }
    }

    private void loadTableOrderFromDb(int number) {
        Async.run(
                () -> {
                    Optional<Order> openOrder = orderDAO.findOpenOrderByTable(number);
                    if (openOrder.isPresent()) {
                        List<OrderItem> items = orderDAO.findItemsByOrderId(openOrder.get().getId());
                        Customer cust = null;
                        if (openOrder.get().getCustomerId() != null) {
                            cust = customerDAO.findById(openOrder.get().getCustomerId()).orElse(null);
                        }
                        return new TableOrderData(openOrder.get(), items, cust);
                    }
                    return new TableOrderData(null, List.of(), null);
                },
                data -> {
                    DiningTable table = tableDataByNumber.get(number);
                    boolean occupied = table != null && table.isOccupied();
                    if (data.order() != null) {
                        activeOpenOrder = data.order();
                        attachedCustomer = data.customer();
                        cart.setAll(data.items());
                        orderInProgress = true;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("#DH" + String.format("%06d", data.order().getId()));
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number + " (Tầng 1)");
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText(data.customer() != null ? data.customer().getFullName() : "Khách vãng lai");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText(data.customer() != null && data.customer().getPhone() != null ? data.customer().getPhone() : "");
                        }
                        if (customerLabel != null) {
                            customerLabel.setText(data.customer() != null ? data.customer().getFullName() : "Khách vãng lai");
                        }
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText(CurrencyUtil.format(cartSubtotal()));
                    } else if (occupied) {
                        activeOpenOrder = null;
                        attachedCustomer = null;
                        cart.clear();
                        orderInProgress = true;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("Đang phục vụ");
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number + " (Tầng 1)");
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText("Khách vãng lai");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText("");
                        }
                        if (customerLabel != null) {
                            customerLabel.setText("Khách vãng lai");
                        }
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText("0 đ");
                    } else {
                        activeOpenOrder = null;
                        attachedCustomer = null;
                        cart.clear();
                        orderInProgress = false;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Không hoạt động");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("Chưa có đơn");
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number + " (Tầng 1)");
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText("");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText("");
                        }
                        if (customerLabel != null) {
                            customerLabel.setText("Trống");
                        }
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText("0 đ");
                    }
                    if (cartItemCountLabel != null) {
                        int totalQty = cart.stream().mapToInt(OrderItem::getQuantity).sum();
                        cartItemCountLabel.setText(cart.size() + " món (" + totalQty + " phần)");
                    }
                },
                error -> {}
        );
    }

    private void highlightSelectedTableButton(int number) {
        for (Map.Entry<Integer, Button> entry : tableButtonByNumber.entrySet()) {
            entry.getValue().getStyleClass().remove("table-button-selected");
            if (entry.getKey() == number) {
                entry.getValue().getStyleClass().add("table-button-selected");
            }
        }
    }

    @FXML
    public void handleToggleTableStatus() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để đổi trạng thái.");
            return;
        }
        DiningTable table = tableDataByNumber.get(selectedTableNumber);
        if (table == null) return;

        boolean newOccupied = !table.isOccupied();
        final int tNum = selectedTableNumber;
        // setEmpty() also cancels the table's OPEN order, so never do that silently.
        if (!newOccupied && activeOpenOrder != null && !cart.isEmpty()
                && !AlertUtil.confirm("Hủy đơn đang phục vụ",
                "Bàn " + tNum + " đang có đơn chưa thanh toán. Đổi sang không hoạt động sẽ hủy đơn này. Tiếp tục?")) {
            return;
        }
        Async.run(
                () -> {
                    if (newOccupied) {
                        tableDAO.setOccupied(tNum, null);
                    } else {
                        tableDAO.setEmpty(tNum);
                    }
                    return null;
                },
                result -> {
                    loadTablesFromDb();
                    loadTableOrderFromDb(tNum);
                    AlertUtil.info("Thành công",
                            "Bàn " + tNum + " đã đổi sang: " + (newOccupied ? "Đang sử dụng" : "Không hoạt động"));
                },
                error -> AlertUtil.error("Lỗi", "Không thể cập nhật trạng thái bàn: " + error.getMessage())
        );
    }

    @FXML
    public void handleTableDetail() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để xem chi tiết.");
            return;
        }
        DiningTable table = tableDataByNumber.get(selectedTableNumber);
        String status = (table != null && table.isOccupied()) ? "Đang sử dụng" : "Không hoạt động";
        AlertUtil.info("Chi tiết " + selectedTableLabel.getText(),
                (table != null ? "Sức chứa: " + table.getCapacity() + " người\n" : "") +
                "Trạng thái: " + status);
    }

    @FXML
    public void handleStartOrder() {
        if (selectedTableNumber == null) {
            AlertUtil.warning("Chưa chọn bàn", "Vui lòng chọn một bàn trước khi bắt đầu đặt đơn.");
            return;
        }
        final int tNum = selectedTableNumber;
        Customer cust = ensureCustomerFromFields();
        attachedCustomer = cust;
        Async.run(
                () -> {
                    tableDAO.setOccupied(tNum, null);
                    Order order = new Order();
                    order.setOrderDate(LocalDateTime.now());
                    order.setEmployeeId(Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null);
                    order.setCustomerId(cust != null ? cust.getId() : null);
                    order.setStatus(OrderStatus.OPEN);
                    order.setSubtotal(BigDecimal.ZERO);
                    order.setDiscount(BigDecimal.ZERO);
                    order.setTotal(BigDecimal.ZERO);
                    order.setTableNumber(tNum);
                    Order saved = orderDAO.insert(order, List.of());
                    tableDAO.updateStatus(tNum, TableStatus.OCCUPIED, saved.getId());
                    return saved;
                },
                savedOrder -> {
                    activeOpenOrder = savedOrder;
                    orderInProgress = true;
                    cart.clear();
                    if (orderStatusLabel != null) {
                        orderStatusLabel.setText("Đang phục vụ");
                        orderStatusLabel.getStyleClass().setAll("order-status-badge");
                    }
                    if (orderIdLabel != null) {
                        orderIdLabel.setText("#DH" + String.format("%06d", savedOrder.getId()));
                    }
                    if (orderTableLabel != null) {
                        orderTableLabel.setText("Bàn " + tNum + " (Tầng 1)");
                    }
                    updateClockDisplay();
                    if (customerLabel != null) {
                        customerLabel.setText(cust != null ? cust.getFullName() : "Khách vãng lai");
                    }
                    orderDetailTable.getItems().setAll(cart);
                    totalAmountLabel.setText("0 đ");
                    loadTablesFromDb();
                },
                error -> AlertUtil.error("Lỗi", "Không thể bắt đầu đặt đơn: " + error.getMessage())
        );
    }

    // =================================================================== Cart

    @FXML
    public void handleAddProductToCart() {
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
        handleQuickAddProduct(chosen);
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
        if (cartItemCountLabel != null) {
            int totalQty = cart.stream().mapToInt(OrderItem::getQuantity).sum();
            cartItemCountLabel.setText(cart.size() + " món (" + totalQty + " phần)");
        }
        syncOpenOrderToDb();
    }

    private void syncOpenOrderToDb() {
        if (activeOpenOrder != null && activeOpenOrder.getId() > 0) {
            activeOpenOrder.setSubtotal(cartSubtotal());
            activeOpenOrder.setTotal(cartSubtotal());
            activeOpenOrder.setCustomerId(attachedCustomer != null ? attachedCustomer.getId() : null);
            final Order orderToSave = activeOpenOrder;
            final List<OrderItem> itemsToSave = new ArrayList<>(cart);
            Async.run(
                    () -> orderDAO.saveOrUpdateOpenOrder(orderToSave, itemsToSave),
                    saved -> {},
                    err -> {}
            );
        }
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
            if (customerNameField != null) customerNameField.setText(customer.getFullName());
            if (customerPhoneField != null) customerPhoneField.setText(customer.getPhone() != null ? customer.getPhone() : "");
            if (customerLabel != null) customerLabel.setText(customer.getFullName());
            syncOpenOrderToDb();
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
        AlertUtil.setDefaultButton(dialog, confirmButtonType);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ToggleGroup methodGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton("Tiền mặt");
        cashRadio.setToggleGroup(methodGroup);
        cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton("Thẻ");
        cardRadio.setToggleGroup(methodGroup);

        TextField discountField = new TextField("0");
        TextField cashReceivedField = new TextField();
        TextLengthLimiter.limit(discountField, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(cashReceivedField, TextLengthLimiter.NUMBER_MAX);
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
        final int tNum = selectedTableNumber;
        final List<OrderItem> items = new ArrayList<>(cart);
        if (attachedCustomer == null) {
            attachedCustomer = ensureCustomerFromFields();
        }
        paymentButton.setDisable(true);
        Async.run(
                () -> {
                    Order savedOrder;
                    if (activeOpenOrder != null && activeOpenOrder.getId() > 0) {
                        savedOrder = orderDAO.payExistingOrder(activeOpenOrder.getId(), payment.method(), payment.discount(), payment.total(), items);
                    } else {
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
                        order.setTableNumber(tNum);
                        savedOrder = orderDAO.insert(order, items);
                    }
                    tableDAO.setEmpty(tNum);
                    return savedOrder;
                },
                savedOrder -> {
                    paymentButton.setDisable(false);
                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = items;
                    activeOpenOrder = null;
                    orderInProgress = false;
                    String customerName = attachedCustomer != null ? attachedCustomer.getFullName() : null;
                    AlertUtil.info("Thanh toán thành công", "Đơn hàng #" + savedOrder.getId() + " đã được thanh toán.");
                    openInvoiceWindow(savedOrder, items, customerName);
                    loadProducts();
                    cart.clear();
                    orderDetailTable.getItems().clear();
                    totalAmountLabel.setText("0 đ");
                    resetOrderAfterPayment();
                    loadTablesFromDb();
                },
                error -> {
                    paymentButton.setDisable(false);
                    AlertUtil.error("Lỗi thanh toán", "Không thể lưu đơn hàng: " + error.getMessage());
                    loadProducts();
                }
        );
    }

    private boolean confirmLeaveOrder() {
        removeCloseGuard();
        return true;
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
        if (customerNameField != null) customerNameField.clear();
        if (customerPhoneField != null) customerPhoneField.clear();
        if (customerLabel != null) customerLabel.setText("Khách vãng lai");
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
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
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
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
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
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
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
