package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Account status used to be checked only at login, so an admin locking (or deleting) an account
 * did nothing to that person's already-open session (TODO.md item 4). This re-reads the account
 * from the database and enforces it at the moments that matter:
 * <ul>
 *   <li>every screen switch ({@link SceneNavigator}),</li>
 *   <li>taking a payment and admin-only actions (their controllers call {@link #validateNow()}),</li>
 *   <li>a periodic background check, so someone idling on one screen is still caught.</li>
 * </ul>
 * It also refreshes the session's role, so demoting an admin takes effect without a re-login.
 *
 * Deliberately <b>fails open</b> on a database error: a transient SQLite hiccup shouldn't throw
 * a cashier out mid-sale. Only a definite "locked" or "account no longer exists" answer does.
 */
public final class SessionGuard {

    private static final Logger LOGGER = Logger.getLogger(SessionGuard.class.getName());
    private static final Duration CHECK_INTERVAL = Duration.seconds(30);

    private enum Check { OK, INVALID, UNKNOWN }

    private record Snapshot(Check check, User fresh) {
    }

    private static Stage primaryStage;
    private static Timeline watcher;

    private SessionGuard() {
    }

    /** Pure database read - safe to run off the FX thread. */
    private static Snapshot fetch() {
        User current = Session.getCurrentUser();
        if (current == null) {
            return new Snapshot(Check.INVALID, null);
        }
        try {
            Optional<User> fresh = new UserDAO().findById(current.getId());
            if (fresh.isEmpty() || fresh.get().getStatus() == AccountStatus.LOCKED) {
                return new Snapshot(Check.INVALID, null);
            }
            return new Snapshot(Check.OK, fresh.get());
        } catch (DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not re-validate the session; leaving it as is", e);
            return new Snapshot(Check.UNKNOWN, null);
        }
    }

    /** Must run on the FX thread (it updates {@link Session}). Returns false if the account
     *  is locked or gone. */
    private static boolean apply(Snapshot snapshot) {
        if (snapshot.check() == Check.OK) {
            Session.start(snapshot.fresh(), Session.getCurrentEmployee());
        }
        return snapshot.check() != Check.INVALID;
    }

    /** Synchronous check: one indexed single-row read, cheap enough for a click handler. */
    public static boolean validateNow() {
        return apply(fetch());
    }

    /** Ends the session and returns to the login screen. */
    public static void forceLogout() {
        Session.clear();
        stopWatching();
        Stage stage = primaryStage;
        primaryStage = null;
        if (stage != null) {
            // The POS screen installs a "you have an unpaid cart" close guard on the window;
            // the session is already over, so it must not block or outlive this.
            stage.setOnCloseRequest(null);
        }
        AlertUtil.warning("Phiên đăng nhập đã kết thúc",
                "Tài khoản của bạn đã bị khóa hoặc không còn tồn tại. " +
                        "Bạn sẽ được đưa về màn hình đăng nhập.");
        if (stage != null) {
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }

    /** Call right after a successful login. */
    public static void startWatching(Stage stage) {
        stopWatching();
        primaryStage = stage;
        watcher = new Timeline(new KeyFrame(CHECK_INTERVAL, e -> tick()));
        watcher.setCycleCount(Animation.INDEFINITE);
        watcher.play();
    }

    private static void stopWatching() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    private static void tick() {
        if (Session.getCurrentUser() == null) {
            stopWatching(); // logged out normally
            return;
        }
        // Don't yank the screen out from under an open dialog / invoice / history window;
        // just try again on the next tick.
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window != primaryStage) {
                return;
            }
        }
        Async.run(
                SessionGuard::fetch,
                snapshot -> {
                    if (Session.getCurrentUser() != null && !apply(snapshot)) {
                        forceLogout();
                    }
                },
                error -> LOGGER.log(Level.WARNING, "Session watcher check failed", error));
    }
}
