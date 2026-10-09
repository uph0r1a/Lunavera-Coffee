package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;

import java.util.Optional;

public class AuthenticationService {

    /**
     * {@code INVALID_CREDENTIALS} covers both "no such user" and "wrong password" on purpose, so the
     * login screen cannot be used to find out which usernames exist.
     */
    public enum LoginStatus { SUCCESS, INVALID_CREDENTIALS, INACTIVE, TOO_MANY_ATTEMPTS }

    public record LoginResult(LoginStatus status, User user, long retryAfterSeconds) {
        public static LoginResult of(LoginStatus status) {
            return new LoginResult(status, null, 0);
        }
    }

    /** Shared by every login screen instance, so logging out and back in does not reset the delay. */
    private static final LoginThrottle SHARED_THROTTLE = new LoginThrottle();

    /** Hash of a throwaway password, checked when the username is unknown so both cases take equally long. */
    private static String dummyHash;

    private static synchronized String dummyHash() {
        if (dummyHash == null) {
            dummyHash = PasswordUtil.hash("lunavera-not-a-real-password");
        }
        return dummyHash;
    }

    private final UserDAO userDAO;
    private final LoginThrottle throttle;

    public AuthenticationService() {
        this(new UserDAO(), SHARED_THROTTLE);
    }

    public AuthenticationService(UserDAO userDAO) {
        this(userDAO, SHARED_THROTTLE);
    }

    public AuthenticationService(UserDAO userDAO, LoginThrottle throttle) {
        this.userDAO = userDAO;
        this.throttle = throttle;
    }

    public LoginResult login(String username, String password) {
        String name = username == null ? "" : username.trim();
        long wait = throttle.secondsUntilAllowed(name);
        if (wait > 0) {
            return new LoginResult(LoginStatus.TOO_MANY_ATTEMPTS, null, wait);
        }
        Optional<User> found = userDAO.findByUsername(name);
        if (found.isEmpty()) {
            PasswordUtil.matches(password, dummyHash()); // same cost as a real check
            throttle.recordFailure(name);
            return LoginResult.of(LoginStatus.INVALID_CREDENTIALS);
        }
        User user = found.get();
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            throttle.recordFailure(name);
            return LoginResult.of(LoginStatus.INVALID_CREDENTIALS);
        }
        // A locked account is only reported to someone who knows its password.
        if (user.getStatus() == AccountStatus.LOCKED) {
            return LoginResult.of(LoginStatus.INACTIVE);
        }
        throttle.recordSuccess(name);
        return new LoginResult(LoginStatus.SUCCESS, user, 0);
    }
}
