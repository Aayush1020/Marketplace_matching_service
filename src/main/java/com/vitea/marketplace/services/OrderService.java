package com.vitea.marketplace.services;

import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.models.Trade;

import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderService {

    private final Map<Integer, MatchingEngine> matchingEngines;
    private final AtomicInteger orderIdCounter;
    private final Connection connection; // Added for H2 interaction

    public OrderService(Connection connection) {
        this.matchingEngines = new ConcurrentHashMap<>();
        this.orderIdCounter = new AtomicInteger(0);
        this.connection = connection;
    }

    private MatchingEngine getMatchingEngine(int itemId) {
        // The MatchingEngine will now also need a reference to OrderService to update DB
        return matchingEngines.computeIfAbsent(itemId, id -> new MatchingEngine(id, this));
    }

    public void reset() {
        for (MatchingEngine engine : matchingEngines.values()) {
            engine.reset();
        }
        matchingEngines.clear();
        orderIdCounter.set(0);
        // We should also clear the database tables here if reset means a full reset for in-memory DB
        // The database tables will be truncated by H2DatabaseUtil.resetAllTables() in test setup methods.
    }

    public void setNextOrderId(int nextId) {
        orderIdCounter.set(nextId);
    }

    // New method to insert an order into the database
    public void insertOrder(Order order) {
        String sql = "INSERT INTO ORDERS (ID, USER_ID, ITEM_ID, SIDE, ORDER_TYPE, PRICE, STATUS, TIMESTAMP, QUANTITY) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, order.getId());
            pstmt.setInt(2, order.getUserId());
            pstmt.setInt(3, order.getItemId());
            pstmt.setString(4, order.getSide().name());
            pstmt.setString(5, order.getOrderType().name());
            if (order.getPrice() != null) {
                pstmt.setDouble(6, order.getPrice());
            } else {
                pstmt.setNull(6, java.sql.Types.DOUBLE);
            }
            pstmt.setString(7, order.getStatus().name());
            pstmt.setTimestamp(8, java.sql.Timestamp.valueOf(order.getTimestamp()));
            pstmt.setInt(9, order.getQuantity());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting order " + order.getId() + ": " + e.getMessage());
        }
    }

    // New method to update an order's status in the database
    public void updateOrderStatus(int orderId, OrderStatus status) {
        String sql = "UPDATE ORDERS SET STATUS = ? WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setInt(2, orderId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating status for order " + orderId + ": " + e.getMessage());
        }
    }

    // New method to insert a trade into the database
    public void insertTrade(Trade trade) {
        String sql = "INSERT INTO TRADES (ID, BUYER_ID, BUY_ORDER_ID, SELLER_ID, SELL_ORDER_ID, ITEM_ID, PRICE, TIMESTAMP, QUANTITY) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, trade.getId());
            pstmt.setInt(2, trade.getBuyerId());
            pstmt.setInt(3, trade.getBuyOrderId());
            pstmt.setInt(4, trade.getSellerId());
            pstmt.setInt(5, trade.getSellOrderId());
            pstmt.setInt(6, trade.getItemId());
            pstmt.setDouble(7, trade.getPrice());
            pstmt.setTimestamp(8, java.sql.Timestamp.valueOf(trade.getTimestamp()));
            pstmt.setInt(9, trade.getQuantity()); // Use new getQuantity()
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting trade " + trade.getId() + ": " + e.getMessage());
        }
    }

    // For regular order submissions, generates a new ID
    public Order submitOrder(int userId, int itemId, Side side, OrderType orderType, Double price, int quantity) {
        int orderId = orderIdCounter.incrementAndGet();
        Order newOrder = new Order(orderId, userId, itemId, side, orderType, price, OrderStatus.OPEN, LocalDateTime.now(), quantity);
        insertOrder(newOrder); // Persist new order to DB
        return getMatchingEngine(itemId).submitOrder(newOrder);
    }

    // For seed data or cases where a specific order ID is required
    public Order submitOrder(int orderId, int userId, int itemId, Side side, OrderType orderType, Double price, int quantity, LocalDateTime timestamp) {
        // Update the counter if the provided orderId is higher
        while (true) {
            int currentMaxId = orderIdCounter.get();
            if (orderId > currentMaxId) {
                if (orderIdCounter.compareAndSet(currentMaxId, orderId)) {
                    break; // Successfully updated
                }
            } else {
                break; // Provided ID is not higher, or another thread updated it
            }
        }
        Order newOrder = new Order(orderId, userId, itemId, side, orderType, price, OrderStatus.OPEN, timestamp, quantity);
        insertOrder(newOrder); // Persist new order to DB
        return getMatchingEngine(itemId).submitOrder(newOrder);
    }

    public boolean cancelOrder(int orderId) {
        // Retrieve order from DB to get itemId, then cancel in engine and update DB
        Order orderToCancel = null;
        String selectSql = "SELECT ITEM_ID FROM ORDERS WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
            pstmt.setInt(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int itemId = rs.getInt("ITEM_ID");
                MatchingEngine engine = matchingEngines.get(itemId);
                if (engine != null) {
                    boolean cancelledInEngine = engine.cancelOrder(orderId);
                    if (cancelledInEngine) {
                        updateOrderStatus(orderId, OrderStatus.CANCELLED); // Update DB
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving order for cancellation " + orderId + ": " + e.getMessage());
        }
        return false;
    }

    // New method to retrieve order status from the database
    public OrderStatus getOrderStatus(int orderId) {
        String sql = "SELECT STATUS FROM ORDERS WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, orderId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return OrderStatus.valueOf(rs.getString("STATUS"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting status for order " + orderId + ": " + e.getMessage());
        }
        return null; // Or throw an exception if order not found
    }

    // New method to generate a new unique trade ID (e.g., from DB sequence)
    public int getNewTradeId() {
        // In a real system, this would use a database sequence or UUID
        // For H2, we can query MAX(ID) from TRADES and increment
        String sql = "SELECT MAX(ID) FROM TRADES";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt(1) + 1;
            }
        } catch (SQLException e) {
            System.err.println("Error generating new trade ID: " + e.getMessage());
        }
        return 1; // Default to 1 if no trades exist
    }

    // Existing query methods will be refactored to query H2 directly in later steps

    public List<Order> queryOrderBook(int itemId) {
        return getOpenOrdersByItem(itemId);
    }

    // New method to retrieve open orders for a specific item from the database
    public List<Order> getOpenOrdersByItem(int itemId) {
        List<Order> openOrders = new java.util.ArrayList<>();
        String sql = "SELECT * FROM ORDERS WHERE ITEM_ID = ? AND STATUS = ? ORDER BY CASE WHEN SIDE = 'BUY' THEN PRICE ELSE -PRICE END DESC, TIMESTAMP ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, OrderStatus.OPEN.name());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                openOrders.add(createOrderFromResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error querying open orders for item " + itemId + ": " + e.getMessage());
        }
        return openOrders;
    }

    public List<Trade> queryTradeHistory(int itemId) {
        return getTradesByItem(itemId);
    }

    // New method to retrieve trades for a specific item from the database
    public List<Trade> getTradesByItem(int itemId) {
        List<Trade> trades = new java.util.ArrayList<>();
        String sql = "SELECT * FROM TRADES WHERE ITEM_ID = ? ORDER BY TIMESTAMP DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                trades.add(createTradeFromResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error querying trades for item " + itemId + ": " + e.getMessage());
        }
        return trades;
    }

    public double getAverageTradePrice(int itemId) {
        String sql = "SELECT AVG(PRICE) FROM TRADES WHERE ITEM_ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            System.err.println("Error calculating average trade price for item " + itemId + ": " + e.getMessage());
        }
        return 0.0;
    }

    public int getUnmatchedOrderCount(int itemId) {
        String sql = "SELECT COUNT(*) FROM ORDERS WHERE ITEM_ID = ? AND STATUS = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            pstmt.setString(2, OrderStatus.OPEN.name());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting unmatched order count for item " + itemId + ": " + e.getMessage());
        }
        return 0;
    }

    // New method to get total executed trades for a specific item
    public int getTotalExecutedTradesByItem(int itemId) {
        String sql = "SELECT COUNT(*) FROM TRADES WHERE ITEM_ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, itemId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting total executed trades for item " + itemId + ": " + e.getMessage());
        }
        return 0;
    }

    public int getTotalExecutedTrades() {
        String sql = "SELECT COUNT(*) FROM TRADES";
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting total executed trades: " + e.getMessage());
        }
        return 0;
    }

    public int getTotalUnmatchedOrders() {
        String sql = "SELECT COUNT(*) FROM ORDERS WHERE STATUS = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, OrderStatus.OPEN.name());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error getting total unmatched orders: " + e.getMessage());
        }
        return 0;
    }

    // Helper method to create an Order object from a ResultSet row
    private Order createOrderFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("ID");
        int userId = rs.getInt("USER_ID");
        int itemId = rs.getInt("ITEM_ID");
        Side side = Side.valueOf(rs.getString("SIDE"));
        OrderType orderType = OrderType.valueOf(rs.getString("ORDER_TYPE"));
        Double price = rs.getDouble("PRICE");
        if (rs.wasNull()) {
            price = null;
        }
        OrderStatus status = OrderStatus.valueOf(rs.getString("STATUS"));
        LocalDateTime timestamp = rs.getTimestamp("TIMESTAMP").toLocalDateTime();
        int quantity = rs.getInt("QUANTITY");
        return new Order(id, userId, itemId, side, orderType, price, status, timestamp, quantity);
    }

    // Helper method to create a Trade object from a ResultSet row
    private Trade createTradeFromResultSet(ResultSet rs) throws SQLException {
        int id = rs.getInt("ID");
        int buyerId = rs.getInt("BUYER_ID");
        int buyOrderId = rs.getInt("BUY_ORDER_ID");
        int sellerId = rs.getInt("SELLER_ID");
        int sellOrderId = rs.getInt("SELL_ORDER_ID");
        int itemId = rs.getInt("ITEM_ID");
        double price = rs.getDouble("PRICE");
        LocalDateTime timestamp = rs.getTimestamp("TIMESTAMP").toLocalDateTime();
        int quantity = rs.getInt("QUANTITY"); // Read quantity from DB
        return new Trade(id, buyerId, buyOrderId, sellerId, sellOrderId, itemId, price, timestamp, quantity);
    }
}
