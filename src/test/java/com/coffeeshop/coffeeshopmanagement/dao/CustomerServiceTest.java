package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;

public class CustomerServiceTest {

    private final CustomerService service = new CustomerService();
    private final CustomerDAO customerDAO = new CustomerDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private String uniquePhone() {
        String digits = UUID.randomUUID().toString().replaceAll("\\D", "");
        return "0" + digits.substring(0, 9);
    }

    @Test
    public void aNewPhoneCreatesACustomerWithTheTypedName() {
        String phone = uniquePhone();
        Customer created = service.findOrCreateByPhone("  Lan  ", phone);
        assertNotNull(created);
        assertEquals("Lan", created.getFullName());
        assertEquals(phone, created.getPhone());
        assertEquals(0, created.getLoyaltyPoints());
    }

    @Test
    public void aBlankNameBecomesWalkInName() {
        assertEquals("Khách vãng lai", service.findOrCreateByPhone("", uniquePhone()).getFullName());
    }

    @Test
    public void anExistingCustomerIsReturnedAsStoredAndNeverRenamed() {
        String phone = uniquePhone();
        Customer first = service.findOrCreateByPhone("Original Name", phone);
        Customer again = service.findOrCreateByPhone("Typo Name", phone);
        assertEquals(first.getId(), again.getId());
        assertEquals("Original Name", customerDAO.findById(first.getId()).orElseThrow().getFullName());
    }

    @Test
    public void theSamePhoneWrittenAnotherWayIsTheSameCustomer() {
        String phone = uniquePhone();
        Customer first = service.findOrCreateByPhone("A", phone);
        Customer other = service.findOrCreateByPhone("A", "+84" + phone.substring(1));
        assertEquals(first.getId(), other.getId());
    }

    @Test
    public void noPhoneOrAnInvalidPhoneMeansWalkInAndCreatesNothing() {
        int before = customerDAO.countAll();
        assertNull(service.findOrCreateByPhone("Someone", ""));
        assertNull(service.findOrCreateByPhone("Someone", null));
        assertNull(service.findOrCreateByPhone("Someone", "12345"));
        assertEquals(before, customerDAO.countAll());
    }

    @Test
    public void manyThreadsRacingOnOneNewPhoneCreateExactlyOneCustomer() throws Exception {
        String phone = uniquePhone();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Customer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return service.findOrCreateByPhone("Racer", phone);
            }));
        }
        start.countDown();
        int id = -1;
        for (Future<Customer> f : results) {
            Customer c = f.get();
            assertNotNull(c);
            if (id == -1) id = c.getId();
            assertEquals(id, c.getId());
        }
        pool.shutdown();
        long withPhone = customerDAO.findAll().stream().filter(c -> phone.equals(c.getPhone())).count();
        assertEquals(1, withPhone);
    }
}
