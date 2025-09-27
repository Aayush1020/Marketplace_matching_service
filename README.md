# Vitea Marketplace Matching Service

## Overview

This project is a prototype of a local marketplace matching service for collectible replicas. It is built with Java and utilizes an H2 in-memory database as the consistent source of truth for all data, Picocli for a command-line interface (CLI), and JUnit 5 for testing. The core matching logic, implemented in the `MatchingEngine`, uses in-memory priority queues for real-time order processing based on price/time priority and efficient fallback price determination for open orders.

## Project Structure

- `src/main/java/com.vitea.marketplace.cli`: Contains the command-line interface logic using Picocli.
- `src/main/java/com.vitea.marketplace.db`: Contains utilities for H2 database interaction, including schema creation and global table reset functionality.
- `src/main/java/com.vitea.marketplace.models`: Defines the data models (e.g., `Item`, `User`, `Order`, `Trade`).
- `src/main/java/com.vitea.marketplace.services`: Contains service classes for business logic, including the `OrderService` (responsible for database interactions for orders, trades, users, and items) and the `MatchingEngine` (responsible for real-time order matching logic).
- `src/test/java/com.vitea.marketplace.tests`: Contains JUnit test classes for various components.

## Database Schema (H2 In-Memory)

The application uses an H2 in-memory database with the following tables:

### `items`
- `id` (integer, primary key)
- `name` (text)

### `users`
- `id` (integer, primary key)
- `name` (text)

### `orders`
- `id` (integer, primary key)
- `user_id` (integer, FK to `users.id`)
- `item_id` (integer, FK to `items.id`)
- `side` (enum {BUY, SELL})
- `order_type` (enum {AT_PRICE, OPEN})
- `price` (double, nullable for Open orders)
- `status` (enum {OPEN, FILLED, CANCELLED})
- `timestamp` (timestamp)
- `quantity` (integer)

### `trades`
- `id` (integer, primary key)
- `buyer_id` (integer, FK to `users.id`)
- `buy_order_id` (integer, FK to `orders.id`)
- `seller_id` (integer, FK to `users.id`)
- `sell_order_id` (integer, FK to `orders.id`)
- `item_id` (integer, FK to `items.id`)
- `price` (double)
- `timestamp` (timestamp)
- `quantity` (integer)

## How to Compile and Run

This project uses Maven. To compile and package the application, navigate to the project root directory and run:

To run this project on your local machine, you will need to have Java Development Kit (JDK) and Apache Maven installed.

```bash
mvn clean install
```

This will compile the Java code and create an executable JAR in the `target/` directory.

The application automatically loads seed data (initial users, items, and orders) on startup.

### Interactive Mode
When run without any arguments, the CLI starts in interactive mode, allowing you to enter commands one by one.

```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar
```

Once in interactive mode, you will see a `marketplace>` prompt. Type `help` to see a list of available commands, or `exit` to quit.

```
marketplace> help
marketplace> query-orderbook "Replica A"
marketplace> query-trade-history "Replica A"
marketplace> create-user David
marketplace> submit-order David "Replica B" BUY AT_PRICE 1100.0 1
marketplace> exit
```

### Running in Batch Mode
To execute individual CLI commands directly (non-interactive mode), specify the command and its arguments after the JAR file:

```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar <command> [arguments]
```
For example:
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar create-user Alice
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar query-orderbook "Replica A"
```

## CLI Commands

The CLI supports the following commands, usable in both batch and interactive modes:

### Create User
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar create-user <userName>
```
- `userName`: The name of the user to create.

### Create Item
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar create-item <itemName>
```
- `itemName`: The name of the item to create.

### Submit Buy/Sell Orders
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar submit-order <userIdOrName> <itemIdOrName> <SIDE (BUY/SELL)> <ORDER_TYPE (AT_PRICE/OPEN)> <price> <quantity>
```
- `userIdOrName`: The ID or name of the user submitting the order.
- `itemIdOrName`: The ID or name of the item.
- `SIDE`: `BUY` or `SELL`.
- `ORDER_TYPE`: `AT_PRICE` (requires price) or `OPEN` (price is optional).
- `price`: The price for `AT_PRICE` orders. Can be `null` for `OPEN` orders.
- `quantity`: The quantity of the item.
- **Output**: The command will print the order ID upon successful submission and explicitly state if an order was "immediately filled" or "queued."

