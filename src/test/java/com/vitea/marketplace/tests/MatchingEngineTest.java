package com.vitea.marketplace.tests;

import com.vitea.marketplace.db.H2DatabaseUtil;
import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.Trade;
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.MatchingEngine;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private OrderService orderService;
    private UserService userService;
    private ItemService itemService;
    private Connection connection;

    private final int USER_ALICE_ID = 1;
    private final int USER_BOB_ID = 2;
    private final int USER_CHARLIE_ID = 3;
    private final int ITEM_REPLICA_A_ID = 1;
    
    @BeforeEach
    void setUp() throws SQLException {
        connection = H2DatabaseUtil.initializeDatabase();
        H2DatabaseUtil.resetAllTables(connection); // Ensure a clean database state before each test

        userService = new UserService(connection);
        itemService = new ItemService(connection);
        orderService = new OrderService(connection);
        matchingEngine = new MatchingEngine(ITEM_REPLICA_A_ID, orderService); // Item ID 1, inject orderService

        // The individual service reset calls are no longer needed as H2DatabaseUtil.resetAllTables() clears the DB
        // userService.reset();
        // itemService.reset();
        // orderService.reset();

        // Create default user and item for tests to use
        userService.createUser("Alice"); // ID 1
        userService.createUser("Bob");   // ID 2
        userService.createUser("Charlie"); // ID 3
        itemService.createItem("Replica A"); // ID 1
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testAtPriceOrderMatchingExactQuantityAndPrice() {
        // Create orders via orderService to ensure they are persisted
        Order buyOrder = orderService.submitOrder(1, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(5));
        Order sellOrder = orderService.submitOrder(2, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now());

        // Query trades via orderService
        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder.getId()));
        assertEquals(0, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID));
        assertEquals(100.0, trades.get(0).getPrice());
    }

    @Test
    void testAtPriceOrderNoMatchDifferentQuantity() {
        Order buyOrder = orderService.submitOrder(3, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now());
        Order sellOrder = orderService.submitOrder(4, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 100.0, 5, LocalDateTime.now());

        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertTrue(orderService.getTradesByItem(ITEM_REPLICA_A_ID).isEmpty());
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(buyOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertEquals(2, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID));
    }

    @Test
    void testAtPriceOrderNoMatchDifferentPrice() {
        Order buyOrder = orderService.submitOrder(5, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 90.0, 10, LocalDateTime.now());
        Order sellOrder = orderService.submitOrder(6, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 110.0, 10, LocalDateTime.now());

        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertTrue(orderService.getTradesByItem(ITEM_REPLICA_A_ID).isEmpty());
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(buyOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertEquals(2, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID));
    }

    @Test
    void testOpenOrderMatchingExactQuantity() {
        Order buyOrder = orderService.submitOrder(7, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.OPEN, null, 10, LocalDateTime.now().minusSeconds(5));
        Order sellOrder = orderService.submitOrder(8, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.OPEN, null, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder.getId()));
        assertEquals(0, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID));
        assertEquals(1000.0, trades.get(0).getPrice()); // Default fallback price
    }

    @Test
    void testOpenOrderMatchingWithLastTradedPriceFallback() {
        // First trade to set last traded price (orders must have new, distinct IDs)
        orderService.submitOrder(10, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 150.0, 5, LocalDateTime.now().minusSeconds(10));
        orderService.submitOrder(11, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 150.0, 5, LocalDateTime.now().minusSeconds(8));

        int initialTradeCount = orderService.getTradesByItem(ITEM_REPLICA_A_ID).size();
        
        // Open orders should use last traded price
        Order buyOrder2 = orderService.submitOrder(12, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.OPEN, null, 10, LocalDateTime.now().minusSeconds(5));
        Order sellOrder2 = orderService.submitOrder(13, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.OPEN, null, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(initialTradeCount + 1, trades.size()); // Expecting one new trade
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder2.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder2.getId()));
        assertEquals(150.0, trades.get(trades.size() - 1).getPrice()); // Should use last traded price
    }

    @Test
    void testOpenOrderMatchingWithAtPriceSellFallback() {
        // Create AT_PRICE sell order for fallback
        Order sellOrder1 = orderService.submitOrder(14, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 120.0, 10, LocalDateTime.now().minusSeconds(10));
        Order buyOrder1 = orderService.submitOrder(15, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.OPEN, null, 10, LocalDateTime.now().minusSeconds(5));

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder1.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder1.getId()));
        assertEquals(120.0, trades.get(0).getPrice()); // Should use AtPrice sell order price
    }

    @Test
    void testOpenOrderMatchingWithAtPriceBuyFallback() {
        // Create AT_PRICE buy order for fallback
        Order buyOrder1 = orderService.submitOrder(16, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 130.0, 10, LocalDateTime.now().minusSeconds(10));
        Order sellOrder1 = orderService.submitOrder(17, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.OPEN, null, 10, LocalDateTime.now().minusSeconds(5));

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder1.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder1.getId()));
        assertEquals(130.0, trades.get(0).getPrice()); // Should use AtPrice buy order price
    }

    @Test
    void testPriceTimePriorityBuyOrders() {
        Order oldBuyOrder = orderService.submitOrder(18, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(10));
        Order newBuyOrder = orderService.submitOrder(19, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 105.0, 10, LocalDateTime.now().minusSeconds(5));
        Order evenNewerBuyOrderSamePrice = orderService.submitOrder(20, USER_CHARLIE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(2));

        Order matchingSellOrder = orderService.submitOrder(21, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        // The highest priced buy order (newBuyOrder at 105.0) should be matched first.
        assertEquals(newBuyOrder.getId(), trades.get(0).getBuyOrderId());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(newBuyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(matchingSellOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(oldBuyOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(evenNewerBuyOrderSamePrice.getId()));
    }

    @Test
    void testPriceTimePrioritySellOrders() {
        // Submit multiple sell orders with different prices and timestamps
        Order sellOrder1 = orderService.submitOrder(22, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 99.0, 10, LocalDateTime.now().minusSeconds(5));
        Order sellOrder2 = orderService.submitOrder(23, USER_CHARLIE_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 98.0, 10, LocalDateTime.now().minusSeconds(7));
        Order evenNewerSellOrderSamePrice = orderService.submitOrder(24, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 99.0, 10, LocalDateTime.now().minusSeconds(2));

        // Now, submit a buy order that should trigger a trade with the highest priority sell order
        Order matchingBuyOrder = orderService.submitOrder(25, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        // The lowest priced sell order (sellOrder2 at 98.0) should be matched first.
        assertEquals(sellOrder2.getId(), trades.get(0).getSellOrderId());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder2.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(matchingBuyOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder1.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(evenNewerSellOrderSamePrice.getId()));
    }

    @Test
    void testCancelledOrdersIgnored() {
        // Submit a cancelled buy order (its status is CANCELLED in DB from the start)
        // We need to create a user with ID 1 and an item with ID 1 before submitting this order.
        Order cancelledBuyOrder = orderService.submitOrder(1, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now().minusSeconds(5));
        orderService.updateOrderStatus(cancelledBuyOrder.getId(), OrderStatus.CANCELLED); // Explicitly cancel in DB

        // Submit an open sell order that would normally match
        Order sellOrder = orderService.submitOrder(2, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 100.0, 10, LocalDateTime.now());

        assertEquals(OrderStatus.CANCELLED, orderService.getOrderStatus(cancelledBuyOrder.getId()));
        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertTrue(orderService.getTradesByItem(ITEM_REPLICA_A_ID).isEmpty());
        assertEquals(1, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID)); // Only sell order should be open
    }

    @Test
    void testMaxBuyLessThanMinSell() {
        Order buyOrder = orderService.submitOrder(26, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 90.0, 10, LocalDateTime.now());
        Order sellOrder = orderService.submitOrder(27, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 110.0, 10, LocalDateTime.now());

        assertEquals(OrderStatus.OPEN, orderService.getOrderStatus(sellOrder.getId()));
        assertTrue(orderService.getTradesByItem(ITEM_REPLICA_A_ID).isEmpty());
        assertEquals(2, orderService.getUnmatchedOrderCount(ITEM_REPLICA_A_ID));
    }

    @Test
    void testTwoAtPriceOrdersDifferentPricesUseFirstPlacedPrice() {
        LocalDateTime timestamp1 = LocalDateTime.now().minusSeconds(10);
        LocalDateTime timestamp2 = LocalDateTime.now().minusSeconds(5);

        Order buyOrder = orderService.submitOrder(28, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 100.0, 10, timestamp1);
        Order sellOrder = orderService.submitOrder(29, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 90.0, 10, timestamp2);

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(100.0, trades.get(0).getPrice()); // Price of the order placed first (buyOrder)
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(buyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(sellOrder.getId()));
    }

    @Test
    void testOpenOrderWithAtPriceOppositeSideMatching() {
        Order openBuyOrder = orderService.submitOrder(30, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.OPEN, null, 10, LocalDateTime.now().minusSeconds(5));
        Order atPriceSellOrder = orderService.submitOrder(31, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.AT_PRICE, 105.0, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(openBuyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(atPriceSellOrder.getId()));
        assertEquals(105.0, trades.get(0).getPrice()); // Should use the AtPrice order's price
    }

    @Test
    void testAtPriceOrderWithOpenOppositeSideMatching() {
        Order atPriceBuyOrder = orderService.submitOrder(32, USER_ALICE_ID, ITEM_REPLICA_A_ID, Side.BUY, OrderType.AT_PRICE, 95.0, 10, LocalDateTime.now().minusSeconds(5));
        Order openSellOrder = orderService.submitOrder(33, USER_BOB_ID, ITEM_REPLICA_A_ID, Side.SELL, OrderType.OPEN, null, 10, LocalDateTime.now());

        List<Trade> trades = orderService.getTradesByItem(ITEM_REPLICA_A_ID);
        assertEquals(1, trades.size());
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(atPriceBuyOrder.getId()));
        assertEquals(OrderStatus.FILLED, orderService.getOrderStatus(openSellOrder.getId()));
        assertEquals(95.0, trades.get(0).getPrice()); // Should use the AtPrice order's price
    }
}
