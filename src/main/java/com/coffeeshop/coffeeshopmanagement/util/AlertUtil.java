package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Every alert/dialog here is tied to the currently focused window and given an explicit
 * {@code Modality.WINDOW_MODAL} rather than being left owner-less on JavaFX's default
 * {@code Modality.APPLICATION_MODAL}. Without an owner, the window manager has no real window
 * geometry to tie its input grab to; on some Linux window managers/compositors,
 * APPLICATION_MODAL in particular has been observed to over-confine the pointer to the dialog's
 * bounds rather than just blocking clicks to the app behind it. WINDOW_MODAL only needs to
 * relate the grab to one specific window, which behaves better in that case.
 */
public final class AlertUtil {

    private AlertUtil() {
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    public static void warning(String title, String message) {
        show(Alert.AlertType.WARNING, title, message);
    }

    public static boolean confirm(String title, String message) {
        // Custom labels: the built-in YES/NO buttons follow the OS language ("Yes/No" on English systems).
        ButtonType yes = new ButtonType("Có", javafx.scene.control.ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Không", javafx.scene.control.ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yes, no);
        alert.setTitle(title);
        alert.setHeaderText(null);
        configure(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        configure(alert);
        alert.showAndWait();
    }

    /** Applies the same owner + modality treatment to any other Dialog (e.g. the custom
     *  add/edit-category and add/edit-account forms), so every popup in the app is consistent. */
    public static void configure(Dialog<?> dialog) {
        Window owner = activeWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.WINDOW_MODAL);
    }

    /** The window the user was actually looking at when the alert was triggered - whichever
     *  currently-open JavaFX window reports itself as focused. */
    private static Window activeWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isFocused()) {
                return window;
            }
        }
        return null;
    }
}


