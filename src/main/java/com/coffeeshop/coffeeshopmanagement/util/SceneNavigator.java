package com.coffeeshop.coffeeshopmanagement.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes "load this FXML into the current window" so every controller doesn't
 * duplicate its own copy of this logic (several of the original screens each had their own
 * near-identical switchScene method).
 */
public final class SceneNavigator {

    private static final Logger LOGGER = Logger.getLogger(SceneNavigator.class.getName());

    private SceneNavigator() {
    }

    /** Switches the scene of the window that raised the given ActionEvent. */
    public static void switchScene(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        switchScene(stage, fxmlPath);
    }

    public static void switchScene(Stage stage, String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            stage.setScene(scene);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load screen: " + fxmlPath, e);
            AlertUtil.error("Lỗi điều hướng",
                    "Không thể mở màn hình được yêu cầu. Vui lòng thử lại hoặc liên hệ quản trị viên.");
        }
    }

    /**
     * Loads an FXML file and returns its controller as well, for callers that need to push
     * data into the new screen (e.g. opening the invoice window for a specific order).
     */
    public static <T> T loadAndShowNewWindow(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.show();
            return loader.getController();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open window: " + fxmlPath, e);
            AlertUtil.error("Lỗi điều hướng", "Không thể mở cửa sổ được yêu cầu.");
            return null;
        }
    }
}
