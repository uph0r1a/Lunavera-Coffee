package com.coffeeshop.coffeeshopmanagement.util;

import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;

/**
 * Show/hide-password toggle. A PasswordField can't reveal its text, so a plain TextField bound
 * to the same text is placed next to it (same style classes, same parent, same position) and
 * the two are swapped by the eye button. Typed text, length limit and focus carry over.
 *
 * <p>Styling contract (see dangnhap.css): the parent container is the visible
 * "input box" ({@code .password-box}), the two fields inside it are borderless
 * ({@code .password-inner}) and the eye button sits flush at its right edge
 * ({@code .password-eye}), so the three read as one control. The box gets the
 * {@code :focused-within} pseudo-class while either field has focus, for the focus ring.
 */
public final class PasswordReveal {

    private static final PseudoClass FOCUSED_WITHIN = PseudoClass.getPseudoClass("focused-within");
    /** 16x16 outline eye (lens + pupil); the "hidden" icon adds a slash. */
    private static final String EYE = "M0.7 8 Q8 -1 15.3 8 Q8 17 0.7 8 Z M8 5.8 A2.2 2.2 0 1 0 8 10.2 A2.2 2.2 0 1 0 8 5.8 Z";
    private static final String SLASH = " M2.5 2.5 L13.5 13.5";

    private PasswordReveal() {
    }

    public static void install(PasswordField passwordField, Button toggleButton) {
        if (passwordField == null || toggleButton == null || !(passwordField.getParent() instanceof Pane parent)) {
            return;
        }
        TextField plainField = new TextField();
        plainField.getStyleClass().setAll(passwordField.getStyleClass());
        plainField.setPromptText(passwordField.getPromptText());
        plainField.setPrefHeight(passwordField.getPrefHeight());
        plainField.setPrefWidth(passwordField.getPrefWidth());
        plainField.setMaxWidth(passwordField.getMaxWidth());
        HBox.setHgrow(plainField, HBox.getHgrow(passwordField));
        if (passwordField.getTextFormatter() != null) {
            // Same length cap on the revealed copy (call limit() before install()).
            plainField.setTextFormatter(new TextFormatter<>(passwordField.getTextFormatter().getFilter()));
        }
        plainField.textProperty().bindBidirectional(passwordField.textProperty());
        plainField.setOnAction(e -> {
            if (passwordField.getOnAction() != null) {
                passwordField.getOnAction().handle(e);
            }
        });
        plainField.setVisible(false);
        plainField.setManaged(false);
        parent.getChildren().add(parent.getChildren().indexOf(passwordField) + 1, plainField);

        // Focus ring on the whole box, not on whichever field happens to be visible.
        Runnable updateFocus = () -> parent.pseudoClassStateChanged(FOCUSED_WITHIN,
                passwordField.isFocused() || plainField.isFocused());
        passwordField.focusedProperty().addListener((obs, was, now) -> updateFocus.run());
        plainField.focusedProperty().addListener((obs, was, now) -> updateFocus.run());

        toggleButton.setText("");
        toggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        toggleButton.setFocusTraversable(false);
        toggleButton.setGraphic(icon(false));
        toggleButton.setOnAction(e -> {
            boolean show = !plainField.isVisible();
            plainField.setVisible(show);
            plainField.setManaged(show);
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            toggleButton.setGraphic(icon(show));
            TextField active = show ? plainField : passwordField;
            active.requestFocus();
            active.positionCaret(passwordField.getText().length());
        });
    }

    /** Open eye while the password is hidden (click to show); crossed-out eye while it is visible. */
    private static SVGPath icon(boolean passwordShown) {
        SVGPath path = new SVGPath();
        path.setContent(passwordShown ? EYE + SLASH : EYE);
        path.getStyleClass().add("password-eye-icon");
        return path;
    }

    /** Length cap for a password field. Call before {@link #install}, so the revealed copy inherits it. */
    public static void limit(PasswordField passwordField, int max) {
        TextLengthLimiter.limit(passwordField, max);
    }
}
