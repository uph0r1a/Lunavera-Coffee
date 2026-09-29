package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Covers OrderDAO.insert()'s and cancelPaidOrder()'s transactional logic - the highest-value
 * gap in test coverage, since it's where stock, loyalty points, and order/item rows all have to
 * move together correctly or not at all. See TestDatabaseSupport for why every DAO test shares
 * one database and asserts only about the rows it created itself.
 */
public class OrderDAOTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-order-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private Product newProduct(int stock, BigDecimal price) {
        Product product = new Product();
        product.setName("test-order-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(price);
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private Customer newCustomer() {
        Customer customer = new Customer();
        customer.setFullName("test-order-customer-" + UUID.randomUUID());
        customer.setLoyaltyPoints(0);
        return customerDAO.insert(customer);
    }

    /** One line: quantity x unitPrice, matching what the POS itself computes. */
    private OrderItem lineFor(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order paidOrderTotaling(BigDecimal total, Integer customerId) {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(total);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(LocalDateTime.now());
        return order;
    }

    @Test
    public void insertPaidOrderDecrementsStockAndAwardsPoints() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer customer = newCustomer();
        OrderItem line = lineFor(product, 2); // 100.000đ
        Order order = paidOrderTotaling(new BigDecimal("100000"), customer.getId());

        Order saved = orderDAO.insert(order, List.of(line));
        assertTrue(saved.getId() > 0);

        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("stock should drop by the quantity sold", 8, reloadedProduct.getStock());

        Customer reloadedCustomer = customerDAO.findById(customer.getId()).orElseThrow();
        assertEquals("points earned should follow LoyaltyPolicy for a 100.000đ order",
                LoyaltyPolicy.pointsFor(new BigDecimal("100000")), reloadedCustomer.getLoyaltyPoints());
    }

    @Test
    public void insertFailsAndRollsBackEverythingWhenStockIsInsufficient() {
        Product product = newProduct(1, new BigDecimal("20000")); // only 1 in stock
        OrderItem line = lineFor(product, 5); // asking for 5
        Order order = paidOrderTotaling(new BigDecimal("100000"), null);

        int paidOrdersBefore = orderDAO.countByStatus(OrderStatus.PAID);
        try {
            orderDAO.insert(order, List.of(line));
            fail("selling more than is in stock should have thrown");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage().contains("không đủ tồn kho"));
        }

        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("a failed sale must never touch stock", 1, reloadedProduct.getStock());
        assertEquals("a failed sale must never leave a PAID order behind",
                paidOrdersBefore, orderDAO.countByStatus(OrderStatus.PAID));
    }

    @Test
    public void insertWithNoCustomerAttachedSucceedsWithoutAwardingAnyPoints() {
        Product product = newProduct(5, new BigDecimal("30000"));
        OrderItem line = lineFor(product, 1);
        Order order = paidOrderTotaling(new BigDecimal("30000"), null); // walk-in, no customer

        Order saved = orderDAO.insert(order, List.of(line));
        assertTrue(saved.getId() > 0);
        // Nothing to assert about points - there is no customer id for any to land on; the real
        // assertion is simply that insert() doesn't throw when customerId is null.
    }

    @Test
    public void findByIdAndFindItemsByOrderIdReturnWhatWasInserted() {
        Product product = newProduct(5, new BigDecimal("15000"));
        OrderItem line = lineFor(product, 2);
        Order order = paidOrderTotaling(new BigDecimal("30000"), null);
        Order saved = orderDAO.insert(order, List.of(line));

        Order reloaded = orderDAO.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, reloaded.getStatus());
        assertEquals(0, new BigDecimal("30000").compareTo(reloaded.getTotal()));

        List<OrderItem> items = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(1, items.size());
        assertEquals(product.getName(), items.get(0).getProductName());
        assertEquals(2, items.get(0).getQuantity());
    }

    @Test
    public void cancelPaidOrderRestoresStockSubtractsPointsAndMarksCancelled() {
        Product product = newProduct(10, new BigDecimal("20000"));
        Customer customer = newCustomer();
        OrderItem line = lineFor(product, 3); // 60.000đ
        Order order = paidOrderTotaling(new BigDecimal("60000"), customer.getId());
        Order saved = orderDAO.insert(order, List.of(line));

        int pointsAfterSale = customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints();
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("60000")), pointsAfterSale);

        orderDAO.cancelPaidOrder(saved.getId());

        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("cancelling should put every sold unit back", 10, reloadedProduct.getStock());
        assertEquals("cancelling should take back exactly the points that sale earned",
                0, customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void cancelPaidOrderRefusesAnOrderThatIsNotPaid() {
        Product product = newProduct(5, new BigDecimal("10000"));
        OrderItem line = lineFor(product, 1);
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.OPEN); // never paid, so insert() never touches stock/points
        order.setSubtotal(new BigDecimal("10000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("10000"));
        Order saved = orderDAO.insert(order, List.of(line));

        try {
            orderDAO.cancelPaidOrder(saved.getId());
            fail("cancelling a non-PAID order should have thrown");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage().contains("đã thanh toán"));
        }
    }
}
