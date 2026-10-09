package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;

import java.util.Optional;

public class AuthenticationService {

    public enum LoginStatus { SUCCESS, INVALID_USERNAME, INVALID_PASSWORD, INACTIVE }

    public record LoginResult(LoginStatus status, User user) {
        public static LoginResult of(LoginStatus status) {
            return new LoginResult(status, null);
        }
    }

    private final UserDAO userDAO;

    public AuthenticationService() {
        this(new UserDAO());
    }

    public AuthenticationService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public LoginResult login(String username, String password) {
        Optional<User> found = userDAO.findByUsername(username == null ? "" : username.trim());
        if (found.isEmpty()) {
            return LoginResult.of(LoginStatus.INVALID_USERNAME);
        }
        User user = found.get();
        if (user.getStatus() == AccountStatus.LOCKED) {
            return LoginResult.of(LoginStatus.INACTIVE);
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            return LoginResult.of(LoginStatus.INVALID_PASSWORD);
        }
        return new LoginResult(LoginStatus.SUCCESS, user);
    }
}
