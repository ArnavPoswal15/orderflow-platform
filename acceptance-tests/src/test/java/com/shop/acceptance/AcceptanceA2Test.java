package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA2Test extends AcceptanceTestBase {

    @Test
    void A2_paymentFail_cancelsAndRestoresStock() throws Exception {
        int before = JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "EGGS");
        JsonNode order = client.createOrder("EGGS", 1, "fail", "a2-" + UUID.randomUUID());
        UUID orderId = UUID.fromString(order.get("id").asText());

        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CANCELLED".equals(client.getOrder(orderId).get("status").asText()));
        assertEquals(before, JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "EGGS"));
        assertNotEquals("SUCCEEDED", JdbcTestSupport.paymentStatus(harness.paymentsJdbcUrl, orderId));
    }
}
