package com.vitea.marketplace.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class H2DatabaseUtil {

    private static final String DB_URL = "jdbc:h2:mem:marketplace;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static Connection initializeDatabase() throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            Statement stmt = conn.createStatement();

            // Drop tables if they exist to ensure a clean state for each test run
            stmt.execute("DROP TABLE IF EXISTS trades");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("DROP TABLE IF EXISTS items");

            // Create Items table
            stmt.execute("CREATE TABLE IF NOT EXISTS items (\n" +
                         "    id INT PRIMARY KEY AUTO_INCREMENT,\n" +
                         "    name VARCHAR(255) NOT NULL\n" +
                         ")");

            // Create Users table
            stmt.execute("CREATE TABLE IF NOT EXISTS users (\n" +
                         "    id INT PRIMARY KEY AUTO_INCREMENT,\n" +
                         "    name VARCHAR(255) NOT NULL\n" +
                         ")");

            // Create Orders table
            stmt.execute("CREATE TABLE IF NOT EXISTS orders (\n" +
                         "    id INT PRIMARY KEY AUTO_INCREMENT,\n" +
                         "    user_id INT NOT NULL,\n" +
                         "    item_id INT NOT NULL,\n" +
                         "    side VARCHAR(10) NOT NULL,\n" +
                         "    order_type VARCHAR(20) NOT NULL,\n" +
                         "    price DOUBLE,\n" +
                         "    status VARCHAR(20) NOT NULL,\n" +
                         "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                         "    quantity INT NOT NULL,\n" +
                         "    FOREIGN KEY (user_id) REFERENCES users(id),\n" +
                         "    FOREIGN KEY (item_id) REFERENCES items(id)\n" +
                         ")");

            // Create Trades table
            stmt.execute("CREATE TABLE IF NOT EXISTS trades (\n" +
                         "    id INT PRIMARY KEY AUTO_INCREMENT,\n" +
                         "    buyer_id INT NOT NULL,\n" +
                         "    buy_order_id INT NOT NULL,\n" +
                         "    seller_id INT NOT NULL,\n" +
                         "    sell_order_id INT NOT NULL,\n" +
                         "    item_id INT NOT NULL,\n" +
                         "    price DOUBLE NOT NULL,\n" +
                         "    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n" +
                         "    quantity INT NOT NULL,\n" +
                         "    FOREIGN KEY (buyer_id) REFERENCES users(id),\n" +
                         "    FOREIGN KEY (buy_order_id) REFERENCES orders(id),\n" +
                         "    FOREIGN KEY (seller_id) REFERENCES users(id),\n" +
                         "    FOREIGN KEY (sell_order_id) REFERENCES orders(id),\n" +
                         "    FOREIGN KEY (item_id) REFERENCES items(id)\n" +
                         ")");

            System.out.println("H2 Database initialized successfully.");
            return conn; // Return the connection

        } catch (SQLException e) {
            System.err.println("Error initializing H2 database: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw the exception to be handled upstream
        } finally {
            // Do not close connection here, it will be closed by the caller
        }
    }

    // New method to truncate all tables in correct order to maintain referential integrity
    public static void resetAllTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Temporarily disable referential integrity checks
            stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

            stmt.execute("TRUNCATE TABLE TRADES");
            stmt.execute("TRUNCATE TABLE ORDERS");
            stmt.execute("TRUNCATE TABLE USERS");
            stmt.execute("TRUNCATE TABLE ITEMS");

            // Re-enable referential integrity checks
            stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");

        } catch (SQLException e) {
            System.err.println("Error truncating tables during reset: " + e.getMessage());
            throw e; // Re-throw to indicate a problem
        }
    }

    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }
}
