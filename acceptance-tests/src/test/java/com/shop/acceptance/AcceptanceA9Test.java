package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA9Test extends AcceptanceTestBase {

    @Test
    void A9_shipPending_returns409() throws Exception {
        JsonNode order = client.createOrder("MILK", 1, "ok", "a9-" + UUID.randomUUID());
        UUID orderId = UUID.fromString(order.get("id").asText());

        assertEquals(409, client.postShip(orderId));
        assertEquals("PENDING", client.getOrder(orderId).get("status").asText());
    }
}
