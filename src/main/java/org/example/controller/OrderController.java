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
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.ImageStorage;
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
import javafx.scene.control.Alert;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.File;
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

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    private List<DiningTable> allTables = List.of();
    private List<Category> allCategories = List.of();
    private Integer selectedCategoryId = null;

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
    @FXML private TextField customerPhoneField;
    @FXML private TextField customerNameField;
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
    @FXML private Button orderHistoryButton;
    @FXML private Button editOrderButton;
    @FXML private Button paymentButton;

    // POS Menu FXML fields
    @FXML private TextField posProductSearchField;
    @FXML private VBox posCategoryContainer;
    @FXML private Label posCategoryTitleLabel;
    @FXML private Label posProductCountLabel;
    @FXML private FlowPane posProductFlowPane;

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
        Button[] buttons = {
                table1Button, table2Button, table3Button, table4Button,
                table5Button, table6Button, table7Button, table8Button,
                table9Button, table10Button, table11Button, table12Button,
                table13Button, table14Button, table15Button, table16Button
        };
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] != null) {
                tableButtons.put(buttons[i], i + 1);
            }
        }
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

        if (customerPhoneField != null) {
            customerPhoneField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.trim().length() >= 9) {
                    String phone = newVal.trim();
                    Async.run(
                            () -> customerDAO.findByPhone(phone),
                            optCust -> {
                                if (optCust.isPresent()) {
                                    attachedCustomer = optCust.get();
                                    if (customerNameField != null) {
                                        customerNameField.setText(attachedCustomer.getFullName());
                                    }
                                }
                            },
                            err -> {}
                    );
                }
            });
        }

        if (posProductSearchField != null) {
            posProductSearchField.textProperty().addListener((obs, oldVal, newVal) -> renderProductCards());
        }

        loadCategories();
        loadProducts();
        loadTablesFromDb();
        selectTable(1);
    }

    private void loadCategories() {
        Async.run(
                categoryDAO::findAll,
                cats -> {
                    allCategories = cats.stream().filter(Category::isActive).collect(Collectors.toList());
                    renderCategoryMenu();
                },
                err -> {
                    allCategories = List.of();
                    renderCategoryMenu();
                }
        );
    }

    private void renderCategoryMenu() {
        if (posCategoryContainer == null) return;
        posCategoryContainer.getChildren().clear();

        // Nút "Tất cả"
        Button allBtn = createCategoryButton(null, "Tất cả");
        posCategoryContainer.getChildren().add(allBtn);

        for (Category cat : allCategories) {
            Button btn = createCategoryButton(cat.getId(), cat.getName());
            posCategoryContainer.getChildren().add(btn);
        }
    }

    private Button createCategoryButton(Integer catId, String title) {
        Button btn = new Button(title);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.getStyleClass().add("pos-category-btn");
        boolean isSelected = (catId == null && selectedCategoryId == null) ||
                             (catId != null && catId.equals(selectedCategoryId));
        if (isSelected) {
            btn.getStyleClass().add("pos-category-btn-active");
        }
        btn.setOnAction(e -> {
            selectedCategoryId = catId;
            if (posCategoryTitleLabel != null) {
                posCategoryTitleLabel.setText(title);
            }
            renderCategoryMenu();
            renderProductCards();
        });
        return btn;
    }

    private void loadProducts() {
        Async.run(
                productDAO::findAll,
                products -> {
                    allProducts = products;
                    renderProductCards();
                },
                err -> AlertUtil.error("Lỗi", "Không thể tải danh sách sản phẩm: " + err.getMessage())
        );
    }

    private void renderProductCards() {
        if (posProductFlowPane == null) return;
        posProductFlowPane.getChildren().clear();

        String keyword = posProductSearchField != null && posProductSearchField.getText() != null
                ? posProductSearchField.getText().trim().toLowerCase()
                : "";

        List<Product> filtered = allProducts.stream()
                .filter(Product::isActive)
                .filter(p -> {
                    if (selectedCategoryId == null) return true;
                    return p.getCategoryId() != null && p.getCategoryId().equals(selectedCategoryId);
                })
                .filter(p -> {
                    if (keyword.isEmpty()) return true;
                    return p.getName() != null && p.getName().toLowerCase().contains(keyword);
                })
                .collect(Collectors.toList());

        if (posProductCountLabel != null) {
            posProductCountLabel.setText(filtered.size() + " món");
        }

        for (Product product : filtered) {
            VBox card = createProductCard(product);
            posProductFlowPane.getChildren().add(card);
        }
    }

    private VBox createProductCard(Product product) {
        VBox card = new VBox(6);
        card.getStyleClass().add("product-card");
        card.setPrefWidth(110);
        card.setMaxWidth(125);
        card.setAlignment(Pos.CENTER);

        // Product Image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(94);
        imageView.setFitHeight(72);
        imageView.setPreserveRatio(false);

        Rectangle clip = new Rectangle(94, 72);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        imageView.setClip(clip);

        File file = product.getImagePath() != null
                ? ImageStorage.resolve(product.getImagePath())
                : null;
        if (file != null && file.exists()) {
            imageView.setImage(new Image(file.toURI().toString(), 94, 72, false, true));
        }

        // Product Name
        Label nameLabel = new Label(product.getName());
        nameLabel.getStyleClass().add("product-card-name");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(100);
        nameLabel.setMinHeight(30);
        nameLabel.setAlignment(Pos.CENTER);

        // Footer: Price and (+) Button
        HBox footer = new HBox(4);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label priceLabel = new Label(CurrencyUtil.format(product.getPrice()));
        priceLabel.getStyleClass().add("product-card-price");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("product-add-btn");
        addBtn.setOnAction(e -> {
            e.consume();
            handleAddProductToCart(product);
        });

        footer.getChildren().addAll(priceLabel, spacer, addBtn);

        card.getChildren().addAll(imageView, nameLabel, footer);
        card.setOnMouseClicked(e -> handleAddProductToCart(product));

        return card;
    }

    private void handleAddProductToCart(Product product) {
        if (!orderInProgress) {
            if (selectedTableNumber == null) {
                selectTable(1);
            }
            orderInProgress = true;
            if (orderTimeLabel != null) {
                orderTimeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
            }
            if (orderStatusLabel != null) {
                orderStatusLabel.setText("Đang phục vụ");
            }
            if (orderTableLabel != null && selectedTableNumber != null) {
                orderTableLabel.setText("Bàn " + selectedTableNumber + " (Tầng 1)");
            }
        }
        addToCart(product);
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

    public void selectTable(int number) {
        selectedTableNumber = number;
        selectedTableLabel.setText("Bàn " + number);
        DiningTable table = findTable(number);
        boolean occupied = table != null && table.isOccupied();
        selectedTableStatusLabel.setText(occupied ? "Có đơn" : "Không đơn");
        selectedTableStatusLabel.getStyleClass().removeAll("empty-badge", "order-status-badge");
        selectedTableStatusLabel.getStyleClass().add(occupied ? "order-status-badge" : "empty-badge");
        startOrderButton.setText(occupied ? "▶  Đặt đơn" : "▶  Bắt đầu đặt đơn");
        highlightSelectedTableButton(number);

        if (orderTableLabel != null) {
            orderTableLabel.setText("Bàn " + number + " (Tầng 1)");
        }
        if (orderTimeLabel != null) {
            orderTimeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
        }

        loadTableOrder(number, occupied);
    }

    private void loadTableOrder(int number, boolean occupied) {
        if (!occupied) {
            orderInProgress = true;
            attachedCustomer = null;
            if (customerPhoneField != null) customerPhoneField.setText("");
            if (customerNameField != null) customerNameField.setText("");
            if (orderStatusLabel != null) {
                orderStatusLabel.setText("Đang phục vụ");
            }
            cart.clear();
            refreshCart();
            Async.run(
                    orderDAO::getNextOrderId,
                    nextId -> {
                        if (orderIdLabel != null) orderIdLabel.setText("#" + nextId);
                    },
                    err -> {
                        if (orderIdLabel != null) orderIdLabel.setText("#1");
                    }
            );
            return;
        }

        Async.run(
                () -> orderDAO.findOpenOrderByTable(number),
                optOrder -> {
                    if (optOrder.isPresent()) {
                        Order order = optOrder.get();
                        orderInProgress = true;
                        if (orderIdLabel != null) orderIdLabel.setText("#" + order.getId());
                        if (orderTimeLabel != null && order.getOrderDate() != null) {
                            orderTimeLabel.setText(order.getOrderDate().format(TIME_FORMAT));
                        }
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                        }

                        if (order.getCustomerId() != null) {
                            Async.run(
                                    () -> customerDAO.findById(order.getCustomerId()),
                                    optCust -> {
                                        attachedCustomer = optCust.orElse(null);
                                        if (attachedCustomer != null) {
                                            if (customerPhoneField != null) {
                                                customerPhoneField.setText(attachedCustomer.getPhone() != null ? attachedCustomer.getPhone() : "");
                                            }
                                            if (customerNameField != null) {
                                                customerNameField.setText(attachedCustomer.getFullName());
                                            }
                                        }
                                    },
                                    err -> {}
                            );
                        } else {
                            attachedCustomer = null;
                            if (customerPhoneField != null) customerPhoneField.setText("");
                            if (customerNameField != null) customerNameField.setText("");
                        }

                        Async.run(
                                () -> orderDAO.findItemsByOrderId(order.getId()),
                                items -> {
                                    cart.setAll(items);
                                    refreshCart();
                                },
                                err -> refreshCart()
                        );
                    } else {
                        orderInProgress = true;
                        attachedCustomer = null;
                        if (customerPhoneField != null) customerPhoneField.setText("");
                        if (customerNameField != null) customerNameField.setText("");
                        cart.clear();
                        refreshCart();
                        Async.run(
                                orderDAO::getNextOrderId,
                                nextId -> {
                                    if (orderIdLabel != null) orderIdLabel.setText("#" + nextId);
                                },
                                err -> {
                                    if (orderIdLabel != null) orderIdLabel.setText("#1");
                                }
                        );
                    }
                },
                err -> {
                    cart.clear();
                    refreshCart();
                }
        );
    }

    private void clearOrderInfo() {
        orderInProgress = true;
        attachedCustomer = null;
        if (customerPhoneField != null) customerPhoneField.setText("");
        if (customerNameField != null) customerNameField.setText("");
        if (orderTableLabel != null && selectedTableNumber != null) {
            orderTableLabel.setText("Bàn " + selectedTableNumber + " (Tầng 1)");
        }
        if (orderTimeLabel != null) {
            orderTimeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
        }
        if (orderStatusLabel != null) {
            orderStatusLabel.setText("Đang phục vụ");
        }
        cart.clear();
        refreshCart();
        Async.run(
                orderDAO::getNextOrderId,
                nextId -> {
                    if (orderIdLabel != null) orderIdLabel.setText("#" + nextId);
                },
                err -> {
                    if (orderIdLabel != null) orderIdLabel.setText("#1");
                }
        );
    }

    private void highlightSelectedTableButton(int number) {
        for (Map.Entry<Button, Integer> entry : tableButtons.entrySet()) {
            entry.getKey().getStyleClass().remove("table-button-selected");
            if (entry.getValue() == number) {
                entry.getKey().getStyleClass().add("table-button-selected");
            }
        }
    }

    private void loadTablesFromDb() {
        Async.run(
                tableDAO::findAll,
                tables -> {
                    this.allTables = tables;
                    renderTableButtons();
                    if (selectedTableNumber != null) {
                        selectTable(selectedTableNumber);
                    }
                },
                error -> AlertUtil.error("Lỗi", "Không thể tải dữ liệu bàn: " + error.getMessage())
        );
    }

    private void renderTableButtons() {
        Map<Integer, DiningTable> tableMap = allTables.stream()
                .collect(Collectors.toMap(DiningTable::getTableNumber, t -> t, (a, b) -> a));

        for (Map.Entry<Button, Integer> entry : tableButtons.entrySet()) {
            Button btn = entry.getKey();
            int num = entry.getValue();
            DiningTable table = tableMap.get(num);
            boolean occupied = table != null && table.isOccupied();

            btn.getStyleClass().removeAll("table-button-occupied", "table-button-empty");
            btn.getStyleClass().add(occupied ? "table-button-occupied" : "table-button-empty");
            btn.setText("☕  " + (table != null ? table.getName() : "Bàn " + num)
                    + "\n" + (occupied ? "Có đơn" : "Không đơn"));
        }
        if (selectedTableNumber != null) {
            highlightSelectedTableButton(selectedTableNumber);
        }
    }

    private DiningTable findTable(int number) {
        for (DiningTable t : allTables) {
            if (t.getTableNumber() == number) {
                return t;
            }
        }
        return null;
    }

    @FXML
    public void handleTableDetail() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để xem chi tiết.");
            return;
        }
        DiningTable table = findTable(selectedTableNumber);
        boolean occupied = table != null && table.isOccupied();
        String currentStatusStr = occupied ? "Có đơn" : "Không đơn";
        String toggleText = occupied ? "Chuyển thành Không đơn" : "Chuyển thành Có đơn";

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Chi tiết " + selectedTableLabel.getText());
        alert.setHeaderText("Thông tin " + selectedTableLabel.getText());
        alert.setContentText("Sức chứa: 4 người • Tầng 1\nTrạng thái hiện tại: " + currentStatusStr);
        ButtonType toggleBtn = new ButtonType(toggleText);
        ButtonType closeBtn = new ButtonType("Đóng", ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(toggleBtn, closeBtn);
        AlertUtil.configure(alert);

        alert.showAndWait().ifPresent(response -> {
            if (response == toggleBtn) {
                if (occupied) {
                    tableDAO.setEmpty(selectedTableNumber);
                } else {
                    tableDAO.setOccupied(selectedTableNumber, null);
                }
                loadTablesFromDb();
            }
        });
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
        orderTableLabel.setText(selectedTableLabel.getText());
        orderTimeLabel.setText(LocalDateTime.now().format(TIME_FORMAT));
        if (customerPhoneField != null) customerPhoneField.setText("");
        if (customerNameField != null) customerNameField.setText("");
        refreshCart();
        Async.run(
                orderDAO::getNextOrderId,
                nextId -> {
                    if (orderIdLabel != null) orderIdLabel.setText("#" + nextId);
                },
                err -> {
                    if (orderIdLabel != null) orderIdLabel.setText("#1");
                }
        );

        // Update table in database to OCCUPIED
        tableDAO.setOccupied(selectedTableNumber, null);
        loadTablesFromDb();
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
            if (customerNameField != null) {
                customerNameField.setText(customer.getFullName());
            }
            if (customerPhoneField != null) {
                customerPhoneField.setText(customer.getPhone() != null ? customer.getPhone() : "");
            }
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
        completeOrderDirect();
    }

    private void completeOrderDirect() {
        BigDecimal subtotal = cartSubtotal();
        PaymentResult payment = new PaymentResult(PaymentMethod.CASH, BigDecimal.ZERO, subtotal);
        completeOrder(payment);
    }

    private void completeOrder(PaymentResult payment) {
        String phoneInput = customerPhoneField != null && customerPhoneField.getText() != null
                ? customerPhoneField.getText().trim() : "";
        String nameInput = customerNameField != null && customerNameField.getText() != null
                ? customerNameField.getText().trim() : "";

        paymentButton.setDisable(true);
        Async.run(
                () -> {
                    Customer currentCustomer = attachedCustomer;
                    if (currentCustomer == null && (!phoneInput.isEmpty() || !nameInput.isEmpty())) {
                        if (!phoneInput.isEmpty()) {
                            Optional<Customer> existing = customerDAO.findByPhone(phoneInput);
                            if (existing.isPresent()) {
                                currentCustomer = existing.get();
                                if (!nameInput.isEmpty() && !nameInput.equals(currentCustomer.getFullName())) {
                                    currentCustomer.setFullName(nameInput);
                                    customerDAO.update(currentCustomer);
                                }
                            } else {
                                Customer newCust = new Customer();
                                newCust.setFullName(!nameInput.isEmpty() ? nameInput : "Khách hàng " + phoneInput);
                                newCust.setPhone(phoneInput);
                                currentCustomer = customerDAO.insert(newCust);
                            }
                        } else {
                            Customer newCust = new Customer();
                            newCust.setFullName(nameInput);
                            currentCustomer = customerDAO.insert(newCust);
                        }
                    }

                    Order order = new Order();
                    order.setOrderDate(LocalDateTime.now());
                    order.setEmployeeId(Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null);
                    order.setCustomerId(currentCustomer != null ? currentCustomer.getId() : null);
                    order.setStatus(OrderStatus.PAID);
                    order.setSubtotal(cartSubtotal());
                    order.setDiscount(payment.discount());
                    order.setTotal(payment.total());
                    order.setPaymentMethod(payment.method());
                    order.setPaidAt(LocalDateTime.now());
                    order.setTableNumber(selectedTableNumber);

                    List<OrderItem> items = new ArrayList<>(cart);
                    Order savedOrder = orderDAO.insert(order, items);
                    String custDisplayName = currentCustomer != null ? currentCustomer.getFullName()
                            : (!nameInput.isEmpty() ? nameInput : "Khách vãng lai");
                    return new Object[] { savedOrder, items, custDisplayName };
                },
                resultArray -> {
                    paymentButton.setDisable(false);
                    Object[] data = (Object[]) resultArray;
                    Order savedOrder = (Order) data[0];
                    @SuppressWarnings("unchecked")
                    List<OrderItem> items = (List<OrderItem>) data[1];
                    String customerName = (String) data[2];

                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = items;
                    openInvoiceWindow(savedOrder, items, customerName);
                    loadProducts(); // stock just changed - refresh in-memory copy
                    if (selectedTableNumber != null) {
                        tableDAO.setEmpty(selectedTableNumber);
                        loadTablesFromDb();
                    }
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
        clearOrderInfo();
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
