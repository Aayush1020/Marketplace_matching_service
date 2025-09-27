package com.vitea.marketplace.tests;

import com.vitea.marketplace.db.DataLoader;
import com.vitea.marketplace.db.H2DatabaseUtil;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.Trade;
import com.vitea.marketplace.models.Item; // Import Item model
import com.vitea.marketplace.models.User; // Import User model
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime; // Added: For explicit timestamps
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataLoaderTest {

    private ItemService itemService;
    private UserService userService;
    private OrderService orderService;
    private DataLoader dataLoader;
    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        // Initialize H2 Database (ensures a clean database for each test)
        connection = H2DatabaseUtil.initializeDatabase();
        H2DatabaseUtil.resetAllTables(connection); // Ensure a clean database state before each test

        // Initialize services with the database connection
        itemService = new ItemService(connection);
        userService = new UserService(connection);
        orderService = new OrderService(connection);
        
        // Individual service resets are no longer needed as H2DatabaseUtil.resetAllTables() clears the DB
        // itemService.reset();
        // userService.reset();
        // orderService.reset();
        // orderService.setNextOrderId(0); // This is now implicitly handled by DB MAX(ID) queries and reset logic
        
        dataLoader = new DataLoader(itemService, userService, orderService, connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testSeedDataLoading() {
        dataLoader.loadSeedData();

        // Verify Items (check existence and IDs, which should start from 1 after reset)
        assertEquals(1, itemService.getItemIdByName("Replica A"));
        assertEquals(2, itemService.getItemIdByName("Replica B"));

        // Verify Users (check existence and IDs)
        assertEquals(1, userService.getUserIdByName("Alice"));
        assertEquals(2, userService.getUserIdByName("Bob"));
        assertEquals(3, userService.getUserIdByName("Charlie"));

        // Verify Order Book (expected to be empty as orders should match immediately)
        assertTrue(orderService.queryOrderBook(itemService.getItemIdByName("Replica A")).isEmpty());
        assertTrue(orderService.queryOrderBook(itemService.getItemIdByName("Replica B")).isEmpty());

        // Verify Trade History for Item A
        List<Trade> tradesItemA = orderService.queryTradeHistory(itemService.getItemIdByName("Replica A"));
        assertEquals(1, tradesItemA.size());
        Trade tradeA = tradesItemA.get(0);
        assertEquals(1, tradeA.getBuyerId()); // Alice
        assertEquals(2, tradeA.getSellerId()); // Bob
        assertEquals(1, tradeA.getItemId()); // Replica A
        assertEquals(1000.0, tradeA.getPrice()); // Price of the order placed first (buyOrder)
        assertEquals(1, tradeA.getBuyOrderId());
        assertEquals(2, tradeA.getSellOrderId());

        // Verify Trade History for Item B
        List<Trade> tradesItemB = orderService.queryTradeHistory(itemService.getItemIdByName("Replica B"));
        assertEquals(1, tradesItemB.size());
        Trade tradeB = tradesItemB.get(0);
        assertEquals(1, tradeB.getBuyerId()); // Alice
        assertEquals(3, tradeB.getSellerId()); // Charlie
        assertEquals(2, tradeB.getItemId()); // Replica B
        assertEquals(1000.0, tradeB.getPrice()); // For OPEN vs OPEN orders, the fallback price is 1000.0
        assertEquals(3, tradeB.getBuyOrderId());
        assertEquals(4, tradeB.getSellOrderId());
    }
}
