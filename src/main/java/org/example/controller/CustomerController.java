package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Backs Customer Management (quanlykhachhang.fxml). Its own class rather than a 7th screen on
 * AdminController - same reasoning as OrderController (see its javadoc and progress.md,
 * Session 6/7): that class was already large and this is a clean, simple screen to start fresh
 * with distinct fx:id names, avoiding the collision risk entirely rather than adding to it.
 *
 * Deliberately read-only-list + search + add/edit for this pass (per progress.md's Step 3
 * plan) - no delete (a customer may be referenced by past orders via customer_id, or by a
 * self-registered login account; safely reconciling that is a bigger feature than this screen
 * needs to unblock) and no order-history view (would need a new OrderDAO query - out of scope
 * here, noted as a possible future addition rather than built partially).
 */
public class CustomerController {

    private final CustomerDAO customerDAO = new CustomerDAO();

    @FXML private Label accountNameLabel;
    @FXML private Label accountRoleLabel;
    @FXML private TextField customerSearchField;
    @FXML private Button addCustomerButton;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Number> customerIndexColumn;
    @FXML private TableColumn<Customer, String> customerNameColumn;
    @FXML private TableColumn<Customer, String> customerPhoneColumn;
    @FXML private TableColumn<Customer, String> customerEmailColumn;
    @FXML private TableColumn<Customer, Number> customerLoyaltyColumn;
    @FXML private TableColumn<Customer, String> customerTierColumn;
    @FXML private TableColumn<Customer, Void> customerActionColumn;

    private List<Customer> allCustomers = List.of();

    @FXML
    private void initialize() {
        if (accountNameLabel != null) accountNameLabel.setText(Session.getDisplayName());
        if (accountRoleLabel != null) accountRoleLabel.setText(Session.isAdmin() ? "Quản trị viên" : "Nhân viên");

        customerIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                customerTable.getItems().indexOf(data.getValue()) + 1));
        customerNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        customerPhoneColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPhone() != null ? data.getValue().getPhone() : "-"));
        customerEmailColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEmail() != null ? data.getValue().getEmail() : "-"));
        customerLoyaltyColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getLoyaltyPoints()));
        customerTierColumn.setCellValueFactory(data -> new SimpleStringProperty(tierFor(data.getValue().getLoyaltyPoints())));
        customerActionColumn.setCellFactory(editOnlyActionColumn(this::openEditDialog));

        customerSearchField.textProperty().addListener((obs, old, value) -> applyFilter());

        reloadCustomers();
    }

    private void reloadCustomers() {
        Async.run(
                customerDAO::findAll,
                result -> { allCustomers = result; applyFilter(); },
                error -> AlertUtil.error("Lỗi tải dữ liệu", "Không thể tải danh sách khách hàng: " + error.getMessage())
        );
    }

    private void applyFilter() {
        String keyword = customerSearchField.getText() != null ? customerSearchField.getText().trim().toLowerCase() : "";
        List<Customer> filtered = keyword.isEmpty()
                ? allCustomers
                : allCustomers.stream()
                        .filter(c -> c.getFullName().toLowerCase().contains(keyword)
                                || (c.getPhone() != null && c.getPhone().toLowerCase().contains(keyword)))
                        .collect(Collectors.toList());
        customerTable.getItems().setAll(filtered);
        customerTable.refresh();
    }

    /**
     * Simple, transparent thresholds on real loyalty-point data - not a stored field, so a
     * shop can't misconfigure it, but also not something they can currently customize. Revisit
     * if the shop wants configurable tiers.
     */
    private String tierFor(int points) {
        if (points >= 500) return "Vàng";
        if (points >= 100) return "Bạc";
        return "Thành viên mới";
    }

    @FXML
    public void handleAddCustomer() {
        openCustomerDialog(null);
    }

    private void openEditDialog(Customer customer) {
        openCustomerDialog(customer);
    }

    private void openCustomerDialog(Customer existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Thêm khách hàng" : "Chỉnh sửa khách hàng");
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType("Lưu", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getFullName() : "");
        TextField phoneField = new TextField(existing != null ? existing.getPhone() : "");
        TextField emailField = new TextField(existing != null ? existing.getEmail() : "");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Họ và tên:"), nameField);
        grid.addRow(1, new Label("Số điện thoại:"), phoneField);
        grid.addRow(2, new Label("Email:"), emailField);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập họ và tên.");
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(phoneField.getText()) && !ValidationUtil.isValidPhone(phoneField.getText())) {
                AlertUtil.warning("Số điện thoại không hợp lệ", "Vui lòng nhập đúng định dạng số điện thoại.");
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(emailField.getText()) && !ValidationUtil.isValidEmail(emailField.getText())) {
                AlertUtil.warning("Email không hợp lệ", "Vui lòng nhập đúng định dạng email.");
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(phoneField.getText())
                    && customerDAO.existsByPhone(phoneField.getText().trim(), existing != null ? existing.getId() : 0)) {
                AlertUtil.warning("Trùng số điện thoại", "Đã có khách hàng khác dùng số điện thoại này.");
                event.consume();
                return;
            }
            Customer customer = existing != null ? existing : new Customer();
            customer.setFullName(nameField.getText().trim());
            customer.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
            customer.setEmail(ValidationUtil.isBlank(emailField.getText()) ? null : emailField.getText().trim());
            try {
                if (existing == null) {
                    customer.setLoyaltyPoints(0);
                    customerDAO.insert(customer);
                } else {
                    customerDAO.update(customer);
                }
            } catch (DataAccessException ex) {
                AlertUtil.error("Không thể lưu khách hàng", ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
        reloadCustomers();
    }

    /** Single-button (edit only, no delete - see class javadoc) action column, same visual
     *  pattern as the ✎/🗑 pair used elsewhere, just without the delete half. */
    private Callback<TableColumn<Customer, Void>, TableCell<Customer, Void>> editOnlyActionColumn(Consumer<Customer> onEdit) {
        return column -> new TableCell<>() {
            private final Button editButton = new Button("✎");
            { editButton.setOnAction(e -> onEdit.accept(getTableView().getItems().get(getIndex()))); }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        };
    }

    // =================================================================== Navigation
    // Duplicated from AdminController/OrderController rather than shared - see OrderController's
    // javadoc for the reasoning; this is now the third occurrence, worth extracting to a shared
    // base class next time a new screen needs the same navigation set.

    @FXML
    public void openDashboard(ActionEvent event) {
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
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
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        Session.clear();
        SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
    }
}
