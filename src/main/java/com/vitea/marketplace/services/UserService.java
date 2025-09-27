package com.vitea.marketplace.services;

import com.vitea.marketplace.models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class UserService {
    private final Connection connection;
    private final AtomicInteger userIdCounter;

    public UserService(Connection connection) {
        this.connection = connection;
        this.userIdCounter = new AtomicInteger(0);
        // Initialize counter based on existing users in DB
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM USERS");
            if (rs.next()) {
                userIdCounter.set(rs.getInt(1));
            }
        } catch (SQLException e) {
            System.err.println("Error initializing userIdCounter: " + e.getMessage());
        }
    }

    public void reset() {
        // Clear users table in DB
        // The database tables will be truncated by H2DatabaseUtil.resetAllTables() in test setup methods.
        userIdCounter.set(0);
    }

    public User createUser(String name) {
        String lowerCaseName = name.toLowerCase();
        // Check if user already exists in DB
        if (getUserIdByName(name) != -1) {
            System.out.println("User '" + name + "' already exists. Returning existing user.");
            return getUserByName(name).orElse(null); // Retrieve from DB
        }

        int newId = userIdCounter.incrementAndGet();
        String sql = "INSERT INTO USERS (ID, NAME) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, newId);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
            User user = new User(newId, name);
            System.out.println("Created user: " + user.getName() + " with ID: " + user.getId());
            return user;
        } catch (SQLException e) {
            System.err.println("Error creating user " + name + ": " + e.getMessage());
            userIdCounter.decrementAndGet(); // Rollback counter if DB insert fails
            return null;
        }
    }

    public Optional<User> getUserById(int id) {
        String sql = "SELECT ID, NAME FROM USERS WHERE ID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new User(rs.getInt("ID"), rs.getString("NAME")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by ID " + id + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<User> getUserByName(String name) {
        String sql = "SELECT ID, NAME FROM USERS WHERE LOWER(NAME) = LOWER(?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return Optional.of(new User(rs.getInt("ID"), rs.getString("NAME")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by name \'" + name + "\': " + e.getMessage());
        }
        return Optional.empty();
    }

    public int getUserIdByName(String name) {
        return getUserByName(name).map(User::getId).orElse(-1);
    }
}
