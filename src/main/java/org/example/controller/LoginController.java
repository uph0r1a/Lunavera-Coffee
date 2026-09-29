package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginResult;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.Optional;

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

    private final AuthenticationService authenticationService = new AuthenticationService();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    @FXML
    private void initialize() {
        if (usernameField != null && passwordField != null) {
            usernameField.setOnAction(event -> passwordField.requestFocus());
        }
        if (passwordField != null) passwordField.setOnAction(event -> handleLogin());
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
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            AlertUtil.warning("Thông báo", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        // Password verification (PBKDF2, ~120,000 iterations) is deliberately slow to resist
        // brute-forcing, which means it's slow enough to freeze the UI if run directly on the
        // FX thread. Run it in the background and only touch the UI again once it's done -
        // Async guarantees these callbacks land back on the FX thread.
        setLoginInProgress(true);
        String trimmedUsername = username.trim();
        boolean usedDefaultPassword = DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password);
        Async.run(
                () -> authenticationService.login(trimmedUsername, password),
                result -> onLoginFinished(result, usedDefaultPassword),
                error -> {
                    setLoginInProgress(false);
                    AlertUtil.error("Lỗi đăng nhập",
                            "Đã xảy ra lỗi khi kiểm tra tài khoản. Vui lòng thử lại.");
                }
        );
    }

    private void onLoginFinished(LoginResult result, boolean usedDefaultPassword) {
        setLoginInProgress(false);
        switch (result.status()) {
            case INVALID_USERNAME -> AlertUtil.error("Đăng nhập thất bại", "Tên đăng nhập không tồn tại.");
            case INVALID_PASSWORD -> AlertUtil.error("Đăng nhập thất bại", "Mật khẩu không chính xác.");
            case INACTIVE -> AlertUtil.error("Tài khoản bị khóa",
                    "Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
            case SUCCESS -> onLoginSuccess(result.user(), usedDefaultPassword);
        }
    }

    /** Disables the button and swaps its label while a login attempt is in flight, so the
     *  window still feels responsive instead of appearing stuck. */
    private void setLoginInProgress(boolean inProgress) {
        Button active = loginButton != null ? loginButton : signInButton;
        if (active == null) {
            return;
        }
        active.setDisable(inProgress);
        active.setText(inProgress ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP");
    }

    private void onLoginSuccess(User user, boolean usedDefaultPassword) {
        if (user.getRole() == Role.CUSTOMER) {
            // No customer-facing screen exists yet in this build; documented in progress.md.
            AlertUtil.info("Chưa hỗ trợ",
                    "Tài khoản khách hàng hiện chưa có màn hình riêng. Vui lòng đăng nhập bằng " +
                            "tài khoản nhân viên hoặc quản trị viên.");
            return;
        }

        Employee employee = user.getEmployeeId() != null
                ? employeeDAO.findById(user.getEmployeeId()).orElse(null)
                : null;
        Session.start(user, employee);

        Stage stage = currentStage();
        if (stage == null) {
            return;
        }
        String target = user.getRole() == Role.ADMIN
                ? "/fxml/admin-trangchu.fxml"
                : "/fxml/employee-trangchu.fxml";
        SessionGuard.startWatching(stage);
        SceneNavigator.switchScene(stage, target);
        if (usedDefaultPassword) {
            javafx.application.Platform.runLater(() -> DefaultPasswordPrompt.show(user));
        }
    }

    @FXML
    public void handleRegister() {
        Stage stage = currentStage();
        if (stage != null) {
            SceneNavigator.switchScene(stage, "/fxml/dangky.fxml");
        }
    }

    @FXML
    public void handleForgotPassword() {
        AlertUtil.info("Quên mật khẩu",
                "Tính năng tự đặt lại mật khẩu chưa được triển khai. Vui lòng liên hệ quản trị viên " +
                        "để được đặt lại mật khẩu thủ công.");
    }

    @FXML
    public void handleShowPassword() {
        // No plain-text sibling field exists in this screen's FXML for the password field, so
        // there is nothing to toggle to yet; kept as a no-op placeholder rather than removed,
        // since dangnhap.fxml does not currently wire this button at all.
    }

    private Stage currentStage() {
        Node anchor = loginButton != null ? loginButton
                : signInButton != null ? signInButton
                : usernameField;
        if (anchor == null || anchor.getScene() == null) {
            return null;
        }
        return (Stage) anchor.getScene().getWindow();
    }
}
