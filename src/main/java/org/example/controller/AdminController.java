package org.example.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AdminController {

    private void switchScene(ActionEvent event, String fxmlPath) {
        try {
            Node source = (Node) event.getSource();
            Stage stage = (Stage) source.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void openDashboard(ActionEvent event) {
        switchScene(event, "/fxml/admin-trangchu.fxml");
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
        switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void handleProducts(ActionEvent event) {
        openProductManagement(event);
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        switchScene(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void handleOrders(ActionEvent event) {
        openOrderManagement(event);
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        switchScene(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void handleCategories(ActionEvent event) {
        openCategoryManagement(event);
    }

    @FXML
    public void openInventoryManagement(ActionEvent event) {
        System.out.println("Mở Quản lý kho");
    }

    @FXML
    public void openEmployeeManagement(ActionEvent event) {
        System.out.println("Mở Quản lý nhân viên");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void handleAccounts(ActionEvent event) {
        openAccountManagement(event);
    }

    @FXML
    public void logout(ActionEvent event) {
        switchScene(event, "/fxml/dangnhap.fxml");
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        logout(event);
    }

    @FXML
    public void handleNavigation(ActionEvent event) {
        // Fallback generic navigation handler
    }

    // Handlers for QuanLyDanhMuc
    @FXML public void handleSearchAction() {}
    @FXML public void handleAddCategory() {}
    @FXML public void handlePreviousPage() {}
    @FXML public void handlePageOne() {}
    @FXML public void handleNextPage() {}
    @FXML public void handleTableClick() {}

    // Handlers for QuanLyDonHang
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

    // Handlers for QuanLySanPham
    @FXML public void handleAllCategory() {}
    @FXML public void handleCoffeeCategory() {}
    @FXML public void handleCakeCategory() {}
    @FXML public void handleJuiceCategory() {}
    @FXML public void handleCategoryFilter() {}
    @FXML public void handleStatusFilter() {}
    @FXML public void handleAddProduct() {}
    @FXML public void handlePageTwo() {}

    // Handlers for QuanLyTaiKhoan
    @FXML public void handleAccountSearch() {}
    @FXML public void handleFilterChange() {}
    @FXML public void handleAddAccount() {}
    @FXML public void handlePreviousAccountPage() {}
    @FXML public void handleAccountPageOne() {}
    @FXML public void handleAccountPageTwo() {}
    @FXML public void handleNextAccountPage() {}
    @FXML public void handleCloseDetail() {}
    @FXML public void handleEditAccount() {}
    @FXML public void handleLockAccount() {}
    @FXML public void handleSelectAccount() {}
}
