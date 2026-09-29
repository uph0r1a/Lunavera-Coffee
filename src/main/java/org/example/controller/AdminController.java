package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.DashboardStats;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Pager;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared by every admin-reachable screen (dashboard, category management, account
 * management, and - not yet wired to real data, see progress.md - product/order/customer
 * management). Each FXML only declares the @FXML fields it actually has, so every
 * initializer below is guarded by a null check on that screen's anchor field.
 */
public class AdminController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PAGE_SIZE = 8;

    private final DashboardStatsService dashboardStatsService = new DashboardStatsService();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    // ---------------------------------------------------------------- Dashboard (admin-trangchu.fxml)
    @FXML private Label totalTableLabel;
    @FXML private Label tableStatusLabel;
    @FXML private Label todayOrderLabel;
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayCustomerLabel;
    @FXML private LineChart<String, Number> revenueChart;
    @FXML private CategoryAxis revenueXAxis;
    @FXML private NumberAxis revenueYAxis;
    @FXML private Label paidOrderLabel;
    @FXML private Label openOrderLabel;
    @FXML private Label cancelledOrderLabel;
    @FXML private Label tableUsageLabel;
    @FXML private GridPane tableStatusGrid;

    // ---------------------------------------------------------------- Category management
    @FXML private Label totalCategoryLabel;
    @FXML private Label activeCategoryLabel;
    @FXML private Label hiddenCategoryLabel;
    @FXML private Label totalCategoryProductLabel;
    @FXML private TextField categorySearchField;
    @FXML private Button addCategoryButton;
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, Number> categoryIndexColumn;
    @FXML private TableColumn<Category, Void> categoryImageColumn;
    @FXML private TableColumn<Category, String> categoryNameColumn;
    @FXML private TableColumn<Category, String> categoryDescriptionColumn;
    @FXML private TableColumn<Category, Number> categoryProductCountColumn;
    @FXML private TableColumn<Category, String> categoryStatusColumn;
    @FXML private TableColumn<Category, Void> categoryActionColumn;
    @FXML private Label categoryPaginationLabel;
    @FXML private Button previousCategoryPageButton;
    @FXML private Button categoryPageOneButton;
    @FXML private Button nextCategoryPageButton;

    private final Pager<Category> categoryPager = new Pager<>(PAGE_SIZE);
    private List<Category> allCategories = List.of();
    // Loaded once per reload via CategoryDAO.countProductsByCategory() (a single grouped
    // query) instead of calling countProductsInCategory(id) per row/per keystroke - see
    // progress.md performance notes.
    private Map<Integer, Integer> categoryProductCounts = Map.of();

    // ---------------------------------------------------------------- Account management
    @FXML private Label totalAccountLabel;
    @FXML private Label activeAccountLabel;
    @FXML private Label lockedAccountLabel;
    @FXML private Label adminAccountLabel;
    @FXML private TextField accountSearchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> accountStatusFilter;
    @FXML private Button addAccountButton;
    @FXML private Label accountCountLabel;
    @FXML private TableView<User> accountTable;
    @FXML private TableColumn<User, Number> accountIndexColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> fullNameColumn;
    @FXML private TableColumn<User, String> phoneColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private TableColumn<User, String> createdDateColumn;
    @FXML private TableColumn<User, Void> accountActionColumn;
    @FXML private Label accountPaginationLabel;
    @FXML private Button previousPageButton;
    @FXML private Button pageOneButton;
    @FXML private Button nextPageNumberButton;
    @FXML private Button nextPageButton;
    @FXML private javafx.scene.layout.VBox accountDetailCard;
    @FXML private Button closeDetailButton;
    @FXML private Label detailAvatar;
    @FXML private Label detailUsername;
    @FXML private Label detailRole;
    @FXML private Label detailUsernameLabel;
    @FXML private Label detailFullNameLabel;
    @FXML private Label detailPhoneLabel;
    @FXML private Label detailRoleLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailCreatedDateLabel;
    @FXML private javafx.scene.layout.VBox permissionBox;
    @FXML private Button editAccountButton;
    @FXML private Button lockAccountButton;

    private final Pager<User> accountPager = new Pager<>(PAGE_SIZE);
    private List<User> allAccounts = List.of();
    // Loaded once per reload instead of calling employeeDAO.findById(id) per row/per
    // keystroke in employeeNameFor()/employeePhoneFor() - see progress.md performance notes.
    private Map<Integer, Employee> employeesById = Map.of();
    private User selectedAccount;

    // =================================================================== initialize

    @FXML
    private void initialize() {
        if (todayOrderLabel != null || todayRevenueLabel != null) {
            loadDashboardStats();
        }
        if (categoryTable != null) {
            initCategoryScreen();
        }
        if (accountTable != null) {
            initAccountScreen();
        }
    }

    // =================================================================== Navigation (shared by every screen)

    @FXML
    public void openDashboard(ActionEvent event) {
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openHome(ActionEvent event) {
        openDashboard(event);
    }

    @FXML
    public void handleHome(ActionEvent event) {
        openDashboard(event);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void handleProducts(ActionEvent event) {
        openProductManagement(event);
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void handleOrders(ActionEvent event) {
        openOrderManagement(event);
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void handleCategories(ActionEvent event) {
        openCategoryManagement(event);
    }

    @FXML
    public void openInventoryManagement(ActionEvent event) {
        AlertUtil.info("Chưa triển khai",
                "Chức năng Quản lý kho riêng biệt chưa được xây dựng. Tồn kho hiện được " +
                        "quản lý trực tiếp trong màn hình Quản lý sản phẩm.");
    }

    @FXML
    public void openEmployeeManagement(ActionEvent event) {
        // There is no separate employee-CRUD screen in this project; Account Management
        // (quanlytaikhoan.fxml) is the screen that creates/edits employee + login records,
        // so employee management is routed there. Documented as a deliberate decision in
        // progress.md rather than a missing feature.
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void handleAccounts(ActionEvent event) {
        openAccountManagement(event);
    }

    @FXML
    public void logout(ActionEvent event) {
        Session.clear();
        SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        logout(event);
    }

    @FXML
    public void handleNavigation(ActionEvent event) {
        // Generic fallback wired by some buttons in the original FXML skeletons; nothing to
        // route without knowing an intended destination.
    }

    // =================================================================== Dashboard

    private void loadDashboardStats() {
        // loadStats() makes ~10 sequential DB round-trips - fine on their own, but enough to
        // cause a visible freeze if run on the FX thread during screen load. Load in the
        // background and populate labels once the result comes back.
        Async.run(
                dashboardStatsService::loadStats,
                this::applyDashboardStats,
                error -> AlertUtil.error("Lỗi tải dữ liệu",
                        "Không thể tải số liệu thống kê: " + error.getMessage())
        );
    }

    private void applyDashboardStats(DashboardStats stats) {
        if (todayOrderLabel != null) todayOrderLabel.setText(String.valueOf(stats.todayOrders()));
        if (todayRevenueLabel != null) todayRevenueLabel.setText(CurrencyUtil.format(stats.todayRevenue()));
        if (todayCustomerLabel != null) todayCustomerLabel.setText(String.valueOf(stats.todayCustomers()));
        if (paidOrderLabel != null) paidOrderLabel.setText(String.valueOf(stats.paidOrders()));
        if (openOrderLabel != null) openOrderLabel.setText(String.valueOf(stats.openOrders()));
        if (cancelledOrderLabel != null) cancelledOrderLabel.setText(String.valueOf(stats.cancelledOrders()));

        if (revenueChart != null) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Doanh thu");
            DateTimeFormatter dayLabel = DateTimeFormatter.ofPattern("dd/MM");
            for (Map.Entry<java.time.LocalDate, BigDecimal> entry : stats.revenueLast7Days().entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey().format(dayLabel), entry.getValue()));
            }
            revenueChart.getData().setAll(series);
        }
        // "Bàn" (dine-in table) seating stats (totalTableLabel/tableStatusLabel/tableUsageLabel/
        // tableStatusGrid) stay as static placeholder content: there is no table/seating entity
        // in the schema yet (out of scope for this pass - see progress.md), so real numbers are
        // not invented for them.
    }

    // =================================================================== Category management

    private void initCategoryScreen() {
        categoryIndexColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        categoryTable.getItems().indexOf(data.getValue()) + categoryPager.getFromIndex()));
        categoryNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        categoryDescriptionColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDescription() == null ? "" : data.getValue().getDescription()));
        categoryProductCountColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        categoryProductCounts.getOrDefault(data.getValue().getId(), 0)));
        categoryStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "Đang hoạt động" : "Đã ẩn"));
        categoryActionColumn.setCellFactory(actionColumnFactory(
                this::openEditCategoryDialog,
                this::deleteCategory
        ));

        if (categorySearchField != null) {
            categorySearchField.textProperty().addListener((obs, old, value) -> applyCategoryFilter());
        }

        reloadCategories();
    }

    private void reloadCategories() {
        try {
            allCategories = categoryDAO.findAll();
            categoryProductCounts = categoryDAO.countProductsByCategory();
            applyCategoryFilter();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", e.getMessage());
        }
    }

    private void applyCategoryFilter() {
        String keyword = categorySearchField != null && categorySearchField.getText() != null
                ? categorySearchField.getText().trim().toLowerCase() : "";
        List<Category> filtered = keyword.isEmpty()
                ? allCategories
                : allCategories.stream()
                        .filter(c -> c.getName().toLowerCase().contains(keyword))
                        .collect(Collectors.toList());

        categoryPager.setItems(filtered);
        categoryTable.getItems().setAll(categoryPager.getCurrentPageItems());
        categoryTable.refresh();

        long activeCount = allCategories.stream().filter(Category::isActive).count();
        if (totalCategoryLabel != null) totalCategoryLabel.setText(String.valueOf(allCategories.size()));
        if (activeCategoryLabel != null) activeCategoryLabel.setText(String.valueOf(activeCount));
        if (hiddenCategoryLabel != null) hiddenCategoryLabel.setText(String.valueOf(allCategories.size() - activeCount));
        if (totalCategoryProductLabel != null) {
            int totalProducts = categoryProductCounts.values().stream().mapToInt(Integer::intValue).sum();
            totalCategoryProductLabel.setText(String.valueOf(totalProducts));
        }
        if (categoryPaginationLabel != null) {
            categoryPaginationLabel.setText(String.format("Hiển thị %d – %d trong tổng số %d danh mục",
                    categoryPager.getFromIndex(), categoryPager.getToIndex(), categoryPager.getTotalCount()));
        }
    }

    @FXML
    public void handleSearchAction() {
        applyCategoryFilter();
    }

    @FXML
    public void handleAddCategory() {
        openCategoryDialog(null);
    }

    private void openEditCategoryDialog(Category category) {
        openCategoryDialog(category);
    }

    private void openCategoryDialog(Category existing) {
        Dialog<Category> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Thêm danh mục" : "Chỉnh sửa danh mục");
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        TextArea descriptionField = new TextArea(existing != null ? existing.getDescription() : "");
        descriptionField.setPrefRowCount(3);
        CheckBox activeBox = new CheckBox("Đang hoạt động");
        activeBox.setSelected(existing == null || existing.isActive());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Tên danh mục:"), nameField);
        grid.addRow(1, new Label("Mô tả:"), descriptionField);
        grid.addRow(2, new Label(""), activeBox);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập tên danh mục.");
                return null;
            }
            Category category = existing != null ? existing : new Category();
            category.setName(nameField.getText().trim());
            category.setDescription(descriptionField.getText());
            category.setActive(activeBox.isSelected());
            return category;
        });

        Optional<Category> result = dialog.showAndWait();
        result.ifPresent(category -> {
            try {
                if (existing == null) {
                    categoryDAO.insert(category);
                } else {
                    categoryDAO.update(category);
                }
                reloadCategories();
            } catch (DataAccessException e) {
                AlertUtil.error("Không thể lưu danh mục", e.getMessage());
            }
        });
    }

    private void deleteCategory(Category category) {
        int productCount = categoryDAO.countProductsInCategory(category.getId());
        if (productCount > 0) {
            AlertUtil.warning("Không thể xóa",
                    "Danh mục \"" + category.getName() + "\" đang có " + productCount +
                            " sản phẩm. Hãy chuyển các sản phẩm sang danh mục khác trước, " +
                            "hoặc ẩn danh mục thay vì xóa.");
            return;
        }
        boolean confirmed = AlertUtil.confirm("Xác nhận xóa",
                "Xóa danh mục \"" + category.getName() + "\"? Hành động này không thể hoàn tác.");
        if (confirmed) {
            categoryDAO.deleteIfUnused(category.getId());
            reloadCategories();
        }
    }

    @FXML
    public void handlePreviousPage() {
        categoryPager.previousPage();
        applyCategoryFilter();
    }

    @FXML
    public void handlePageOne() {
        categoryPager.goToPage(1);
        applyCategoryFilter();
    }

    @FXML
    public void handleNextPage() {
        categoryPager.nextPage();
        applyCategoryFilter();
    }

    @FXML
    public void handleTableClick() {
        // Row selection alone needs no action; edit/delete are reached through the action
        // column's own buttons (see actionColumnFactory).
    }

    // =================================================================== Account management

    private void initAccountScreen() {
        accountIndexColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        accountTable.getItems().indexOf(data.getValue()) + accountPager.getFromIndex()));
        usernameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        fullNameColumn.setCellValueFactory(data -> new SimpleStringProperty(employeeNameFor(data.getValue())));
        phoneColumn.setCellValueFactory(data -> new SimpleStringProperty(employeePhoneFor(data.getValue())));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(roleLabel(data.getValue().getRole())));
        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus() == AccountStatus.ACTIVE ? "Hoạt động" : "Đã khóa"));
        createdDateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCreatedAt() != null ? data.getValue().getCreatedAt().format(DATE_FORMAT) : "-"));
        accountActionColumn.setCellFactory(actionColumnFactory(this::showAccountDetail, null));

        if (roleFilter != null) {
            roleFilter.getItems().setAll("Tất cả quyền", "Quản trị viên", "Nhân viên", "Khách hàng");
            roleFilter.getSelectionModel().selectFirst();
        }
        if (accountStatusFilter != null) {
            accountStatusFilter.getItems().setAll("Tất cả trạng thái", "Hoạt động", "Đã khóa");
            accountStatusFilter.getSelectionModel().selectFirst();
        }
        if (accountSearchField != null) {
            accountSearchField.textProperty().addListener((obs, old, value) -> applyAccountFilter());
        }
        if (!Session.isAdmin() && permissionBox != null) {
            // Employees can view accounts but not modify them - enforced here in code, not
            // just by hiding buttons (see progress.md section on permission checks).
            if (addAccountButton != null) addAccountButton.setDisable(true);
        }

        reloadAccounts();
    }

    private void reloadAccounts() {
        try {
            allAccounts = userDAO.findAll();
            employeesById = employeeDAO.findAll().stream()
                    .collect(Collectors.toMap(Employee::getId, e -> e));
            applyAccountFilter();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", e.getMessage());
        }
    }

    private void applyAccountFilter() {
        String keyword = accountSearchField != null && accountSearchField.getText() != null
                ? accountSearchField.getText().trim().toLowerCase() : "";
        String roleChoice = roleFilter != null ? roleFilter.getValue() : null;
        String statusChoice = accountStatusFilter != null ? accountStatusFilter.getValue() : null;

        List<User> filtered = allAccounts.stream()
                .filter(u -> keyword.isEmpty()
                        || u.getUsername().toLowerCase().contains(keyword)
                        || employeeNameFor(u).toLowerCase().contains(keyword)
                        || employeePhoneFor(u).toLowerCase().contains(keyword))
                .filter(u -> roleChoice == null || roleChoice.startsWith("Tất cả") || roleLabel(u.getRole()).equals(roleChoice))
                .filter(u -> statusChoice == null || statusChoice.startsWith("Tất cả")
                        || (u.getStatus() == AccountStatus.ACTIVE) == statusChoice.equals("Hoạt động"))
                .collect(Collectors.toList());

        accountPager.setItems(filtered);
        accountTable.getItems().setAll(accountPager.getCurrentPageItems());
        accountTable.refresh();

        if (totalAccountLabel != null) totalAccountLabel.setText(String.valueOf(allAccounts.size()));
        if (activeAccountLabel != null) {
            activeAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getStatus() == AccountStatus.ACTIVE).count()));
        }
        if (lockedAccountLabel != null) {
            lockedAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getStatus() == AccountStatus.LOCKED).count()));
        }
        if (adminAccountLabel != null) {
            adminAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getRole() == Role.ADMIN).count()));
        }
        if (accountCountLabel != null) accountCountLabel.setText(filtered.size() + " tài khoản");
        if (accountPaginationLabel != null) {
            accountPaginationLabel.setText(String.format("Hiển thị %d – %d / %d tài khoản",
                    accountPager.getFromIndex(), accountPager.getToIndex(), accountPager.getTotalCount()));
        }
    }

    private String employeeNameFor(User user) {
        if (user.getEmployeeId() == null) return "-";
        Employee employee = employeesById.get(user.getEmployeeId());
        return employee != null ? employee.getFullName() : "-";
    }

    private String employeePhoneFor(User user) {
        if (user.getEmployeeId() == null) return "-";
        Employee employee = employeesById.get(user.getEmployeeId());
        return employee != null && employee.getPhone() != null ? employee.getPhone() : "-";
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> "Quản trị viên";
            case EMPLOYEE -> "Nhân viên";
            case CUSTOMER -> "Khách hàng";
        };
    }

    @FXML
    public void handleAccountSearch() {
        applyAccountFilter();
    }

    @FXML
    public void handleFilterChange() {
        applyAccountFilter();
    }

    @FXML
    public void handleAddAccount() {
        if (!requireAdmin()) return;
        openAccountDialog(null);
    }

    private void openAccountDialog(User existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Thêm tài khoản nhân viên" : "Chỉnh sửa tài khoản");
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType("Lưu", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Employee existingEmployee = existing != null && existing.getEmployeeId() != null
                ? employeeDAO.findById(existing.getEmployeeId()).orElse(null) : null;

        TextField fullNameField = new TextField(existingEmployee != null ? existingEmployee.getFullName() : "");
        TextField phoneField = new TextField(existingEmployee != null ? existingEmployee.getPhone() : "");
        TextField usernameField = new TextField(existing != null ? existing.getUsername() : "");
        usernameField.setDisable(existing != null); // username is immutable once created
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(existing == null ? "" : "Để trống nếu không đổi mật khẩu");
        ComboBox<Role> roleBox = new ComboBox<>();
        roleBox.getItems().setAll(Role.ADMIN, Role.EMPLOYEE);
        roleBox.setValue(existing != null && existing.getRole() != Role.CUSTOMER ? existing.getRole() : Role.EMPLOYEE);
        CheckBox activeBox = new CheckBox("Hoạt động");
        activeBox.setSelected(existing == null || existing.getStatus() == AccountStatus.ACTIVE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Họ và tên:"), fullNameField);
        grid.addRow(1, new Label("Số điện thoại:"), phoneField);
        grid.addRow(2, new Label("Tên đăng nhập:"), usernameField);
        grid.addRow(3, new Label("Mật khẩu:"), passwordField);
        grid.addRow(4, new Label("Quyền:"), roleBox);
        grid.addRow(5, new Label(""), activeBox);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }
            if (ValidationUtil.isBlank(fullNameField.getText()) || ValidationUtil.isBlank(usernameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập họ tên và tên đăng nhập.");
                return null;
            }
            if (!ValidationUtil.isValidUsername(usernameField.getText())) {
                AlertUtil.warning("Tên đăng nhập không hợp lệ",
                        "Tên đăng nhập phải từ 4-32 ký tự, chỉ gồm chữ, số và dấu gạch dưới.");
                return null;
            }
            if (!ValidationUtil.isBlank(phoneField.getText()) && !ValidationUtil.isValidPhone(phoneField.getText())) {
                AlertUtil.warning("Số điện thoại không hợp lệ", "Vui lòng nhập đúng định dạng số điện thoại.");
                return null;
            }
            try {
                if (existing == null) {
                    if (userDAO.existsByUsername(usernameField.getText().trim())) {
                        AlertUtil.warning("Trùng tên đăng nhập", "Tên đăng nhập đã tồn tại.");
                        return null;
                    }
                    if (!ValidationUtil.isValidPassword(passwordField.getText())) {
                        AlertUtil.warning("Mật khẩu yếu", "Mật khẩu phải có ít nhất 6 ký tự, gồm cả chữ và số.");
                        return null;
                    }
                    Employee employee = new Employee();
                    employee.setFullName(fullNameField.getText().trim());
                    employee.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
                    employee.setActive(activeBox.isSelected());
                    employeeDAO.insert(employee);

                    User user = new User();
                    user.setUsername(usernameField.getText().trim());
                    user.setPasswordHash(PasswordUtil.hash(passwordField.getText()));
                    user.setRole(roleBox.getValue());
                    user.setStatus(activeBox.isSelected() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                    user.setEmployeeId(employee.getId());
                    userDAO.insert(user);
                } else {
                    if (existingEmployee != null) {
                        existingEmployee.setFullName(fullNameField.getText().trim());
                        existingEmployee.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
                        existingEmployee.setActive(activeBox.isSelected());
                        employeeDAO.update(existingEmployee);
                    }
                    userDAO.updateRole(existing.getId(), roleBox.getValue());
                    userDAO.updateStatus(existing.getId(), activeBox.isSelected() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                }
            } catch (DataAccessException e) {
                AlertUtil.error("Không thể lưu tài khoản", e.getMessage());
            }
            return null;
        });

        dialog.showAndWait();
        reloadAccounts();
    }

    @FXML
    public void handleEditAccount() {
        if (!requireAdmin()) return;
        if (selectedAccount == null) {
            AlertUtil.info("Chưa chọn tài khoản", "Vui lòng chọn một tài khoản trong bảng trước.");
            return;
        }
        openAccountDialog(selectedAccount);
    }

    @FXML
    public void handleLockAccount() {
        if (!requireAdmin()) return;
        if (selectedAccount == null) {
            AlertUtil.info("Chưa chọn tài khoản", "Vui lòng chọn một tài khoản trong bảng trước.");
            return;
        }
        if (selectedAccount.getUsername().equals(Session.getCurrentUser().getUsername())) {
            AlertUtil.warning("Không thể khóa", "Bạn không thể tự khóa tài khoản đang đăng nhập.");
            return;
        }
        boolean nowLocking = selectedAccount.getStatus() == AccountStatus.ACTIVE;
        boolean confirmed = AlertUtil.confirm(nowLocking ? "Khóa tài khoản" : "Mở khóa tài khoản",
                (nowLocking ? "Khóa" : "Mở khóa") + " tài khoản \"" + selectedAccount.getUsername() + "\"?");
        if (confirmed) {
            userDAO.updateStatus(selectedAccount.getId(), nowLocking ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
            reloadAccounts();
            handleCloseDetail();
        }
    }

    @FXML
    public void handleSelectAccount() {
        User selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        showAccountDetail(selected);
    }

    private void showAccountDetail(User user) {
        selectedAccount = user;
        if (detailUsername != null) detailUsername.setText(user.getUsername());
        if (detailRole != null) detailRole.setText(roleLabel(user.getRole()));
        if (detailUsernameLabel != null) detailUsernameLabel.setText(user.getUsername());
        if (detailFullNameLabel != null) detailFullNameLabel.setText(employeeNameFor(user));
        if (detailPhoneLabel != null) detailPhoneLabel.setText(employeePhoneFor(user));
        if (detailRoleLabel != null) detailRoleLabel.setText(roleLabel(user.getRole()));
        if (detailStatusLabel != null) {
            detailStatusLabel.setText(user.getStatus() == AccountStatus.ACTIVE ? "Hoạt động" : "Đã khóa");
        }
        if (detailCreatedDateLabel != null) {
            detailCreatedDateLabel.setText(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_FORMAT) : "-");
        }
        if (lockAccountButton != null) {
            lockAccountButton.setText(user.getStatus() == AccountStatus.ACTIVE ? "▣   Khóa" : "▣   Mở khóa");
        }
        if (accountDetailCard != null) {
            accountDetailCard.setVisible(true);
        }
    }

    @FXML
    public void handleCloseDetail() {
        selectedAccount = null;
        if (accountDetailCard != null) {
            accountDetailCard.setVisible(false);
        }
        if (accountTable != null) {
            accountTable.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void handlePreviousAccountPage() {
        accountPager.previousPage();
        applyAccountFilter();
    }

    @FXML
    public void handleAccountPageOne() {
        accountPager.goToPage(1);
        applyAccountFilter();
    }

    @FXML
    public void handleAccountPageTwo() {
        accountPager.goToPage(2);
        applyAccountFilter();
    }

    @FXML
    public void handleNextAccountPage() {
        accountPager.nextPage();
        applyAccountFilter();
    }

    private boolean requireAdmin() {
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chỉ quản trị viên mới có thể thực hiện thao tác này.");
            return false;
        }
        return true;
    }

    /** Builds a table-cell factory rendering one or two small action buttons per row. */
    private <T> Callback<TableColumn<T, Void>, TableCell<T, Void>> actionColumnFactory(
            java.util.function.Consumer<T> onEdit, java.util.function.Consumer<T> onDelete) {
        return column -> new TableCell<>() {
            private final Button editButton = new Button("✎");
            private final Button deleteButton = new Button("🗑");
            private final HBox box = new HBox(6, editButton, deleteButton);

            {
                editButton.setOnAction(e -> onEdit.accept(getTableView().getItems().get(getIndex())));
                if (onDelete != null) {
                    deleteButton.setOnAction(e -> onDelete.accept(getTableView().getItems().get(getIndex())));
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    box.getChildren().setAll(editButton);
                    if (onDelete != null) {
                        box.getChildren().add(deleteButton);
                    }
                    setGraphic(box);
                }
            }
        };
    }

    // =================================================================== Order management (quanlydonhang.fxml)
    // Not wired to real data yet: order creation/POS checkout has no screen built against it
    // in this pass (a large feature on its own - see progress.md). These handlers are
    // currently inert filter/tab clicks with no data loaded, so nothing appears broken; they
    // are not "fake success" on a create/save action.
    @FXML public void handleAllOrders() {}
    @FXML public void handleOpenOrders() {}
    @FXML public void handlePaidOrders() {}
    @FXML public void handleCancelledOrders() {}
    @FXML public void handleOrderStatusFilter() {}
    @FXML public void handleOrderDateFilter() {}
    @FXML public void handleClearOrderFilter() {}
    @FXML public void handleRefreshOrders() {}
    @FXML public void handlePreviousOrderPage() {}
    @FXML public void handleOrderPageOne() {}
    @FXML public void handleOrderPageTwo() {}
    @FXML public void handleNextOrderPage() {}
    @FXML public void handleSearchOrder() {}

    // =================================================================== Product management (quanlysanpham.fxml)
    // Same status as order management above: browsing/filtering tabs exist but are not yet
    // backed by ProductDAO from this screen (deferred - see progress.md). The one true
    // create action is explicit about not being ready yet, rather than silently no-op-ing.
    @FXML public void handleAllCategory() {}
    @FXML public void handleCoffeeCategory() {}
    @FXML public void handleCakeCategory() {}
    @FXML public void handleJuiceCategory() {}
    @FXML public void handleCategoryFilter() {}
    @FXML public void handleStatusFilter() {}

    @FXML
    public void handleAddProduct() {
        AlertUtil.info("Chưa triển khai",
                "Quản lý sản phẩm (thêm/sửa/xóa) chưa được kết nối với cơ sở dữ liệu trong " +
                        "phiên bản này. Xem progress.md để biết kế hoạch cho giai đoạn tiếp theo.");
    }

    @FXML public void handlePageTwo() {}
}
