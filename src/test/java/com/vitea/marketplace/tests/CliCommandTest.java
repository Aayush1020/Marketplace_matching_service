package com.vitea.marketplace.tests;

import com.vitea.marketplace.cli.MarketplaceCLI;
import com.vitea.marketplace.db.H2DatabaseUtil;
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CliCommandTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private Connection connection;
    private UserService userService;
    private ItemService itemService;
    private OrderService orderService;

    @BeforeEach
    public void setUp() throws SQLException {
        // Capture System.out and System.err
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));

        // Initialize H2 database connection for each test
        connection = H2DatabaseUtil.initializeDatabase();
        H2DatabaseUtil.resetAllTables(connection); // Ensure a clean database state before each test
        
        // Initialize services with the connection
        userService = new UserService(connection);
        itemService = new ItemService(connection);
        orderService = new OrderService(connection);

        // Inject services into MarketplaceCLI for testing
        MarketplaceCLI.setUserService(userService);
        MarketplaceCLI.setItemService(itemService);
        MarketplaceCLI.setOrderService(orderService);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        // Restore original System.out and System.err
        System.setOut(originalOut);
        System.setErr(originalErr);

        // Close the database connection after each test
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void testCreateUserCommand() {
        new CommandLine(new MarketplaceCLI()).execute("create-user", "Alice");
        assertTrue(outContent.toString().contains("Created user: Alice with ID: 1"));
    }

    @Test
    void testCreateItemCommand() {
        new CommandLine(new MarketplaceCLI()).execute("create-item", "Collectible A");
        assertTrue(outContent.toString().contains("Created item: Collectible A with ID: 1"));
    }

    @Test
    void testSubmitOrderCommandWithNames() {
        // Since services are reset, IDs start from 1 for each test
        new CommandLine(new MarketplaceCLI()).execute("create-user", "Bob"); // Bob gets ID 1
        new CommandLine(new MarketplaceCLI()).execute("create-item", "ItemX"); // ItemX gets ID 1
        outContent.reset(); // Clear output from create commands

        new CommandLine(new MarketplaceCLI()).execute("submit-order", "Bob", "ItemX", "BUY", "AT_PRICE", "100.0", "10");
        assertTrue(outContent.toString().contains("Order 1 submitted successfully and QUEUED (waiting for a match)."));
    }

    @Test
    void testCancelOrderCommand() {
        // Create user, item, and an order
        new CommandLine(new MarketplaceCLI()).execute("create-user", "UserToCancel"); // User gets ID 1
        new CommandLine(new MarketplaceCLI()).execute("create-item", "ItemToCancel"); // Item gets ID 1
        new CommandLine(new MarketplaceCLI()).execute("submit-order", "UserToCancel", "ItemToCancel", "BUY", "AT_PRICE", "100.0", "10"); // Order gets ID 1
        outContent.reset(); // Clear output from previous commands

        new CommandLine(new MarketplaceCLI()).execute("cancel-order", "1");
        assertTrue(outContent.toString().contains("Order 1 cancelled successfully."));
        // Verify in DB that it's cancelled
        // No longer assert on in-memory state; OrderService.getOrderStatus will query DB
        assertTrue(orderService.getOrderStatus(1) == com.vitea.marketplace.models.OrderStatus.CANCELLED);
    }

    @Test
    void testQueryOrderBookCommandWithItemName() {
        new CommandLine(new MarketplaceCLI()).execute("create-item", "RareCard"); // Item ID 1
        new CommandLine(new MarketplaceCLI()).execute("create-user", "Alice"); // User ID 1
        new CommandLine(new MarketplaceCLI()).execute("submit-order", "Alice", "RareCard", "BUY", "AT_PRICE", "100.0", "1"); // Order ID 1
        outContent.reset();

        new CommandLine(new MarketplaceCLI()).execute("query-orderbook", "RareCard");
        assertTrue(outContent.toString().contains("Order Book for Item ID: 1"));
        assertTrue(outContent.toString().contains("- Order{id=1, userId=1, itemId=1, side=BUY, orderType=AT_PRICE, price=100.0, status=OPEN, timestamp="));
        assertTrue(outContent.toString().contains("Unmatched Orders: 1"));
    }

    @Test
    void testQueryTradeHistoryCommandWithItemName() {
        new CommandLine(new MarketplaceCLI()).execute("create-user", "Buyer"); // User ID 1
        new CommandLine(new MarketplaceCLI()).execute("create-user", "Seller"); // User ID 2
        new CommandLine(new MarketplaceCLI()).execute("create-item", "Gem"); // Item ID 1

        // Submit orders that will match to create a trade
        new CommandLine(new MarketplaceCLI()).execute("submit-order", "Buyer", "Gem", "BUY", "AT_PRICE", "150.0", "1"); // Order ID 1
        new CommandLine(new MarketplaceCLI()).execute("submit-order", "Seller", "Gem", "SELL", "AT_PRICE", "150.0", "1"); // Order ID 2
        outContent.reset();

        new CommandLine(new MarketplaceCLI()).execute("query-trade-history", "Gem");
        assertTrue(outContent.toString().contains("Trade History for Item ID: 1"));
        assertTrue(outContent.toString().contains("Average Trade Price: 150.0"));
        // Verify that trade is in the history
        assertTrue(outContent.toString().contains("Trade{id=1, buyerId=1, buyOrderId=1, sellerId=2, sellOrderId=2, itemId=1, price=150.0, timestamp="));
    }
}
