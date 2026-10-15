package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA5Test extends AcceptanceTestBase {

    @Test
    void A5_outboxSurvivesPublisherRestart() throws Exception {
        harness.restartOrder(Map.of(), List.of("--outbox.enabled=false"));
        client = new HttpTestClient("http://localhost:" + harness.orderPort);
        JsonNode order = client.createOrder("MILK", 1, "ok", "a5-" + UUID.randomUUID());
        UUID orderId = UUID.fromString(order.get("id").asText());
        assertEquals(1, JdbcTestSupport.unpublishedOutboxCountForOrder(harness.ordersJdbcUrl, orderId));

        harness.restartOrder(Map.of(), List.of("--outbox.enabled=true"));
        client = new HttpTestClient("http://localhost:" + harness.orderPort);

        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                JdbcTestSupport.unpublishedOutboxCountForOrder(harness.ordersJdbcUrl, orderId) == 0);
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CONFIRMED".equals(client.getOrder(orderId).get("status").asText()));
    }
}
