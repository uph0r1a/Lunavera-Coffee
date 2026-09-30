package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.DashboardStats;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.DashboardWidgets;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Backs the employee dashboard (employee-trangchu.fxml). Mirrors AdminController's
 * navigation/stat-loading pattern; the two are not merged into one class because the
 * screens each are allowed to reach differ by role (see progress.md - Account Management
 * currently applies its own admin-only checks for the actions employees aren't allowed to
 * perform, rather than being hidden here).
 */
public class EmployeeController {

    @FXML
    private Label dashboardProductCountLabel;
    @FXML
    private Label dashboardLowStockNoteLabel;
    @FXML
    private Label todayOrderLabel;
    @FXML
    private Label todayRevenueLabel;
    @FXML
    private Label todayCustomerLabel;
    @FXML
    private Label recentOrdersNoteLabel;
    @FXML
    private Label dashboardGreetingLabel;
    @FXML
    private Label dashboardAccountNameLabel;
    @FXML
    private GridPane homeTableGrid;
    @FXML
    private VBox lowStockList;

    private final DashboardStatsService dashboardStatsService = new DashboardStatsService();
    private final TableDAO tableDAO = new TableDAO();

    @FXML
    private void initialize() {
        if (dashboardGreetingLabel != null) {
            dashboardGreetingLabel.setText("Chào mừng, " + Session.getDisplayName() + "!");
        }
        if (dashboardAccountNameLabel != null) {
            dashboardAccountNameLabel.setText(Session.getDisplayName());
        }
        loadDashboardStats();
    }

    private void loadDashboardStats() {
        // See AdminController for why this is backgrounded: loadStats() makes ~10 sequential
        // DB round-trips, enough to freeze the UI for a moment if run on the FX thread.
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
        // Real data replaces the old hardcoded table-occupancy grid and fake low-stock rows
        // (progress.md, Session 13) - same content as AdminController's dashboard.
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
        loadHomeTableGrid();
    }

    private void loadHomeTableGrid() {
        if (homeTableGrid == null) return;
        Async.run(
                tableDAO::findAll,
                tables -> DashboardWidgets.fillTableGrid(homeTableGrid, tables, this::handleHomeTableClick),
                error -> AlertUtil.error("Lỗi", "Không thể tải sơ đồ bàn: " + error.getMessage())
        );
    }

    private void handleHomeTableClick(DiningTable table) {
        if (homeTableGrid == null || homeTableGrid.getScene() == null) return;
        javafx.stage.Stage stage = (javafx.stage.Stage) homeTableGrid.getScene().getWindow();
        OrderController controller = SceneNavigator.switchSceneAndGetController(stage, "/fxml/quanlydonhang.fxml");
        if (controller != null) {
            controller.selectTable(table.getTableNumber());
        }
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
    public void openCustomerManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlykhachhang.fxml");
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
    public void openAccountManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        Session.clear();
        SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
    }
}
