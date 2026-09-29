package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.RegisterResult;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Backs the public self-registration screen (dangky.fxml). Creates a linked Customer + User
 * (role CUSTOMER) account - see AuthenticationService for why that role has no dashboard yet.
 */
public class RegisterController {

    @FXML
    private TextField fullNameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Button showPasswordButton;
    @FXML
    private Button showConfirmPasswordButton;
    @FXML
    private Button registerButton;
    @FXML
    private Hyperlink loginLink;

    private final AuthenticationService authenticationService = new AuthenticationService();

    @FXML
    private void initialize() {
        if (confirmPasswordField != null) confirmPasswordField.setOnAction(event -> handleRegister());
        if (registerButton != null) registerButton.setOnAction(event -> handleRegister());
        if (loginLink != null) loginLink.setOnAction(event -> handleBackToLogin());
        if (showPasswordButton != null) {
            showPasswordButton.setOnAction(event -> togglePasswordVisible(passwordField));
        }
        if (showConfirmPasswordButton != null) {
            showConfirmPasswordButton.setOnAction(event -> togglePasswordVisible(confirmPasswordField));
        }
    }

    @FXML
    public void handleRegister() {
        String fullName = textOf(fullNameField);
        String phone = textOf(phoneField);
        String username = textOf(usernameField);
        String password = passwordField != null ? passwordField.getText() : "";
        String confirmPassword = confirmPasswordField != null ? confirmPasswordField.getText() : "";

        RegisterResult result = authenticationService.register(fullName, phone, username, password, confirmPassword);
        if (result.status() == AuthenticationService.RegisterStatus.SUCCESS) {
            AlertUtil.info("Đăng ký thành công", result.message());
            handleBackToLogin();
        } else {
            AlertUtil.error("Không thể đăng ký", result.message());
        }
    }

    @FXML
    public void handleBackToLogin() {
        Stage stage = stageOf(loginLink != null ? loginLink : registerButton);
        if (stage != null) {
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }

    /**
     * PasswordField has no built-in "reveal" mode in JavaFX; a full implementation would swap
     * in a matching TextField bound to the same text. That companion field is not part of the
     * current FXML, so for now this only confirms the click was received rather than silently
     * doing nothing - full show/hide is left for a future pass (see progress.md).
     */
    private void togglePasswordVisible(PasswordField field) {
        AlertUtil.info("Chưa hỗ trợ", "Hiện chưa hỗ trợ hiện mật khẩu dạng văn bản thường.");
    }

    private String textOf(TextField field) {
        return field != null ? field.getText() : "";
    }

    private Stage stageOf(javafx.scene.Node node) {
        return (node != null && node.getScene() != null) ? (Stage) node.getScene().getWindow() : null;
    }
}
