package com.vitea.marketplace.services;

import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.models.Trade;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MatchingEngine {

    private final int itemId;
    private final PriorityBlockingQueue<Order> buyOrders;
    private final PriorityBlockingQueue<Order> sellOrders;
    private final Map<Integer, Order> allOpenOrders; // Still keep for quick in-memory lookup during matching
    // private final List<Trade> tradeHistory; // No longer managed directly by MatchingEngine

    private volatile Double lastTradedPrice;
    private final AtomicInteger totalExecutedTrades;
    private final OrderService orderService; // Dependency injection of OrderService

    public MatchingEngine(int itemId, OrderService orderService) {
        this.itemId = itemId;
        this.orderService = orderService; // Initialize orderService
        // Max heap for buy orders (highest price first, then earliest timestamp)
        this.buyOrders = new PriorityBlockingQueue<>(
                11, (o1, o2) -> {
            int priceComparison = Double.compare(o2.getPrice() != null ? o2.getPrice() : Double.MIN_VALUE,
                                                 o1.getPrice() != null ? o1.getPrice() : Double.MIN_VALUE);
            if (priceComparison == 0) {
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
            return priceComparison;
        });
        // Min heap for sell orders (lowest price first, then earliest timestamp)
        this.sellOrders = new PriorityBlockingQueue<>(
                11, (o1, o2) -> {
            int priceComparison = Double.compare(o1.getPrice() != null ? o1.getPrice() : Double.MAX_VALUE,
                                                 o2.getPrice() != null ? o2.getPrice() : Double.MAX_VALUE);
            if (priceComparison == 0) {
                return o1.getTimestamp().compareTo(o2.getTimestamp());
            }
            return priceComparison;
        });
        this.allOpenOrders = new ConcurrentHashMap<>();
        // this.tradeHistory = new ArrayList<>(); // Removed
        this.lastTradedPrice = null;
        this.totalExecutedTrades = new AtomicInteger(0);
    }

    public int getItemId() {
        return itemId;
    }

    public void reset() {
        buyOrders.clear();
        sellOrders.clear();
        allOpenOrders.clear();
        // tradeHistory.clear(); // Removed
        lastTradedPrice = null;
        totalExecutedTrades.set(0);
        // The database will be truncated by OrderService.reset()
    }

    public synchronized Order submitOrder(Order newOrder) {
        if (newOrder.getStatus() == OrderStatus.CANCELLED) {
            return newOrder; // Return the cancelled order itself
        }

        allOpenOrders.put(newOrder.getId(), newOrder);

        // No longer need to pass executedTrades list, as trades are inserted directly to DB

        if (newOrder.getSide() == Side.BUY) {
            buyOrders.offer(newOrder);
            matchOrders(newOrder, sellOrders);
        } else {
            sellOrders.offer(newOrder);
            matchOrders(newOrder, buyOrders);
        }
        return newOrder;
    }

    private void matchOrders(Order newOrder, PriorityBlockingQueue<Order> opposingOrders) {
        List<Order> skippedOrders = new ArrayList<>();
        while (!opposingOrders.isEmpty() && newOrder.getStatus() == OrderStatus.OPEN) {
            Order opposingOrder = opposingOrders.poll(); // Use poll to remove the order from the queue
            if (opposingOrder == null) {
                break; // Should not happen with !isEmpty(), but for safety
            }

            // Skip if the opposing order is already cancelled (from DB perspective, or by another engine)
            if (orderService.getOrderStatus(opposingOrder.getId()) == OrderStatus.CANCELLED) { // Check DB status
                continue; // Try to match with the next best opposing order
            }

            // Ensure the opposing order is still open and not already filled by another process/engine
            if (orderService.getOrderStatus(opposingOrder.getId()) != OrderStatus.OPEN) {
                continue;
            }

            // Check for quantity match (exact quantity match required)
            if (newOrder.getQuantity() != opposingOrder.getQuantity()) {
                skippedOrders.add(opposingOrder); // Add to skipped, will be re-added later
                continue; // Continue to the next opposing order if quantities don't match
            }

            // Price checks
            boolean priceMatch = false;
            double tradePrice = 0.0;

            if (newOrder.getOrderType() == OrderType.AT_PRICE && opposingOrder.getOrderType() == OrderType.AT_PRICE) {
                if (newOrder.getSide() == Side.BUY) { // New order is BUY AT_PRICE
                    if (newOrder.getPrice() != null && opposingOrder.getPrice() != null && newOrder.getPrice() >= opposingOrder.getPrice()) {
                        priceMatch = true;
                        tradePrice = newOrder.getTimestamp().isBefore(opposingOrder.getTimestamp()) ? newOrder.getPrice() : opposingOrder.getPrice();
                    }
                } else if (newOrder.getSide() == Side.SELL) { // New order is SELL AT_PRICE
                    if (newOrder.getPrice() != null && opposingOrder.getPrice() != null && newOrder.getPrice() <= opposingOrder.getPrice()) {
                        priceMatch = true;
                        tradePrice = newOrder.getTimestamp().isBefore(opposingOrder.getTimestamp()) ? newOrder.getPrice() : opposingOrder.getPrice();
                    }
                }
            } else if (newOrder.getOrderType() == OrderType.OPEN || opposingOrder.getOrderType() == OrderType.OPEN) {
                // One or both orders are OPEN. Price needs to be determined by fallback or explicit AtPrice order.
                tradePrice = determineFallbackPrice();
                priceMatch = true; // An OPEN order always matches on price if quantities align.

                // Additional validation for AT_PRICE vs OPEN scenario.
                // If new order is AT_PRICE and opposing is OPEN, ensure AT_PRICE conditions are met against fallback.
                if (newOrder.getOrderType() == OrderType.AT_PRICE && newOrder.getPrice() != null) {
                    if (newOrder.getSide() == Side.BUY && newOrder.getPrice() < tradePrice) {
                        priceMatch = false;
                    } else if (newOrder.getSide() == Side.SELL && newOrder.getPrice() > tradePrice) {
                        priceMatch = false;
                    }
                }
                if (opposingOrder.getOrderType() == OrderType.AT_PRICE && opposingOrder.getPrice() != null) {
                    if (opposingOrder.getSide() == Side.BUY && opposingOrder.getPrice() < tradePrice) {
                        priceMatch = false;
                    } else if (opposingOrder.getSide() == Side.SELL && opposingOrder.getPrice() > tradePrice) {
                        priceMatch = false;
                    }
                }
            }

            if (priceMatch) {
                // Matched successfully, update statuses in DB via OrderService
                orderService.updateOrderStatus(newOrder.getId(), OrderStatus.FILLED);
                orderService.updateOrderStatus(opposingOrder.getId(), OrderStatus.FILLED);

                newOrder.setStatus(OrderStatus.FILLED); // Update in-memory for immediate return value
                opposingOrder.setStatus(OrderStatus.FILLED); // Update in-memory for consistency within engine

                allOpenOrders.remove(newOrder.getId());
                allOpenOrders.remove(opposingOrder.getId());

                totalExecutedTrades.incrementAndGet();
                lastTradedPrice = tradePrice;

                // No longer need to remove from individual queues here; they are managed by allOpenOrders and DB queries

                Trade trade = createTrade(newOrder, opposingOrder, tradePrice);
                orderService.insertTrade(trade); // Persist trade to DB
                // executedTrades.add(trade); // Removed
            } else {
                skippedOrders.add(opposingOrder); // Add to skipped, will be re-added later
                // Continue the loop to check for other matches
            }
        }
        // Re-add any skipped orders back to the opposing queue
        for (Order skippedOrder : skippedOrders) {
            opposingOrders.offer(skippedOrder);
        }
    }

    private double determineFallbackPrice() {
        // 1. Last traded price for the item
        // The lastTradedPrice should ideally be queried from the DB for consistency
        // For now, we will keep the in-memory lastTradedPrice. This is a potential area for future refinement if strict DB consistency is needed here.
        if (lastTradedPrice != null) {
            return lastTradedPrice;
        }

        // Filter open AT_PRICE orders from allOpenOrders
        List<Order> openAtPriceOrders = allOpenOrders.values().stream()
                .filter(order -> order.getStatus() == OrderStatus.OPEN && order.getOrderType() == OrderType.AT_PRICE && order.getPrice() != null)
                .collect(Collectors.toList());

        // 2. Earliest AtPrice sell order price (considering active orders only)
        // Find the lowest priced sell order among active AT_PRICE orders
        Double earliestSellPrice = openAtPriceOrders.stream()
                .filter(order -> order.getSide() == Side.SELL)
                .min(Comparator.comparing(Order::getPrice).thenComparing(Order::getTimestamp))
                .map(Order::getPrice)
                .orElse(null);

        if (earliestSellPrice != null) {
            return earliestSellPrice;
        }

        // 3. Earliest AtPrice buy order price (considering active orders only)
        // Find the highest priced buy order among active AT_PRICE orders
        Double earliestBuyPrice = openAtPriceOrders.stream()
                .filter(order -> order.getSide() == Side.BUY)
                .max(Comparator.comparing(Order::getPrice).thenComparing(Order::getTimestamp).reversed())
                .map(Order::getPrice)
                .orElse(null);

        if (earliestBuyPrice != null) {
            return earliestBuyPrice;
        }

        // 4. System default $1000
        return 1000.0;
    }

    // This method is now responsible for generating a unique trade ID if needed, or rely on DB auto-increment
    private Trade createTrade(Order newOrder, Order opposingOrder, double price) {
        int buyerId = (newOrder.getSide() == Side.BUY) ? newOrder.getUserId() : opposingOrder.getUserId();
        int buyOrderId = (newOrder.getSide() == Side.BUY) ? newOrder.getId() : opposingOrder.getId();
        int sellerId = (newOrder.getSide() == Side.SELL) ? newOrder.getUserId() : opposingOrder.getUserId();
        int sellOrderId = (newOrder.getSide() == Side.SELL) ? newOrder.getId() : opposingOrder.getId();
        int tradeId = orderService.getNewTradeId(); // Get a new trade ID from OrderService
        return new Trade(tradeId, buyerId, buyOrderId, sellerId, sellOrderId, itemId, price, LocalDateTime.now(), newOrder.getQuantity()); // Assuming trade quantity is newOrder.getQuantity()
    }

    public synchronized boolean cancelOrder(int orderId) {
        Order order = allOpenOrders.get(orderId);
        if (order != null) {
            // Update in-memory status
            order.setStatus(OrderStatus.CANCELLED);
            allOpenOrders.remove(orderId);
            // Do not remove from buyOrders/sellOrders here, as they are implicitly managed by allOpenOrders
            // and will be re-populated by DB queries when getOrderBook is called.
            if (order.getSide() == Side.BUY) {
                buyOrders.remove(order);
            } else {
                sellOrders.remove(order);
            }

            // Notify OrderService to update DB
            // This call is redundant as OrderService.cancelOrder already updates DB
            // orderService.updateOrderStatus(orderId, OrderStatus.CANCELLED);
            return true;
        }
        return false;
    }

    public List<Order> getOrderBook() {
        // This method will now query the DB via OrderService
        return orderService.getOpenOrdersByItem(itemId);
    }

    public List<Trade> getTradeHistory() {
        // This method will now query the DB via OrderService
        return orderService.getTradesByItem(itemId);
    }

    public double getAverageTradePrice() {
        // This method will now query the DB via OrderService
        return orderService.getAverageTradePrice(itemId);
    }

    public int getUnmatchedOrderCount() {
        // This method will now query the DB via OrderService
        return orderService.getUnmatchedOrderCount(itemId);
    }

    public int getTotalExecutedTrades() {
        // This method will now query the DB via OrderService
        // No longer needs to mapToInt MatchingEngine::getTotalExecutedTrades
        return orderService.getTotalExecutedTradesByItem(itemId);
    }
}
