package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Customer rules used by the POS, kept out of the controller so they can be tested. */
public class CustomerService {

    private static final Logger LOGGER = Logger.getLogger(CustomerService.class.getName());

    /** One lock for the whole app: two callers racing on the same new phone must not both insert it. */
    private static final Object LOCK = new Object();

    private final CustomerDAO customerDAO;

    public CustomerService() {
        this(new CustomerDAO());
    }

    public CustomerService(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    /**
     * Customers are identified by phone. Returns the existing customer exactly as stored (a different
     * name typed at the till never renames them), creates one when the phone is new, and returns
     * {@code null} for a missing or invalid phone: that order is a walk-in and no customer row is made.
     */
    public Customer findOrCreateByPhone(String name, String phone) {
        if (!ValidationUtil.isValidPhone(phone)) return null;
        synchronized (LOCK) {
            Optional<Customer> existing = customerDAO.findByPhone(phone);
            if (existing.isPresent()) {
                return existing.get();
            }
            Customer created = new Customer();
            String trimmed = name == null ? "" : name.trim();
            created.setFullName(trimmed.isEmpty() ? "Khách vãng lai" : trimmed);
            created.setPhone(phone);
            created.setLoyaltyPoints(0);
            try {
                return customerDAO.insert(created);
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Could not create customer", e);
                return customerDAO.findByPhone(phone).orElse(null);
            }
        }
    }
}