### Cancel Orders
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar cancel-order <orderId>
```
- `orderId`: The ID of the order to cancel.
- **Output**: A simplified confirmation message (e.g., "Order 123 cancelled successfully.") without verbose debugging prints.

### Query Order Book
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar query-orderbook <itemIdOrName>
```
- `itemIdOrName`: The ID or name of the item for which to query the order book. This command will also display the count of unmatched orders.

### Query Trade History
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar query-trade-history <itemIdOrName>
```
- `itemIdOrName`: The ID or name of the item for which to query the trade history. This command will also display the average trade price.

### Query Marketplace Metrics
```bash
java -jar target/ViteaMarketplaceMatchingService-1.0-SNAPSHOT.jar query-metrics
```
This command displays aggregate metrics for the entire marketplace, including:
-   **Total Executed Trades**: The total number of trades that have occurred across all items.
-   **Total Unmatched Orders**: The total count of open orders currently awaiting a match across all items.

## How to Run Tests

To run the JUnit tests, use Maven:

```bash
mvn test
```

## Seed Data

On application startup, the following seed data is loaded into the in-memory H2 database:

### Items
1.  **Replica A**: id=1
2.  **Replica B**: id=2

### Users
1.  **Alice**: id=1
2.  **Bob**: id=2
3.  **Charlie**: id=3

### Orders
(These orders are processed by the matching engine on startup, potentially leading to immediate trades.)
1.  **BUY Order** (id=1): user_id=1 (Alice), item_id=1 (Replica A), side=BUY, order_type=AT_PRICE, price=1000, quantity=1, status=OPEN
2.  **SELL Order** (id=2): user_id=2 (Bob), item_id=1 (Replica A), side=SELL, order_type=AT_PRICE, price=950, quantity=1, status=OPEN
    *Expected Outcome*: These two orders for "Replica A" will match, resulting in one trade with a price of 1000.0 (price of the first order).
3.  **BUY Order** (id=3): user_id=1 (Alice), item_id=2 (Replica B), side=BUY, order_type=OPEN, price=NULL, quantity=1, status=OPEN
4.  **SELL Order** (id=4): user_id=3 (Charlie), item_id=2 (Replica B), side=SELL, order_type=OPEN, price=NULL, quantity=1, status=OPEN
    *Expected Outcome*: These two orders for "Replica B" will match, resulting in one trade with a fallback price of 1000.0 (system default).

## Matching Logic

The matching engine implements the following rules:

1.  **Order Matching**
    -   Orders can be `AtPrice` (specific price) or `Open` (any price).
    -   A trade executes only if **buy and sell quantities match exactly**.
    -   When searching for an opposing order, the `MatchingEngine` looks through the entire queue of available opposing orders (not just the top priority one) to find a match with the exact quantity.
    -   Cancelled orders are ignored.
    -   Orders with no opposing matches remain queued.
    -   All order status updates and trade creations are immediately persisted to the H2 database via the `OrderService`.

2.  **Price/Time Priority**
    -   For a buy, match against the **lowest-priced sell** first.
    -   For a sell, match against the **highest-priced buy** first.
    -   If multiple orders have the same price, the **earliest timestamp** takes priority.
    -   When comparing Open orders to AtPrice orders, default to **timestamp priority**.

3.  **Fallback Price for Open Orders**
    -   If both sides are Open orders, the trade price is determined by:
        1.  The last traded price for the item.
        2.  The price of the earliest `AtPrice` sell order (among currently active orders).
        3.  The price of the earliest `AtPrice` buy order (among currently active orders).
        4.  A system default of $1000 if none of the above exist.

4.  **Trade Record**
    -   Upon execution, a `Trade` record is created with: id, buyer_id, buy_order_id, seller_id, sell_order_id, item_id, price, timestamp, and quantity. The `OrderService` handles generating unique `tradeId`s and persisting the trade to the database.
    -   Matched orders have their statuses updated to `FILLED` in the database.

5.  **Metrics**
    -   The system tracks total executed trades, average trade price per item, and the count of unmatched orders. The `query-metrics` CLI command provides total executed trades and total unmatched orders across the entire marketplace, querying the H2 database directly.

6.  **Edge Cases**
    -   If the maximum buy price is less than the minimum sell price, and no `Open` orders exist, orders remain queued.
    -   When two `AtPrice` orders match at different prices, the price of the order placed first is used.
