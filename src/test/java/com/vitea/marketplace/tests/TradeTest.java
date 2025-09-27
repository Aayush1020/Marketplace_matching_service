package com.vitea.marketplace.tests;

import com.vitea.marketplace.models.Trade;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TradeTest {

    @Test
    void testTradeCreation() {
        LocalDateTime now = LocalDateTime.now();
        Trade trade = new Trade(1,
                                1001, // buyerId
                                2001, // buyOrderId
                                1002, // sellerId
                                2002, // sellOrderId
                                3001, // itemId
                                99.50, // price
                                now,
                                10); // quantity

        assertNotNull(trade);
        assertEquals(1, trade.getId());
        assertEquals(1001, trade.getBuyerId());
        assertEquals(2001, trade.getBuyOrderId());
        assertEquals(1002, trade.getSellerId());
        assertEquals(2002, trade.getSellOrderId());
        assertEquals(3001, trade.getItemId());
        assertEquals(99.50, trade.getPrice());
        assertEquals(now, trade.getTimestamp());
    }
}
