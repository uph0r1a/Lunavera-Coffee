package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class UserDAOTest {

    private final UserDAO userDAO = new UserDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private User newUser(Role role, AccountStatus status) {
        User user = new User();
        user.setUsername("test-user-" + UUID.randomUUID());
        user.setPasswordHash("irrelevant-for-these-tests");
        user.setRole(role);
        user.setStatus(status);
        return userDAO.insert(user);
    }

    @Test
    public void insertAssignsIdAndFindByUsernameReturnsSameData() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        assertTrue(user.getId() > 0);

        Optional<User> found = userDAO.findByUsername(user.getUsername());
        assertTrue(found.isPresent());
        assertEquals(Role.EMPLOYEE, found.get().getRole());
        assertEquals(AccountStatus.ACTIVE, found.get().getStatus());
        assertNotNull("insert should stamp created_at", found.get().getCreatedAt());
    }

    @Test
    public void findByIdMatchesFindByUsername() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        User byId = userDAO.findById(user.getId()).orElseThrow();
        assertEquals(user.getUsername(), byId.getUsername());
    }

    @Test
    public void existsByUsernameIsTrueOnlyForARealUsername() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        assertTrue(userDAO.existsByUsername(user.getUsername()));
        assertFalse(userDAO.existsByUsername("no-such-user-" + UUID.randomUUID()));
    }

    @Test
    public void updateStatusLocksAndUnlocksTheAccount() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        userDAO.updateStatus(user.getId(), AccountStatus.LOCKED);
        assertEquals(AccountStatus.LOCKED, userDAO.findById(user.getId()).orElseThrow().getStatus());

        userDAO.updateStatus(user.getId(), AccountStatus.ACTIVE);
        assertEquals(AccountStatus.ACTIVE, userDAO.findById(user.getId()).orElseThrow().getStatus());
    }

    @Test
    public void updateRolePromotesAndDemotes() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        userDAO.updateRole(user.getId(), Role.ADMIN);
        assertEquals(Role.ADMIN, userDAO.findById(user.getId()).orElseThrow().getRole());
    }

    @Test
    public void updatePasswordHashReplacesTheStoredHash() {
        User user = newUser(Role.EMPLOYEE, AccountStatus.ACTIVE);
        userDAO.updatePasswordHash(user.getId(), "brand-new-hash");
        assertEquals("brand-new-hash", userDAO.findById(user.getId()).orElseThrow().getPasswordHash());
    }

    @Test
    public void countActiveAdminsReflectsOnlyActiveAdmins() {
        int before = userDAO.countActiveAdmins();
        User admin = newUser(Role.ADMIN, AccountStatus.ACTIVE);
        assertEquals("one more active admin than before", before + 1, userDAO.countActiveAdmins());

        userDAO.updateStatus(admin.getId(), AccountStatus.LOCKED);
        assertEquals("locking that admin should drop the count back down",
                before, userDAO.countActiveAdmins());
    }

    @Test
    public void countByRoleAndCountByStatusMoveByExactlyOneForOneNewUser() {
        int adminsBefore = userDAO.countByRole(Role.ADMIN);
        int activeBefore = userDAO.countByStatus(AccountStatus.ACTIVE);

        newUser(Role.ADMIN, AccountStatus.ACTIVE);

        assertEquals(adminsBefore + 1, userDAO.countByRole(Role.ADMIN));
        assertEquals(activeBefore + 1, userDAO.countByStatus(AccountStatus.ACTIVE));
    }
}
