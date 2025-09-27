package com.vitea.marketplace.models;

import java.time.LocalDateTime;

public class Order {
    private int id;
    private int userId;
    private int itemId;
    private Side side;
    private OrderType orderType;
    private Double price;
    private OrderStatus status;
    private LocalDateTime timestamp;
    private int quantity;

    public Order(int id, int userId, int itemId, Side side, OrderType orderType, Double price, OrderStatus status, LocalDateTime timestamp, int quantity) {
        this.id = id;
        this.userId = userId;
        this.itemId = itemId;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.status = status;
        this.timestamp = timestamp;
        this.quantity = quantity;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "Order{" +
               "id=" + id +
               ", userId=" + userId +
               ", itemId=" + itemId +
               ", side=" + side +
               ", orderType=" + orderType +
               ", price=" + price +
               ", status=" + status +
               ", timestamp=" + timestamp +
               ", quantity=" + quantity +
               '}';
    }
}
