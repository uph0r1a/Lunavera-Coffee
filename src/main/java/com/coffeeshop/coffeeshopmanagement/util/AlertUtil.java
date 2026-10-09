package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
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
 * relate the grab to one specific window, which behaves better in that case. On Linux that is
 * still not enough, so there dialogs use JavaFX-side "soft" modality instead - see
 * {@link #configure}.
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
        setDefaultButton(alert, yes);
        setCancelButton(alert, no);
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
        if (useSoftModality() && owner != null && owner.getScene() != null) {
            applySoftModality(dialog, owner.getScene().getRoot());
        } else {
            dialog.initModality(Modality.WINDOW_MODAL);
        }
    }

    /**
     * On Linux, any native modal window makes GTK grab the pointer, and some window managers
     * then confine the mouse to the dialog (the "dialogs trap the mouse" bug). So there the
     * dialog is opened non-modal and "modality" is done in JavaFX instead: the owner window's
     * content is disabled while the dialog is showing (it can't be clicked or typed into), and
     * re-enabled when the dialog closes. {@code showAndWait()} still blocks the caller as before.
     * Windows/macOS keep real WINDOW_MODAL. Set {@code -Dlunavera.nativeModal=true} to force the
     * native behaviour on Linux too.
     */
    private static boolean useSoftModality() {
        if (Boolean.getBoolean("lunavera.nativeModal")) {
            return false;
        }
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    private static void applySoftModality(Dialog<?> dialog, Node ownerRoot) {
        dialog.initModality(Modality.NONE);
        boolean[] wasDisabled = new boolean[1];
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, e -> {
            wasDisabled[0] = ownerRoot.isDisable();
            ownerRoot.setDisable(true);
        });
        dialog.addEventHandler(DialogEvent.DIALOG_HIDDEN, e -> ownerRoot.setDisable(wasDisabled[0]));
    }

    /** Makes Enter trigger this button. */
    public static void setDefaultButton(Dialog<?> dialog, ButtonType buttonType) {
        Node node = dialog.getDialogPane().lookupButton(buttonType);
        if (node instanceof Button button) {
            button.setDefaultButton(true);
        }
    }

    /** Makes Esc trigger this button. */
    public static void setCancelButton(Dialog<?> dialog, ButtonType buttonType) {
        Node node = dialog.getDialogPane().lookupButton(buttonType);
        if (node instanceof Button button) {
            button.setCancelButton(true);
        }
    }

    /** Esc closes a stand-alone window (receipt, history, report) - dialogs get this from their
     *  cancel button, but a plain Stage has no such thing. Combo/date popups consume Esc first. */
    public static void closeOnEscape(javafx.scene.Scene scene) {
        scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && scene.getWindow() != null) {
                event.consume();
                scene.getWindow().hide();
            }
        });
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


