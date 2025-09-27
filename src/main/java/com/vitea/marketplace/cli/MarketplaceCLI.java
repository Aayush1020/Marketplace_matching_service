package com.vitea.marketplace.cli;

import com.vitea.marketplace.db.H2DatabaseUtil;
import com.vitea.marketplace.db.DataLoader;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.UserService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection; // Added this import
import java.sql.SQLException; // Added this import
import java.util.concurrent.Callable;
import java.util.ArrayList;
import java.util.List;

@Command(name = "marketplace",
        mixinStandardHelpOptions = true,
        version = "Vitea Marketplace Matching Service 1.0",
        description = "CLI for the Vitea Marketplace Matching Service.",
        subcommands = {
            MarketplaceCLI.SubmitOrderCommand.class,
            MarketplaceCLI.CancelOrderCommand.class,
            MarketplaceCLI.QueryOrderBookCommand.class,
            MarketplaceCLI.QueryTradeHistoryCommand.class,
            MarketplaceCLI.CreateUserCommand.class,
            MarketplaceCLI.CreateItemCommand.class,
            MarketplaceCLI.QueryMetricsCommand.class
        }
)
public class MarketplaceCLI implements Callable<Integer> {

    // These services will now be initialized in main with the H2 connection
    private static OrderService orderService;
    private static ItemService itemService;
    private static UserService userService;

    // Setters for testing purposes (keep for now, may remove later if no longer needed)
    public static void setOrderService(OrderService service) {
        MarketplaceCLI.orderService = service;
    }

    public static void setItemService(ItemService service) {
        MarketplaceCLI.itemService = service;
    }

    public static void setUserService(UserService service) {
        MarketplaceCLI.userService = service;
    }

    public static void main(String[] args) {
        Connection connection = null; // Declare connection here
        try {
            connection = H2DatabaseUtil.initializeDatabase();

            // Initialize services with the database connection
            userService = new UserService(connection);
            itemService = new ItemService(connection);
            orderService = new OrderService(connection);
            
            // Load seed data on startup
            DataLoader dataLoader = new DataLoader(itemService, userService, orderService, connection);
            dataLoader.loadSeedData();

            CommandLine commandLine = new CommandLine(new MarketplaceCLI());

            if (args.length == 0) {
                // Interactive mode
                System.out.println("Vitea Marketplace Matching Service CLI (Interactive Mode)");
                System.out.println("Type 'help' for available commands. Type 'exit' to quit.");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String line;
                try {
                    while (true) {
                        System.out.print("marketplace> ");
                        line = reader.readLine();
                        if (line == null || line.equalsIgnoreCase("exit")) {
                            break;
                        }
                        if (line.trim().equalsIgnoreCase("help")) {
                            CommandLine.usage(commandLine.getCommandSpec(), System.out);
                        } else {
                            String[] interactiveArgs = splitCommandline(line);
                            new CommandLine(new MarketplaceCLI()).execute(interactiveArgs);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error in interactive mode: " + e.getMessage());
                }
                System.exit(0);
            } else {
                // Batch mode
                int exitCode = commandLine.execute(args);
                System.exit(exitCode);
            }
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            System.exit(1);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }
        }
    }

    // Helper method to split command line arguments, respecting quotes
    private static String[] splitCommandline(String line) {
        List<String> arguments = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder currentArg = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
                if (!inQuote && currentArg.length() > 0) { // End of quote, add currentArg if not empty
                    arguments.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
            } else if (c == ' ' && !inQuote) {
                if (currentArg.length() > 0) {
                    arguments.add(currentArg.toString());
                    currentArg = new StringBuilder();
                }
            } else {
                currentArg.append(c);
            }
        }
        if (currentArg.length() > 0) {
            arguments.add(currentArg.toString());
        }
        return arguments.toArray(new String[0]);
    }

