package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Table status is computed: a table is occupied when it has an OPEN order OR its stored status
 * is OCCUPIED (manual toggle). setEmpty() must also cancel any OPEN order on that table.
 * Each test uses its own table number (the database is shared across DAO tests).
 */
public class TableDAOTest {

    private final TableDAO tableDAO = new TableDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private Order openOrderOn(int tableNumber) {
        Order order = new Order();
        order.setOrderDate(LocalDateTime.now());
        order.setTableNumber(tableNumber);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    @Test
    public void seedsTwelveTablesAndTheyStartEmpty() {
        List<DiningTable> tables = tableDAO.findAll();
        assertTrue(tables.size() >= 12);
        DiningTable first = tableDAO.findByNumber(12);
        assertNotNull(first);
        assertEquals(TableStatus.EMPTY, tableDAO.findByNumber(12).getStatus());
    }

    @Test
    public void manualOccupiedThenEmpty() {
        tableDAO.setOccupied(1, null);
        assertTrue(tableDAO.findByNumber(1).isOccupied());
        tableDAO.setEmpty(1);
        assertFalse(tableDAO.findByNumber(1).isOccupied());
    }

    @Test
    public void openOrderMakesTableOccupiedAndSetEmptyCancelsIt() {
        Order saved = openOrderOn(2);
        assertTrue(saved.getId() > 0);
        assertTrue(tableDAO.findByNumber(2).isOccupied());
        assertEquals(Integer.valueOf(saved.getId()), tableDAO.findByNumber(2).getCurrentOrderId());
        assertTrue(orderDAO.findOpenOrderByTable(2).isPresent());

        tableDAO.setEmpty(2);

        assertFalse(tableDAO.findByNumber(2).isOccupied());
        assertFalse(orderDAO.findOpenOrderByTable(2).isPresent());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
    }

    @Test
    public void unknownTableReturnsNull() {
        assertNull(tableDAO.findByNumber(9999));
    }
}
