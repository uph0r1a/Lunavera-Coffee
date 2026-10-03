package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.DashboardStats;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.DashboardWidgets;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.Pager;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shared by the dashboard, category, account, and product management screens (not order
 * management - that has its own dedicated OrderController, see progress.md Session 7). Each
 * FXML only declares the @FXML fields it actually has, so every initializer below is guarded
 * by a null check on that screen's anchor field.
 */
public class AdminController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PAGE_SIZE = 8;

    private final DashboardStatsService dashboardStatsService = new DashboardStatsService();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // ---------------------------------------------------------------- Dashboard (admin-trangchu.fxml)
    @FXML private Label dashboardProductCountLabel;
    @FXML private Label dashboardLowStockNoteLabel;
    @FXML private Label todayOrderLabel;
    @FXML private Label dashboardGreetingLabel;
    @FXML private Label dashboardAccountNameLabel;
    @FXML private Button reportButton;
    @FXML private Button backupButton;
    @FXML private Label todayRevenueLabel;
    @FXML private Label todayCustomerLabel;
    @FXML private LineChart<String, Number> revenueChart;
    @FXML private CategoryAxis revenueXAxis;
    @FXML private NumberAxis revenueYAxis;
    @FXML private Label paidOrderLabel;
    @FXML private Label openOrderLabel;
    @FXML private Label cancelledOrderLabel;
    @FXML private Label recentOrdersNoteLabel;
    @FXML private VBox recentOrdersList;
    @FXML private VBox lowStockList;

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

    // ---------------------------------------------------------------- Product management
    @FXML private Button allCategoryButton;
    @FXML private Button coffeeCategoryButton;
    @FXML private Button cakeCategoryButton;
    @FXML private Button juiceCategoryButton;
    @FXML private Button allCategoryListButton;
    @FXML private Button coffeeCategoryListButton;
    @FXML private Button cakeCategoryListButton;
    @FXML private Button juiceCategoryListButton;
    @FXML private Label allProductCountLabel;
    @FXML private Label coffeeCountLabel;
    @FXML private Label cakeCountLabel;
    @FXML private Label juiceCountLabel;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button addProductButton;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Void> imageColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, String> sellingPriceColumn;
    @FXML private TableColumn<Product, Number> stockColumn;
    @FXML private TableColumn<Product, String> productStatusColumn;
    @FXML private TableColumn<Product, Void> actionColumn;
    @FXML private Label totalProductLabel;
    @FXML private Button pageTwoButton;

    private final Pager<Product> productPager = new Pager<>(PAGE_SIZE);
    private List<Product> allProducts = List.of();
    private List<Category> allActiveCategories = List.of();
    /** "ALL" / "COFFEE" / "CAKE" / "JUICE" - the quick-filter tabs already in the FXML. */
    private String productCategoryTab = "ALL";

    // =================================================================== initialize

    @FXML
    private void initialize() {
        if (todayOrderLabel != null || todayRevenueLabel != null) {
            loadDashboardStats();
        }
        if (dashboardGreetingLabel != null) {
            dashboardGreetingLabel.setText("Chào mừng, " + Session.getDisplayName() + "!");
        }
        if (dashboardAccountNameLabel != null) {
            dashboardAccountNameLabel.setText(Session.getDisplayName());
        }
        if (categoryTable != null) {
            initCategoryScreen();
        }
        if (accountTable != null) {
            initAccountScreen();
        }
        if (productTable != null) {
            initProductScreen();
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
    public void handleBackupData() {
        if (!requireAdmin()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sao lưu dữ liệu");
        chooser.setInitialFileName("lunavera-backup-" +
                java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".db");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite database", "*.db"));
        java.io.File file = chooser.showSaveDialog(backupButton.getScene().getWindow());
        if (file == null) return;
        backupButton.setDisable(true);
        Async.run(
                () -> { com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.backupTo(file.toPath()); return file; },
                saved -> {
                    backupButton.setDisable(false);
                    AlertUtil.info("Sao lưu thành công", "Đã lưu bản sao dữ liệu vào:\n" + saved.getAbsolutePath() +
                            "\n\nĐể khôi phục: đóng ứng dụng rồi thay file ~/.lunavera-coffee/lunavera.db bằng bản sao này.");
                },
                error -> {
                    backupButton.setDisable(false);
                    AlertUtil.error("Không thể sao lưu", error.getMessage());
                }
        );
    }

    @FXML
    public void handleShowReports() {
        ReportWindow.show();
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
        // The old hardcoded "Bàn" (dine-in table) occupancy grid and fake low-stock rows were
        // replaced with real data (progress.md, Session 13): there's no table entity to report
        // real occupancy from, so those cards now show product count / low stock / recent orders.
        if (dashboardProductCountLabel != null) {
            dashboardProductCountLabel.setText(String.valueOf(stats.totalProducts()));
        }
        if (dashboardLowStockNoteLabel != null) {
            dashboardLowStockNoteLabel.setText(stats.lowStockCount() == 0
                    ? "Không có sản phẩm sắp hết hàng"
                    : "Cần nhập thêm: " + stats.lowStockCount() + " sản phẩm");
        }
        if (lowStockList != null) {
            DashboardWidgets.fillLowStock(lowStockList, stats.lowStockProducts());
        }
        if (recentOrdersList != null) {
            DashboardWidgets.fillRecentOrders(recentOrdersList, stats.recentOrders());
        }
        if (recentOrdersNoteLabel != null) {
            recentOrdersNoteLabel.setText(stats.recentOrders().isEmpty()
                    ? "Chưa có đơn" : stats.recentOrders().size() + " đơn mới nhất");
        }
    }

    // =================================================================== Category management

    private void initCategoryScreen() {
        categoryImageColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "☕");
                setGraphic(null);
            }
        });
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
        Async.run(
                () -> new Object[]{categoryDAO.findAll(), categoryDAO.countProductsByCategory()},
                result -> {
                    allCategories = castList(result[0]);
                    categoryProductCounts = castCountMap(result[1]);
                    applyCategoryFilter();
                    // Symmetric case: this screen's real "+ Thêm danh mục" only ever runs here, so
                    // productTable is null unless the *Product* screen's shortcut button used the
                    // same shared handler - in which case its own category list/dropdown needs
                    // refreshing too, since applyCategoryFilter() above just skipped (see its guard).
                    if (productTable != null) {
                        reloadProducts();
                    }
                },
                error -> AlertUtil.error("Lỗi", error.getMessage())
        );
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
        // categoryTable is null when this runs from the Product screen's own "+ Thêm danh
        // mục" shortcut (it shares handleAddCategory()/openCategoryDialog() with the real
        // Category screen, and each screen gets its own fresh AdminController instance with
        // only its own fx:id fields populated) - guard rather than NPE right after a
        // successful save.
        if (categoryTable != null) {
            categoryTable.getItems().setAll(categoryPager.getCurrentPageItems());
            categoryTable.refresh();
        }

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

        // Validate and save via an event filter on the button itself, consuming the event to
        // keep the dialog open on failure. setResultConverter can't do this: returning null
        // from it still closes the dialog, silently discarding whatever the user typed - the
        // exact "known limitation" flagged in progress.md (Session 11).
        boolean[] saved = {false};
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập tên danh mục.");
                event.consume();
                return;
            }
            Category category = existing != null ? existing : new Category();
            category.setName(nameField.getText().trim());
            category.setDescription(descriptionField.getText());
            category.setActive(activeBox.isSelected());
            try {
                if (existing == null) {
                    categoryDAO.insert(category);
                } else {
                    categoryDAO.update(category);
                }
                saved[0] = true;
            } catch (DataAccessException e) {
                AlertUtil.error("Không thể lưu danh mục", e.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
        if (saved[0]) {
            reloadCategories();
        }
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
        Async.run(
                () -> new Object[]{userDAO.findAll(), employeeDAO.findAll()},
                result -> {
                    allAccounts = castList(result[0]);
                    List<Employee> employees = castList(result[1]);
                    employeesById = employees.stream().collect(Collectors.toMap(Employee::getId, e -> e));
                    applyAccountFilter();
                },
                error -> AlertUtil.error("Lỗi", error.getMessage())
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Integer> castCountMap(Object value) {
        return (Map<Integer, Integer>) value;
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
        roleBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role role) { return role == null ? "" : roleLabel(role); }
            @Override public Role fromString(String text) { return null; }
        });
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

        // Event filter on the button itself (not setResultConverter) so an invalid/failed save
        // consumes the click and keeps the dialog open with everything the user typed intact -
        // see openCategoryDialog's comment for why.
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(fullNameField.getText()) || ValidationUtil.isBlank(usernameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập họ tên và tên đăng nhập.");
                event.consume();
                return;
            }
            if (!ValidationUtil.isValidUsername(usernameField.getText())) {
                AlertUtil.warning("Tên đăng nhập không hợp lệ",
                        "Tên đăng nhập phải từ 4-32 ký tự, chỉ gồm chữ, số và dấu gạch dưới.");
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(phoneField.getText()) && !ValidationUtil.isValidPhone(phoneField.getText())) {
                AlertUtil.warning("Số điện thoại không hợp lệ", "Vui lòng nhập đúng định dạng số điện thoại.");
                event.consume();
                return;
            }
            try {
                if (existing == null) {
                    if (userDAO.existsByUsername(usernameField.getText().trim())) {
                        AlertUtil.warning("Trùng tên đăng nhập", "Tên đăng nhập đã tồn tại.");
                        event.consume();
                        return;
                    }
                    if (!ValidationUtil.isValidPassword(passwordField.getText())) {
                        AlertUtil.warning("Mật khẩu yếu", "Mật khẩu phải có ít nhất 6 ký tự, gồm cả chữ và số.");
                        event.consume();
                        return;
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
                    boolean losesAdmin = existing.getRole() == Role.ADMIN
                            && existing.getStatus() == AccountStatus.ACTIVE
                            && (roleBox.getValue() != Role.ADMIN || !activeBox.isSelected());
                    if (losesAdmin && userDAO.countActiveAdmins() <= 1) {
                        AlertUtil.warning("Không thể thực hiện",
                                "Đây là quản trị viên đang hoạt động cuối cùng. Hãy tạo hoặc giữ ít nhất một " +
                                        "quản trị viên khác trước khi hạ quyền hoặc khóa tài khoản này.");
                        event.consume();
                        return;
                    }
                    if (!ValidationUtil.isBlank(passwordField.getText())
                            && !ValidationUtil.isValidPassword(passwordField.getText())) {
                        AlertUtil.warning("Mật khẩu yếu", "Mật khẩu phải có ít nhất 6 ký tự, gồm cả chữ và số.");
                        event.consume();
                        return;
                    }
                    if (existingEmployee != null) {
                        existingEmployee.setFullName(fullNameField.getText().trim());
                        existingEmployee.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
                        existingEmployee.setActive(activeBox.isSelected());
                        employeeDAO.update(existingEmployee);
                    }
                    if (!ValidationUtil.isBlank(passwordField.getText())) {
                        userDAO.updatePasswordHash(existing.getId(), PasswordUtil.hash(passwordField.getText()));
                    }
                    userDAO.updateRole(existing.getId(), roleBox.getValue());
                    userDAO.updateStatus(existing.getId(), activeBox.isSelected() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                }
            } catch (DataAccessException e) {
                AlertUtil.error("Không thể lưu tài khoản", e.getMessage());
                event.consume();
            }
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
        if (nowLocking && selectedAccount.getRole() == Role.ADMIN && userDAO.countActiveAdmins() <= 1) {
            AlertUtil.warning("Không thể khóa",
                    "Đây là quản trị viên đang hoạt động cuối cùng - khóa sẽ làm mất quyền quản trị hệ thống.");
            return;
        }
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
        // Re-read the account first: refreshes the role (an admin demoted since login stops
        // being one immediately) and ends the session if it was locked/removed meanwhile.
        if (!SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return false;
        }
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

    // =================================================================== Order management
    // Moved to a dedicated OrderController - see progress.md, Session 7. quanlydonhang.fxml's
    // fx:controller no longer points here, so these stub handlers are gone rather than left
    // as dead code.

    // =================================================================== Product management (quanlysanpham.fxml)

    private void initProductScreen() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCategoryName() != null ? data.getValue().getCategoryName() : "-"));
        sellingPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getPrice())));
        stockColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getStock()));
        stockColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle(null);
                    return;
                }
                setText(value.toString());
                // Same threshold the dashboard's "low stock" widgets already use
                // (ProductDAO.LOW_STOCK_THRESHOLD) - one definition of "low", not two.
                setStyle(value.intValue() <= ProductDAO.LOW_STOCK_THRESHOLD
                        ? "-fx-text-fill: #c0392b; -fx-font-weight: bold;" : null);
            }
        });
        productStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "Đang bán" : "Ngừng bán"));
        actionColumn.setCellFactory(actionColumnFactory(this::openEditProductDialog, this::deleteProduct));
        imageColumn.setCellFactory(column -> new TableCell<>() {
            private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
            {
                imageView.setFitWidth(44);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                Product product = (!empty && getTableRow() != null) ? (Product) getTableRow().getItem() : null;
                java.io.File file = product != null
                        ? com.coffeeshop.coffeeshopmanagement.util.ImageStorage.resolve(product.getImagePath())
                        : null;
                if (file == null) {
                    setGraphic(null);
                    return;
                }
                imageView.setImage(new javafx.scene.image.Image(
                        file.toURI().toString(), 44, 44, true, true));
                setGraphic(imageView);
            }
        });

        categoryFilter.getItems().setAll("Tất cả danh mục");
        categoryFilter.getSelectionModel().selectFirst();
        statusFilter.getItems().setAll("Tất cả trạng thái", "Đang bán", "Ngừng bán");
        statusFilter.getSelectionModel().selectFirst();
        searchField.textProperty().addListener((obs, old, value) -> applyProductFilter());

        reloadProducts();
    }

    private void reloadProducts() {
        Async.run(
                () -> {
                    List<Category> categories = categoryDAO.findAll();
                    List<Product> products = productDAO.findAll();
                    return new Object[]{categories, products};
                },
                result -> {
                    allActiveCategories = ((List<Category>) result[0]).stream()
                            .filter(Category::isActive).collect(Collectors.toList());
                    allProducts = (List<Product>) result[1];

                    String currentCategoryChoice = categoryFilter.getValue();
                    categoryFilter.getItems().setAll("Tất cả danh mục");
                    allActiveCategories.forEach(c -> categoryFilter.getItems().add(c.getName()));
                    categoryFilter.setValue(
                            currentCategoryChoice != null && categoryFilter.getItems().contains(currentCategoryChoice)
                                    ? currentCategoryChoice : "Tất cả danh mục");

                    applyProductFilter();
                },
                error -> AlertUtil.error("Lỗi tải dữ liệu", "Không thể tải danh sách sản phẩm: " + error.getMessage())
        );
    }

    private void applyProductFilter() {
        String keyword = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String categoryChoice = categoryFilter.getValue();
        String statusChoice = statusFilter.getValue();

        List<Product> filtered = allProducts.stream()
                .filter(p -> matchesCategoryTab(p, productCategoryTab))
                .filter(p -> keyword.isEmpty() || p.getName().toLowerCase().contains(keyword))
                .filter(p -> categoryChoice == null || categoryChoice.startsWith("Tất cả")
                        || categoryChoice.equals(p.getCategoryName()))
                .filter(p -> statusChoice == null || statusChoice.startsWith("Tất cả")
                        || p.isActive() == statusChoice.equals("Đang bán"))
                .collect(Collectors.toList());

        productPager.setItems(filtered);
        productTable.getItems().setAll(productPager.getCurrentPageItems());
        productTable.refresh();

        if (totalProductLabel != null) {
            totalProductLabel.setText("Tổng cộng: " + filtered.size() + " sản phẩm");
        }
        // Tab counts reflect the true category, independent of the search box/filters above -
        // they answer "how many products are in this category overall", not "how many match
        // what's currently typed".
        if (allProductCountLabel != null) allProductCountLabel.setText(String.valueOf(allProducts.size()));
        if (coffeeCountLabel != null) {
            coffeeCountLabel.setText(String.valueOf(allProducts.stream().filter(p -> matchesCategoryTab(p, "COFFEE")).count()));
        }
        if (cakeCountLabel != null) {
            cakeCountLabel.setText(String.valueOf(allProducts.stream().filter(p -> matchesCategoryTab(p, "CAKE")).count()));
        }
        if (juiceCountLabel != null) {
            juiceCountLabel.setText(String.valueOf(allProducts.stream().filter(p -> matchesCategoryTab(p, "JUICE")).count()));
        }
    }

    /**
     * The four quick-filter tabs (All/Coffee/Cake/Juice) are static UI carried over from the
     * original design rather than generated from the live category list (see progress.md for
     * why: turning them into a dynamic, arbitrary-length tab bar is an FXML layout change, not
     * just a controller change). They still reflect real data: a product only counts under
     * "Coffee" etc. if its actual category name contains the matching keyword. A shop that
     * never creates a category with these words simply sees 0 there - never a fake count.
     */
    private boolean matchesCategoryTab(Product product, String tab) {
        if ("ALL".equals(tab)) {
            return true;
        }
        String category = product.getCategoryName() != null ? product.getCategoryName().toLowerCase() : "";
        return switch (tab) {
            case "COFFEE" -> category.contains("phê") || category.contains("coffee");
            case "CAKE" -> category.contains("bánh") || category.contains("cake");
            case "JUICE" -> category.contains("nước") || category.contains("juice");
            default -> true;
        };
    }

    private void setProductCategoryTab(String tab) {
        productCategoryTab = tab;
        productPager.goToPage(1);
        applyProductFilter();

        Button[] activeTabs = {allCategoryButton, coffeeCategoryButton, cakeCategoryButton, juiceCategoryButton};
        Button[] activeListButtons = {allCategoryListButton, coffeeCategoryListButton, cakeCategoryListButton, juiceCategoryListButton};
        String[] tabKeys = {"ALL", "COFFEE", "CAKE", "JUICE"};
        for (int i = 0; i < tabKeys.length; i++) {
            boolean active = tabKeys[i].equals(tab);
            toggleStyleClass(activeTabs[i], "category-button-active", active);
            toggleStyleClass(activeListButtons[i], "category-list-active", active);
        }
    }

    private void toggleStyleClass(Button button, String styleClass, boolean present) {
        if (button == null) return;
        button.getStyleClass().remove(styleClass);
        if (present) button.getStyleClass().add(styleClass);
    }

    @FXML public void handleAllCategory() { setProductCategoryTab("ALL"); }
    @FXML public void handleCoffeeCategory() { setProductCategoryTab("COFFEE"); }
    @FXML public void handleCakeCategory() { setProductCategoryTab("CAKE"); }
    @FXML public void handleJuiceCategory() { setProductCategoryTab("JUICE"); }
    @FXML public void handleCategoryFilter() { applyProductFilter(); }
    @FXML public void handleStatusFilter() { applyProductFilter(); }

    @FXML
    public void handleAddProduct() {
        if (allActiveCategories.isEmpty()) {
            AlertUtil.warning("Chưa có danh mục",
                    "Vui lòng tạo ít nhất một danh mục (trong Quản lý danh mục) trước khi thêm sản phẩm.");
            return;
        }
        openProductDialog(null);
    }

    private void openEditProductDialog(Product product) {
        openProductDialog(product);
    }

    private void openProductDialog(Product existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Thêm sản phẩm" : "Chỉnh sửa sản phẩm");
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType("Lưu", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        ComboBox<Category> categoryBox = new ComboBox<>();
        categoryBox.getItems().setAll(allActiveCategories);
        if (existing != null && existing.getCategoryId() != null) {
            allActiveCategories.stream().filter(c -> c.getId() == existing.getCategoryId()).findFirst()
                    .ifPresent(categoryBox::setValue);
        }
        TextField priceField = new TextField(existing != null ? existing.getPrice().toPlainString() : "");
        TextField costField = new TextField(existing != null && existing.getCost() != null ? existing.getCost().toPlainString() : "");
        TextField stockField = new TextField(existing != null ? String.valueOf(existing.getStock()) : "0");
        TextArea descriptionField = new TextArea(existing != null ? existing.getDescription() : "");
        descriptionField.setPrefRowCount(3);
        CheckBox activeBox = new CheckBox("Đang bán");
        activeBox.setSelected(existing == null || existing.isActive());

        Label imagePathLabel = new Label(existing != null && existing.getImagePath() != null
                ? existing.getImagePath() : "Chưa chọn ảnh");
        String[] pickedImagePath = {existing != null ? existing.getImagePath() : null};
        Button pickImageButton = new Button("Chọn ảnh...");
        pickImageButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn ảnh sản phẩm");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg"));
            java.io.File picked = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (picked != null) {
                String previousPath = pickedImagePath[0];
                try {
                    // Stores a bare file name now, not an absolute path (TODO.md item 10a) - a
                    // database copied to another machine/user still finds its product images.
                    String fileName = com.coffeeshop.coffeeshopmanagement.util.ImageStorage.storeNewFile(picked);
                    pickedImagePath[0] = fileName;
                    imagePathLabel.setText(fileName);
                    // Old image is now replaced in the form; delete its file so re-picking an
                    // image repeatedly doesn't leave orphans in ~/.lunavera-coffee/images. Only
                    // happens once the new copy has actually succeeded.
                    com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(previousPath);
                } catch (IOException ex) {
                    AlertUtil.error("Không thể lưu ảnh", "Có lỗi khi sao chép ảnh: " + ex.getMessage());
                }
            }
        });
        HBox imageRow = new HBox(10, pickImageButton, imagePathLabel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.addRow(row++, new Label("Tên sản phẩm:"), nameField);
        grid.addRow(row++, new Label("Danh mục:"), categoryBox);
        grid.addRow(row++, new Label("Giá bán:"), priceField);
        grid.addRow(row++, new Label("Giá vốn:"), costField);
        grid.addRow(row++, new Label("Tồn kho:"), stockField);
        grid.addRow(row++, new Label("Mô tả:"), descriptionField);
        grid.addRow(row++, new Label("Ảnh:"), imageRow);
        grid.addRow(row, new Label(""), activeBox);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng nhập tên sản phẩm.");
                event.consume();
                return;
            }
            if (categoryBox.getValue() == null) {
                AlertUtil.warning("Thiếu thông tin", "Vui lòng chọn danh mục.");
                event.consume();
                return;
            }
            BigDecimal price;
            try {
                price = new BigDecimal(priceField.getText().trim());
                if (price.signum() <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                AlertUtil.warning("Giá không hợp lệ", "Giá bán phải là một số lớn hơn 0.");
                event.consume();
                return;
            }
            BigDecimal cost = null;
            if (!ValidationUtil.isBlank(costField.getText())) {
                try {
                    cost = new BigDecimal(costField.getText().trim());
                } catch (NumberFormatException ex) {
                    AlertUtil.warning("Giá vốn không hợp lệ", "Giá vốn phải là một số.");
                    event.consume();
                    return;
                }
            }
            int stock;
            try {
                stock = Integer.parseInt(stockField.getText().trim());
                if (stock < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                AlertUtil.warning("Tồn kho không hợp lệ", "Tồn kho phải là một số nguyên không âm.");
                event.consume();
                return;
            }

            Product product = existing != null ? existing : new Product();
            product.setName(nameField.getText().trim());
            product.setCategoryId(categoryBox.getValue().getId());
            product.setPrice(price);
            product.setCost(cost);
            product.setStock(stock);
            product.setDescription(descriptionField.getText());
            product.setImagePath(pickedImagePath[0]);
            product.setActive(activeBox.isSelected());

            try {
                if (existing == null) {
                    productDAO.insert(product);
                } else {
                    productDAO.update(product);
                }
            } catch (DataAccessException ex) {
                AlertUtil.error("Không thể lưu sản phẩm", ex.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
        reloadProducts();
    }

    private void deleteProduct(Product product) {
        int orderRefs = productDAO.countOrderReferences(product.getId());
        if (orderRefs > 0) {
            boolean confirmed = AlertUtil.confirm("Không thể xóa",
                    "Sản phẩm \"" + product.getName() + "\" đã xuất hiện trong " + orderRefs +
                            " đơn hàng và không thể xóa để giữ nguyên lịch sử đơn hàng. " +
                            "Bạn có muốn ngừng bán sản phẩm này thay vì xóa không?");
            if (confirmed) {
                productDAO.setActive(product.getId(), false);
                reloadProducts();
            }
            return;
        }
        boolean confirmed = AlertUtil.confirm("Xác nhận xóa",
                "Xóa sản phẩm \"" + product.getName() + "\"? Hành động này không thể hoàn tác.");
        if (confirmed) {
            productDAO.delete(product.getId());
            com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(product.getImagePath());
            reloadProducts();
        }
    }

    @FXML public void handleProductPreviousPage() { productPager.previousPage(); applyProductFilter(); }
    @FXML public void handleProductPageOne() { productPager.goToPage(1); applyProductFilter(); }
    @FXML public void handleProductPageTwo() { productPager.goToPage(2); applyProductFilter(); }
    @FXML public void handleProductNextPage() { productPager.nextPage(); applyProductFilter(); }
}
