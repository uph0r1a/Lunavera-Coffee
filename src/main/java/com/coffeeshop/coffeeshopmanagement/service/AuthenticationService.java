package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.util.Optional;

public class AuthenticationService {

    public enum LoginStatus { SUCCESS, INVALID_USERNAME, INVALID_PASSWORD, INACTIVE }

    public enum RegisterStatus { SUCCESS, INVALID_INPUT, PASSWORD_MISMATCH, WEAK_PASSWORD, DUPLICATE_USERNAME }

    public record LoginResult(LoginStatus status, User user) {
        public static LoginResult of(LoginStatus status) {
            return new LoginResult(status, null);
        }
    }

    public record RegisterResult(RegisterStatus status, String message) {
    }

    private final UserDAO userDAO;
    private final CustomerDAO customerDAO;

    public AuthenticationService() {
        this(new UserDAO(), new CustomerDAO());
    }

    public AuthenticationService(UserDAO userDAO, CustomerDAO customerDAO) {
        this.userDAO = userDAO;
        this.customerDAO = customerDAO;
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

    /**
     * Public self-registration (the "dangky" screen). Creates a linked Customer + User
     * account with the CUSTOMER role. Admin/employee accounts are created separately, from
     * Account Management, by an administrator.
     */
    public RegisterResult register(String fullName, String phone, String username, String password,
                                    String confirmPassword) {
        if (ValidationUtil.isBlank(fullName) || ValidationUtil.isBlank(username) || ValidationUtil.isBlank(password)) {
            return new RegisterResult(RegisterStatus.INVALID_INPUT, "Vui lòng nhập đầy đủ thông tin bắt buộc.");
        }
        if (!ValidationUtil.isBlank(phone) && !ValidationUtil.isValidPhone(phone)) {
            return new RegisterResult(RegisterStatus.INVALID_INPUT, "Số điện thoại không hợp lệ.");
        }
        if (!ValidationUtil.isValidUsername(username)) {
            return new RegisterResult(RegisterStatus.INVALID_INPUT,
                    "Tên đăng nhập phải từ 4-32 ký tự, chỉ gồm chữ, số và dấu gạch dưới.");
        }
        if (!password.equals(confirmPassword)) {
            return new RegisterResult(RegisterStatus.PASSWORD_MISMATCH, "Mật khẩu nhập lại không khớp.");
        }
        if (!ValidationUtil.isValidPassword(password)) {
            return new RegisterResult(RegisterStatus.WEAK_PASSWORD,
                    "Mật khẩu phải có ít nhất 6 ký tự, gồm cả chữ và số.");
        }
        if (!ValidationUtil.isBlank(phone) && customerDAO.existsByPhone(phone.trim(), 0)) {
            return new RegisterResult(RegisterStatus.INVALID_INPUT, "Số điện thoại này đã được đăng ký.");
        }
        if (userDAO.existsByUsername(username.trim())) {
            return new RegisterResult(RegisterStatus.DUPLICATE_USERNAME, "Tên đăng nhập đã tồn tại.");
        }

        Customer customer = new Customer();
        customer.setFullName(fullName.trim());
        customer.setPhone(ValidationUtil.isBlank(phone) ? null : phone.trim());
        customer.setLoyaltyPoints(0);
        customerDAO.insert(customer);

        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setRole(Role.CUSTOMER);
        user.setStatus(AccountStatus.ACTIVE);
        user.setCustomerId(customer.getId());
        userDAO.insert(user);

        return new RegisterResult(RegisterStatus.SUCCESS, "Đăng ký thành công! Vui lòng đăng nhập.");
    }
}
