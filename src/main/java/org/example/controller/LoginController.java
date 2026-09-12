package org.example.controller;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button showPasswordButton;
    @FXML
    private CheckBox rememberCheckBox;
    @FXML
    private Hyperlink forgotPasswordLink;
    @FXML
    private Button signInButton;
    @FXML
    private Hyperlink registerLink;
    @FXML
    private void initialize() {
        signInButton.setOnAction(event -> handleLogin());
        registerLink.setOnAction(event -> handleRegister());
        forgotPasswordLink.setOnAction(event -> handleForgotPassword());
        showPasswordButton.setOnAction(event -> handleShowPassword());
    }
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        if (username.isBlank()) {
            showAlert("Thông báo", "Vui lòng nhập đầy đủ thông tin.");
            return;
        } else if (password.isBlank()) {
            showAlert("Thông báo", "Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        System.out.println("Login: " + username);
    }
    private void handleRegister() {
        System.out.println("Open register screen");
    }
    private void handleForgotPassword() {
        System.out.println("Forgot password");
    }
    private void handleShowPassword() {
        System.out.println("Show / hide password");
    }
    private void showAlert(String title, String message) {
        // tạo hộp thoại thông báo cho người dùng
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}