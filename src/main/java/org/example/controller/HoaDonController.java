package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;

import javafx.fxml.FXML;
import javafx.print.PrinterJob;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backs the invoice/receipt popup (donthanhtoan.fxml). Populated after the FXML is loaded
 * by calling {@link #setInvoiceData}; there is no "open invoice #N" screen yet since order
 * creation/POS checkout is not wired up (see progress.md), so this controller is currently
 * exercised only by whatever future POS screen calls SceneNavigator.loadAndShowNewWindow(...)
 * and then this method.
 */
public class HoaDonController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @FXML
    private VBox invoiceRoot;
    @FXML
    private Label invoiceIdLabel;
    @FXML
    private Label tableLabel;
    @FXML
    private Label customerLabel;
    @FXML
    private Label orderTimeLabel;
    @FXML
    private TableView<OrderItem> invoiceDetailTable;
    @FXML
    private TableColumn<OrderItem, String> productColumn;
    @FXML
    private TableColumn<OrderItem, Number> quantityColumn;
    @FXML
    private TableColumn<OrderItem, String> priceColumn;
    @FXML
    private TableColumn<OrderItem, String> subtotalColumn;
    @FXML
    private Label subtotalLabel;
    @FXML
    private Label discountLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label paymentMethodLabel;
    @FXML
    private ImageView qrCodeImage;
    @FXML
    private Label qrOrderIdLabel;
    @FXML
    private Button printButton;
    @FXML
    private Button closeButton;

    @FXML
    private void initialize() {
        productColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(data.getValue().getQuantity()));
        priceColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));
    }

    /** Populates every field on the receipt from a completed order. */
    public void setInvoiceData(Order order, List<OrderItem> items, String employeeName, String customerName) {
        invoiceIdLabel.setText("#" + order.getId());
        tableLabel.setText(employeeName != null ? employeeName : "-");
        customerLabel.setText(customerName != null ? customerName : "Khách lẻ");
        orderTimeLabel.setText(order.getOrderDate() != null ? order.getOrderDate().format(TIME_FORMAT) : "-");

        invoiceDetailTable.getItems().setAll(items);

        subtotalLabel.setText(CurrencyUtil.format(order.getSubtotal()));
        discountLabel.setText(CurrencyUtil.format(order.getDiscount()));
        totalLabel.setText(CurrencyUtil.format(order.getTotal()));
        PaymentMethod method = order.getPaymentMethod();
        paymentMethodLabel.setText(method == PaymentMethod.CASH ? "Tiền mặt"
                : method == PaymentMethod.CARD ? "Thẻ" : "-");

        qrOrderIdLabel.setText("#" + order.getId());
        // No QR-code generation library is included in this project (adding one was out of
        // scope for this pass - see progress.md), so qrCodeImage intentionally stays blank
        // rather than showing a fake placeholder graphic.
    }

    @FXML
    public void handlePrint() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.error("Không thể in", "Không tìm thấy máy in nào trên hệ thống.");
            return;
        }
        boolean proceed = job.showPrintDialog(invoiceRoot.getScene().getWindow());
        if (!proceed) {
            return;
        }
        boolean printed = job.printPage(invoiceRoot);
        if (printed) {
            job.endJob();
            AlertUtil.info("In hóa đơn", "Đã gửi hóa đơn tới máy in.");
        } else {
            AlertUtil.error("Không thể in", "Có lỗi xảy ra khi in hóa đơn.");
        }
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}
