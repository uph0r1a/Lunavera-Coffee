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
    private Button loginButton;
    @FXML
    private Hyperlink registerLink;

    @FXML
    private void initialize() {
        if (signInButton != null) signInButton.setOnAction(event -> handleLogin());
        if (loginButton != null) loginButton.setOnAction(event -> handleLogin());
        if (registerLink != null) registerLink.setOnAction(event -> handleRegister());
        if (forgotPasswordLink != null) forgotPasswordLink.setOnAction(event -> handleForgotPassword());
        if (showPasswordButton != null) showPasswordButton.setOnAction(event -> handleShowPassword());
    }

    @FXML
    public void handleLogin() {
        String username = usernameField != null ? usernameField.getText() : "";
        String password = passwordField != null ? passwordField.getText() : "";
        if (username.isBlank() || password.isBlank()) {
            showAlert("Thông báo", "Vui lòng nhập đầy đủ thông tin.");
            return;
        }
        System.out.println("Login: " + username);
    }

    @FXML
    public void handleRegister() {
        System.out.println("Open register screen");
    }

    @FXML
    public void handleForgotPassword() {
        System.out.println("Forgot password");
    }

    @FXML
    public void handleShowPassword() {
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