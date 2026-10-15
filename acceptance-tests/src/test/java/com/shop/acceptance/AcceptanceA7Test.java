package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA7Test extends AcceptanceTestBase {

    @Test
    void A7_cancelConfirmed_refundsAndReleasesStock() throws Exception {
        JdbcTestSupport.setStock(harness.inventoryJdbcUrl, "MILK", 50);
        JsonNode order = client.createOrder("MILK", 1, "ok", "a7-" + UUID.randomUUID());
        UUID orderId = UUID.fromString(order.get("id").asText());
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CONFIRMED".equals(client.getOrder(orderId).get("status").asText()));

        int stockReserved = JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "MILK");
        assertEquals(202, client.postCancel(orderId));

        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CANCELLED".equals(client.getOrder(orderId).get("status").asText()));
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "REFUNDED".equals(JdbcTestSupport.paymentStatus(harness.paymentsJdbcUrl, orderId)));
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "RELEASED".equals(JdbcTestSupport.reservationStatus(harness.inventoryJdbcUrl, orderId)));
        assertEquals(stockReserved + 1, JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "MILK"));
    }
}
