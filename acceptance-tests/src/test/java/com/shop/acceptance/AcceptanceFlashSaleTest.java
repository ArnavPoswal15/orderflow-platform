package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceFlashSaleTest extends AcceptanceTestBase {

    @Test
    void flashSale_lastItem_oneConfirmedNineCancelled() throws Exception {
        JsonNode result = client.flashSale("LASTITEM", 10);
        assertEquals(1, result.get("confirmed").asInt());
        assertEquals(9, result.get("cancelled").asInt());

        JsonNode orders = result.get("orders");
        assertEquals(10, orders.size());
        int confirmed = 0;
        int cancelled = 0;
        for (JsonNode order : orders) {
            String status = order.get("status").asText();
            if ("CONFIRMED".equals(status)) {
                confirmed++;
            } else if ("CANCELLED".equals(status)) {
                cancelled++;
            }
        }
        assertEquals(1, confirmed);
        assertEquals(9, cancelled);
        assertEquals(0, JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "LASTITEM"));
    }
}
