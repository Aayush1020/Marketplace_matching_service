package com.vitea.marketplace.db;

import com.vitea.marketplace.models.Item;
import com.vitea.marketplace.models.Order;
import com.vitea.marketplace.models.OrderType;
import com.vitea.marketplace.models.Side;
import com.vitea.marketplace.models.OrderStatus;
import com.vitea.marketplace.models.User;
import com.vitea.marketplace.services.ItemService;
import com.vitea.marketplace.services.OrderService;
import com.vitea.marketplace.services.UserService;

import java.time.LocalDateTime;
import java.sql.Connection; // Added this import

public class DataLoader {

    private final ItemService itemService;
    private final UserService userService;
    private final OrderService orderService;

    public DataLoader(ItemService itemService, UserService userService, OrderService orderService, Connection connection) {
        this.itemService = itemService;
        this.userService = userService;
        this.orderService = new OrderService(connection); // Instantiate OrderService with connection
    }

    public void loadSeedData() {
        System.out.println("Loading seed data...");

        // Seed Items
        Item replicaA = itemService.createItem("Replica A"); // ID 1
        Item replicaB = itemService.createItem("Replica B"); // ID 2

        // Seed Users
        User alice = userService.createUser("Alice"); // ID 1
        User bob = userService.createUser("Bob");     // ID 2
        User charlie = userService.createUser("Charlie"); // ID 3

        // Seed Orders (these will go through the matching engine)
        // Use explicit, staggered timestamps to ensure predictable time priority
        LocalDateTime timestamp1 = LocalDateTime.of(2025, 9, 27, 10, 0, 0);
        LocalDateTime timestamp2 = LocalDateTime.of(2025, 9, 27, 10, 0, 1);
        LocalDateTime timestamp3 = LocalDateTime.of(2025, 9, 27, 10, 0, 2);
        LocalDateTime timestamp4 = LocalDateTime.of(2025, 9, 27, 10, 0, 3);

        // Order 1: id=1, user_id=1, item_id=1, side=BUY, order_type=AT_PRICE, price=1000, quantity=1, status=OPEN
        // After order 2 is submitted, this should result in a trade.
        orderService.submitOrder(1, alice.getId(), replicaA.getId(), Side.BUY, OrderType.AT_PRICE, 1000.0, 1, timestamp1);

        // Order 2: id=2, user_id=2, item_id=1, side=SELL, order_type=AT_PRICE, price=950, quantity=1, status=OPEN
        // This order should match with Order 1, resulting in a trade.
        orderService.submitOrder(2, bob.getId(), replicaA.getId(), Side.SELL, OrderType.AT_PRICE, 950.0, 1, timestamp2);

        // Order 3: id=3, user_id=1, item_id=2, side=BUY, order_type=OPEN, price=NULL, quantity=1, status=OPEN
        // After order 4 is submitted, this should result in a trade.
        orderService.submitOrder(3, alice.getId(), replicaB.getId(), Side.BUY, OrderType.OPEN, null, 1, timestamp3);

        // Order 4: id=4, user_id=3, item_id=2, side=SELL, order_type=OPEN, price=NULL, quantity=1, status=OPEN
        // This order should match with Order 3, resulting in a trade (using fallback price).
        orderService.submitOrder(4, charlie.getId(), replicaB.getId(), Side.SELL, OrderType.OPEN, null, 1, timestamp4);

        System.out.println("Seed data loaded.");
    }
}
