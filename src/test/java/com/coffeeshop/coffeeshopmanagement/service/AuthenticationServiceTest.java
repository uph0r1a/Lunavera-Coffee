package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginResult;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Uses an in-memory stand-in for the user table, so no database is needed. */
public class AuthenticationServiceTest {

    private User user;
    private AuthenticationService service;

    @Before
    public void setUp() {
        user = new User();
        user.setUsername("staff1");
        user.setPasswordHash(PasswordUtil.hash("Correct1"));
        user.setStatus(AccountStatus.ACTIVE);
        UserDAO fake = new UserDAO() {
            @Override
            public Optional<User> findByUsername(String username) {
                return "staff1".equals(username) ? Optional.of(user) : Optional.empty();
            }
        };
        service = new AuthenticationService(fake, new LoginThrottle());
    }

    @Test
    public void correctPasswordSucceeds() {
        LoginResult result = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.SUCCESS, result.status());
        assertNotNull(result.user());
    }

    @Test
    public void unknownUserAndWrongPasswordGiveTheSameAnswer() {
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("nobody", "Whatever1").status());
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
    }

    @Test
    public void aLockedAccountIsOnlyReportedToSomeoneWithItsPassword() {
        user.setStatus(AccountStatus.LOCKED);
        assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
        LoginResult result = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.INACTIVE, result.status());
        assertNull(result.user());
    }

    @Test
    public void fiveWrongAttemptsBlockEvenTheCorrectPasswordForAWhile() {
        for (int i = 0; i < 5; i++) {
            assertEquals(LoginStatus.INVALID_CREDENTIALS, service.login("staff1", "Wrong123").status());
        }
        LoginResult blocked = service.login("staff1", "Correct1");
        assertEquals(LoginStatus.TOO_MANY_ATTEMPTS, blocked.status());
        assertEquals(30, blocked.retryAfterSeconds());
    }

    @Test
    public void guessingAnUnknownUsernameIsThrottledToo() {
        for (int i = 0; i < 5; i++) service.login("ghost", "Wrong123");
        assertEquals(LoginStatus.TOO_MANY_ATTEMPTS, service.login("ghost", "Wrong123").status());
    }
}
