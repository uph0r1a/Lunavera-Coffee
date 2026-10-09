package org.example.controller;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PasswordUtil;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;

/**
 * Shown right after someone logs in with the well-known seeded password (Admin@123). Offers to
 * set a real one immediately; "Để sau" (later) is allowed so a shop isn't locked out of a
 * working demo, but the prompt returns on every such login until the password is changed.
 */
final class DefaultPasswordPrompt {

    private DefaultPasswordPrompt() {
    }

    static void show(User user) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Đổi mật khẩu mặc định");
        AlertUtil.configure(dialog);
        ButtonType saveType = new ButtonType("Đổi mật khẩu", ButtonData.OK_DONE);
        ButtonType laterType = new ButtonType("Để sau", ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, laterType);
        AlertUtil.setDefaultButton(dialog, saveType);
        AlertUtil.setCancelButton(dialog, laterType);

        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Bạn đang dùng mật khẩu mặc định (ai cũng biết). Hãy đặt mật khẩu mới."), 0, 0, 2, 1);
        grid.addRow(1, new Label("Mật khẩu mới:"), newField);
        grid.addRow(2, new Label("Nhập lại:"), confirmField);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String password = newField.getText();
            if (!ValidationUtil.isValidPassword(password)) {
                AlertUtil.warning("Mật khẩu yếu", "Mật khẩu phải có ít nhất 6 ký tự, gồm cả chữ và số.");
                event.consume();
            } else if (DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password)) {
                AlertUtil.warning("Mật khẩu không hợp lệ", "Vui lòng chọn mật khẩu khác mật khẩu mặc định.");
                event.consume();
            } else if (!password.equals(confirmField.getText())) {
                AlertUtil.warning("Không khớp", "Mật khẩu nhập lại không khớp.");
                event.consume();
            } else {
                try {
                    new UserDAO().updatePasswordHash(user.getId(), PasswordUtil.hash(password));
                } catch (DataAccessException e) {
                    AlertUtil.error("Không thể đổi mật khẩu", e.getMessage());
                    event.consume();
                }
            }
        });
        dialog.showAndWait();
    }
}
