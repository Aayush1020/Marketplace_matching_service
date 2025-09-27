package com.vitea.marketplace.tests;

import com.vitea.marketplace.db.H2DatabaseUtil;
import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderServiceTest {

    private OrderService orderService;
    private Connection connection;
    private UserService userService;
    private ItemService itemService;

    private final int USER_ID_1 = 1;
    private final int USER_ID_2 = 2;
    private final int ITEM_ID_1 = 1;
    private final int ITEM_ID_2 = 2;

    @BeforeEach
    void setUp() throws SQLException {
        connection = H2DatabaseUtil.initializeDatabase();
        H2DatabaseUtil.resetAllTables(connection);

        userService = new UserService(connection);
        itemService = new ItemService(connection);
        orderService = new OrderService(connection);

        // Create some default users and items for tests that don't explicitly create them
        userService.createUser("TestUser1"); // ID 1
        userService.createUser("TestUser2"); // ID 2
        itemService.createItem("TestItem1"); // ID 1
        itemService.createItem("TestItem2"); // ID 2

        // The individual service reset is no longer needed as H2DatabaseUtil.resetAllTables() clears the DB
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testSubmitOrder() {
        Order order = orderService.submitOrder(USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10); // 6 arguments
        assertNotNull(order);
        assertEquals(1, order.getId());

        // Verify that the order is in the database as OPEN
        OrderStatus statusInDb = orderService.getOrderStatus(order.getId());
        assertEquals(OrderStatus.OPEN, statusInDb);

        List<Order> openOrders = orderService.getOpenOrdersByItem(ITEM_ID_1);
        assertEquals(1, openOrders.size());
        assertEquals(order.getId(), openOrders.get(0).getId());
    }

    @Test
    void testCancelOrder() {
        // Submit an order with a specific ID to easily test cancellation
        int orderIdToCancel = 100;
        orderService.submitOrder(orderIdToCancel, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now()); // 8 arguments

        // Verify it's initially OPEN in DB
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(orderIdToCancel), "Order should be open initially");

        // Now cancel the order
        boolean cancelled = orderService.cancelOrder(orderIdToCancel);
        assertTrue(cancelled, "Order should be successfully cancelled");

        // Verify its status is CANCELLED in DB
        assertEquals(OrderStatus.CANCELLED, orderService.getOrderStatus(orderIdToCancel), "Order status should be CANCELLED after cancellation");

        // Ensure it's no longer in the open order book
        assertTrue(orderService.getOpenOrdersByItem(ITEM_ID_1).isEmpty());
    }

    @Test
    void testGetOrderBook() {
        orderService.submitOrder(1, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 5, LocalDateTime.now().minusMinutes(1)); // 8 arguments
        orderService.submitOrder(2, USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 110.0, 5, LocalDateTime.now()); // 8 arguments
        
        List<Order> orderBook = orderService.queryOrderBook(ITEM_ID_1);
        assertEquals(2, orderBook.size());
        // Verify order of items based on price-time priority (buy higher, sell lower)
        assertEquals(100.0, orderBook.get(0).getPrice()); // Buy order should come first for priority list (desc price for buy)
        assertEquals(110.0, orderBook.get(1).getPrice()); // Sell order should come after
    }

    @Test
    void testGetTradeHistory() {
        // Submit orders that will result in a trade
        orderService.submitOrder(1, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(1)); // 8 arguments
        orderService.submitOrder(2, USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now()); // 8 arguments

        List<com.vitea.marketplace.models.Trade> tradeHistory = orderService.queryTradeHistory(ITEM_ID_1);
        assertEquals(1, tradeHistory.size());
        assertEquals(100.0, tradeHistory.get(0).getPrice());
        assertEquals(1, tradeHistory.get(0).getBuyOrderId());
        assertEquals(2, tradeHistory.get(0).getSellOrderId());
    }

    @Test
    void testGetAverageTradePrice() {
        orderService.submitOrder(1, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(3)); // 8 arguments
        orderService.submitOrder(2, USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(2)); // 8 arguments
        orderService.submitOrder(3, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 120.0, 5, LocalDateTime.now().minusSeconds(1)); // 8 arguments
        orderService.submitOrder(4, USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 120.0, 5, LocalDateTime.now()); // 8 arguments

        // Expected trades: (100.0) and (120.0)
        assertEquals(110.0, orderService.getAverageTradePrice(ITEM_ID_1), 0.001);
    }

    @Test
    void testGetUnmatchedOrderCount() {
        orderService.submitOrder(USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10); // 6 arguments
        orderService.submitOrder(USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 90.0, 10); // 6 arguments
        // These two should match, leaving 0 unmatched (because exact quantity match is required)

        assertEquals(0, orderService.getUnmatchedOrderCount(ITEM_ID_1));

        orderService.submitOrder(USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 80.0, 5); // 6 arguments
        assertEquals(1, orderService.getUnmatchedOrderCount(ITEM_ID_1)); // One unmatched order
    }

    @Test
    void testGetTotalExecutedTrades() {
        // No trades initially
        assertEquals(0, orderService.getTotalExecutedTrades());

        // One trade
        orderService.submitOrder(1, USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(1)); // 8 arguments
        orderService.submitOrder(2, USER_ID_2, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now()); // 8 arguments
        assertEquals(1, orderService.getTotalExecutedTrades());

        // Another trade for a different item
        orderService.submitOrder(3, USER_ID_1, ITEM_ID_2, Side.BUY, OrderType.AT_PRICE, 50.0, 5, LocalDateTime.now().minusSeconds(1)); // 8 arguments
        orderService.submitOrder(4, USER_ID_2, ITEM_ID_2, Side.SELL, OrderType.AT_PRICE, 50.0, 5, LocalDateTime.now()); // 8 arguments
        assertEquals(2, orderService.getTotalExecutedTrades());
    }

    @Test
    void testGetTotalUnmatchedOrders() {
        // No unmatched orders initially
        assertEquals(0, orderService.getTotalUnmatchedOrders());

        // One unmatched order
        orderService.submitOrder(USER_ID_1, ITEM_ID_1, Side.BUY, OrderType.AT_PRICE, 100.0, 10); // 6 arguments
        assertEquals(1, orderService.getTotalUnmatchedOrders());

        // Another unmatched order for a different item
        orderService.submitOrder(USER_ID_2, ITEM_ID_2, Side.SELL, OrderType.AT_PRICE, 50.0, 5); // 6 arguments
        assertEquals(2, orderService.getTotalUnmatchedOrders());

        // One matched, so count reduces
        orderService.submitOrder(USER_ID_1, ITEM_ID_1, Side.SELL, OrderType.AT_PRICE, 100.0, 10); // 6 arguments
        assertEquals(1, orderService.getTotalUnmatchedOrders()); // Only order 2 remains unmatched
    }
}
