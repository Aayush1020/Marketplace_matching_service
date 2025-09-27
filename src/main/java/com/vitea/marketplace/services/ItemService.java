package com.vitea.marketplace.services;

import com.vitea.marketplace.models.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class ItemService {
    private final Connection connection;
    private final AtomicInteger itemIdCounter;

    public ItemService(Connection connection) {
        this.connection = connection;
        this.itemIdCounter = new AtomicInteger(0);
        // Initialize counter based on existing items in DB
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM ITEMS");
            if (rs.next()) {
                itemIdCounter.set(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("Error initializing itemIdCounter: " + e.getMessage());
        }
    }

    public void reset() {
        // Clear items table in DB
        // The database tables will be truncated by H2DatabaseUtil.resetAllTables() in test setup methods.
        itemIdCounter.set(0);
    }

    public Item createItem(String name) {
        String lowerCaseName = name.toLowerCase();
        // Check if item already exists in DB
        if (getItemIdByName(name) != -1) {
            System.out.println("Item '" + name + "' already exists. Returning existing item.");
            return getItemByName(name).orElse(null); // Retrieve from DB
        }

        int newId = itemIdCounter.incrementAndGet();
        String sql = "INSERT INTO ITEMS (ID, NAME) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, newId);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
            Item item = new Item(newId, name);
            System.out.println("Created item: " + item.getName() + " with ID: " + item.getId());
            return item;
        } catch (SQLException e) {
            System.err.println("Error creating item " + name + ": " + e.getMessage());
            itemIdCounter.decrementAndGet(); // Rollback counter if DB insert fails
            return null;
        }
    }

    public Optional<Item> getItemById(int id) {
        String sql = "SELECT ID, NAME FROM ITEMS WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Item(rs.getInt("ID"), rs.getString("NAME")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting item by ID " + id + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Item> getItemByName(String name) {
        String sql = "SELECT ID, NAME FROM ITEMS WHERE LOWER(NAME) = LOWER(?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new Item(rs.getInt("ID"), rs.getString("NAME")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting item by name \'" + name + "\': " + e.getMessage());
        }
        return Optional.empty();
    }

    public int getItemIdByName(String name) {
        return getItemByName(name).map(Item::getId).orElse(-1);
    }
}
