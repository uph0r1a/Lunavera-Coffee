package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pulls together the numbers shown on the admin and employee dashboards. All figures come
 * from the database - none are hardcoded - so once the POS/order-creation screen starts
 * writing real orders, these numbers will move on their own.
 */
public class DashboardStatsService {

    public record DashboardStats(
            int todayOrders,
            BigDecimal todayRevenue,
            int todayCustomers,
            int paidOrders,
            int openOrders,
            int cancelledOrders,
            int totalProducts,
            int totalEmployees,
            int totalCustomers,
            Map<LocalDate, BigDecimal> revenueLast7Days,
            int lowStockCount,
            List<Product> lowStockProducts,
            List<Order> todayOrdersList
    ) {
    }

    private static final int LOW_STOCK_ROWS = 5;

    private final OrderDAO orderDAO;
    private final ProductDAO productDAO;
    private final EmployeeDAO employeeDAO;
    private final CustomerDAO customerDAO;

    public DashboardStatsService() {
        this(new OrderDAO(), new ProductDAO(), new EmployeeDAO(), new CustomerDAO());
    }

    public DashboardStatsService(OrderDAO orderDAO, ProductDAO productDAO, EmployeeDAO employeeDAO,
                                  CustomerDAO customerDAO) {
        this.orderDAO = orderDAO;
        this.productDAO = productDAO;
        this.employeeDAO = employeeDAO;
        this.customerDAO = customerDAO;
    }

    public DashboardStats loadStats() {
        return new DashboardStats(
                orderDAO.countToday(),
                orderDAO.sumRevenueToday(),
                orderDAO.countDistinctCustomersToday(),
                orderDAO.countByStatus(OrderStatus.PAID),
                orderDAO.countByStatus(OrderStatus.OPEN),
                orderDAO.countByStatus(OrderStatus.CANCELLED),
                productDAO.countAll(),
                employeeDAO.countActive(),
                customerDAO.countAll(),
                orderDAO.revenueForLast7Days(),
                productDAO.countLowStock(ProductDAO.LOW_STOCK_THRESHOLD),
                productDAO.findLowStock(ProductDAO.LOW_STOCK_THRESHOLD, LOW_STOCK_ROWS),
                orderDAO.findTodayOrders()
        );
    }
}
