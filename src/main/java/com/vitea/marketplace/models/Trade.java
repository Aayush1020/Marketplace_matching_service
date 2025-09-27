package com.vitea.marketplace.models;

import java.time.LocalDateTime;

public class Trade {
    private int id;
    private int buyerId;
    private int buyOrderId;
    private int sellerId;
    private int sellOrderId;
    private int itemId;
    private double price;
    private LocalDateTime timestamp;
    private int quantity; // Added quantity field

    public Trade(int id, int buyerId, int buyOrderId, int sellerId, int sellOrderId, int itemId, double price, LocalDateTime timestamp, int quantity) {
        this.id = id;
        this.buyerId = buyerId;
        this.buyOrderId = buyOrderId;
        this.sellerId = sellerId;
        this.sellOrderId = sellOrderId;
        this.itemId = itemId;
        this.price = price;
        this.timestamp = timestamp;
        this.quantity = quantity; // Initialize quantity
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(int buyerId) {
        this.buyerId = buyerId;
    }

    public int getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(int buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public int getSellerId() {
        return sellerId;
    }

    public void setSellerId(int sellerId) {
        this.sellerId = sellerId;
    }

    public int getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(int sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getQuantity() { // Added quantity getter
        return quantity;
    }

    public void setQuantity(int quantity) { // Added quantity setter
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "Trade{" +
               "id=" + id +
               ", buyerId=" + buyerId +
               ", buyOrderId=" + buyOrderId +
               ", sellerId=" + sellerId +
               ", sellOrderId=" + sellOrderId +
               ", itemId=" + itemId +
               ", price=" + price +
               ", timestamp=" + timestamp +
               ", quantity=" + quantity + // Added quantity to toString
               '}';
    }
}
