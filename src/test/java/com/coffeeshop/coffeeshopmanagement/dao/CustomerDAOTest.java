package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class CustomerDAOTest {

    private final CustomerDAO customerDAO = new CustomerDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private String uniquePhone() {
        // Matches ValidationUtil.isValidPhone's pattern (0 + 9-10 digits); last 9 digits from a
        // random UUID keep every test's phone number unique without colliding with another test.
        String digits = UUID.randomUUID().toString().replaceAll("\\D", "");
        return "0" + digits.substring(0, 9);
    }

    @Test
    public void insertAssignsIdAndFindByIdReturnsSameData() {
        Customer customer = new Customer();
        customer.setFullName("Test Customer " + UUID.randomUUID());
        customer.setPhone(uniquePhone());
        customer.setEmail("test@example.com");
        customer.setLoyaltyPoints(0);
        customerDAO.insert(customer);

        assertTrue(customer.getId() > 0);
        Optional<Customer> found = customerDAO.findById(customer.getId());
        assertTrue(found.isPresent());
        assertEquals(customer.getFullName(), found.get().getFullName());
        assertEquals(customer.getPhone(), found.get().getPhone());
        assertNotNull("insert should stamp a created_at", found.get().getCreatedAt());
    }

    @Test
    public void updateChangesNameAndPhoneButNotLoyaltyPoints() {
        Customer customer = new Customer();
        customer.setFullName("Before Update");
        customer.setPhone(uniquePhone());
        customer.setLoyaltyPoints(42); // update() intentionally has no loyalty_points column -
        customerDAO.insert(customer);  // that field is only ever changed via OrderDAO's award logic

        customer.setFullName("After Update");
        String newPhone = uniquePhone();
        customer.setPhone(newPhone);
        customerDAO.update(customer);

        Customer reloaded = customerDAO.findById(customer.getId()).orElseThrow();
        assertEquals("After Update", reloaded.getFullName());
        assertEquals(newPhone, reloaded.getPhone());
        assertEquals("update() must never reset loyalty points earned from real orders",
                42, reloaded.getLoyaltyPoints());
    }

    @Test
    public void existsByPhoneExcludesTheCustomersOwnRecord() {
        String phone = uniquePhone();
        Customer customer = new Customer();
        customer.setFullName("Phone Owner");
        customer.setPhone(phone);
        customerDAO.insert(customer);

        assertTrue("another customer (excludeId 0) should see the phone as taken",
                customerDAO.existsByPhone(phone, 0));
        assertFalse("the customer's own edit dialog (excluding its own id) should not flag its own phone",
                customerDAO.existsByPhone(phone, customer.getId()));
        assertFalse("a phone nobody has should never be reported as existing",
                customerDAO.existsByPhone(uniquePhone(), 0));
    }

    @Test
    public void findAllIncludesEveryInsertedCustomer() {
        Customer customer = new Customer();
        customer.setFullName("Findable Customer " + UUID.randomUUID());
        customer.setPhone(uniquePhone());
        customerDAO.insert(customer);

        boolean present = customerDAO.findAll().stream().anyMatch(c -> c.getId() == customer.getId());
        assertTrue(present);
    }
}