    @Override
    public Integer call() throws Exception {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "create-user", description = "Creates a new user.")
    static class CreateUserCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "User name")
        private String name;

        @Override
        public Integer call() throws Exception {
            userService.createUser(name);
            return 0;
        }
    }

    @Command(name = "create-item", description = "Creates a new item.")
    static class CreateItemCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Item name")
        private String name;

        @Override
        public Integer call() throws Exception {
            itemService.createItem(name);
            return 0;
        }
    }

    @Command(name = "submit-order", description = "Submits a buy or sell order.")
    static class SubmitOrderCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "User ID or Name")
        private String userIdOrName;
        @Parameters(index = "1", description = "Item ID or Name")
        private String itemIdOrName;
        @Parameters(index = "2", description = "Side (BUY or SELL)")
        private String sideStr;
        @Parameters(index = "3", description = "Order Type (AT_PRICE or OPEN)")
        private String orderTypeStr;
        @Parameters(index = "4", description = "Price (required for AT_PRICE, optional for OPEN). Use 'NULL' for open orders with no set price.")
        private String priceString;
        @Parameters(index = "5", description = "Quantity")
        private int quantity;

        @Override
        public Integer call() throws Exception {
            int userId = parseUserId(userIdOrName);
            int itemId = parseItemId(itemIdOrName);

            if (userId == -1) { System.out.println("Invalid User ID or Name: " + userIdOrName); return 1; }
            if (itemId == -1) { System.out.println("Invalid Item ID or Name: " + itemIdOrName); return 1; }

            Side side = Side.valueOf(sideStr.toUpperCase());
            OrderType orderType = OrderType.valueOf(orderTypeStr.toUpperCase());

            Double price = null;
            if (priceString != null && !priceString.trim().equalsIgnoreCase("NULL")) {
                try {
                    price = Double.parseDouble(priceString);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid price format: " + priceString + ". Please provide a numeric value or 'NULL'.");
                    return 1;
                }
            } else if (orderType == OrderType.AT_PRICE) {
                System.out.println("AT_PRICE orders require a valid price. 'NULL' is not allowed.");
                return 1;
            }

            Order submittedOrder = orderService.submitOrder(userId, itemId, side, orderType, price, quantity);

            if (submittedOrder.getStatus() == OrderStatus.FILLED) {
                System.out.println("Order " + submittedOrder.getId() + " submitted successfully and immediately FILLED.");
            } else if (submittedOrder.getStatus() == OrderStatus.OPEN) {
                System.out.println("Order " + submittedOrder.getId() + " submitted successfully and QUEUED (waiting for a match).");
            } else {
                System.out.println("Order " + submittedOrder.getId() + " submission resulted in status: " + submittedOrder.getStatus() + ".");
            }
            return 0;
        }
    }

    @Command(name = "cancel-order", description = "Cancels an existing order.")
    static class CancelOrderCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Order ID to cancel")
        private int orderId;

        @Override
        public Integer call() throws Exception {
            if (orderService.cancelOrder(orderId)) {
                System.out.println("Order " + orderId + " cancelled successfully.");
            } else {
                System.out.println("Failed to cancel order " + orderId + ". Order not found or already cancelled.");
            }
            return 0;
        }
    }

    @Command(name = "query-orderbook", description = "Queries the order book for a specific item.")
    static class QueryOrderBookCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Item ID or Name")
        private String itemIdOrName;

        @Override
        public Integer call() throws Exception {
            int itemId = parseItemId(itemIdOrName);
            if (itemId == -1) { System.out.println("Invalid Item ID or Name: " + itemIdOrName); return 1; }

            System.out.println("Order Book for Item ID: " + itemId);
            orderService.queryOrderBook(itemId).forEach(order -> System.out.println("- " + order));
            System.out.println("Unmatched Orders: " + orderService.getUnmatchedOrderCount(itemId));
            return 0;
        }
    }

    @Command(name = "query-trade-history", description = "Queries the trade history for a specific item.")
    static class QueryTradeHistoryCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Item ID or Name")
        private String itemIdOrName;

        @Override
        public Integer call() throws Exception {
            int itemId = parseItemId(itemIdOrName);
            if (itemId == -1) { System.out.println("Invalid Item ID or Name: " + itemIdOrName); return 1; }

            System.out.println("Trade History for Item ID: " + itemId);
            orderService.queryTradeHistory(itemId).forEach(trade -> System.out.println("- " + trade));
            System.out.println("Average Trade Price: " + orderService.getAverageTradePrice(itemId));
            return 0;
        }
    }

    @Command(name = "query-metrics", description = "Queries aggregate marketplace metrics.")
    static class QueryMetricsCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            System.out.println("Marketplace Metrics:");
            System.out.println("  Total Executed Trades: " + orderService.getTotalExecutedTrades());
            System.out.println("  Total Unmatched Orders: " + orderService.getTotalUnmatchedOrders());
            return 0;
        }
    }

    private static int parseUserId(String idOrName) {
        try {
            return Integer.parseInt(idOrName);
        } catch (NumberFormatException e) {
            return userService.getUserIdByName(idOrName);
        }
    }

    private static int parseItemId(String idOrName) {
        try {
            return Integer.parseInt(idOrName);
        } catch (NumberFormatException e) {
            return itemService.getItemIdByName(idOrName);
        }
    }
}
