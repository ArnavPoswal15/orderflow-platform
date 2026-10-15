package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA3Test extends AcceptanceTestBase {

    @Test
    void A3_parallelSameIdempotencyKey_oneOrderOneCharge() throws Exception {
        String idempotencyKey = "a3-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<HttpTestClient.RawOrderResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> client.createOrderRaw("MILK", 1, "ok", idempotencyKey)));
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);

        List<String> orderIds = new ArrayList<>();
        for (Future<HttpTestClient.RawOrderResponse> f : futures) {
            HttpTestClient.RawOrderResponse raw = f.get();
            assertTrue(raw.statusCode() == 200 || raw.statusCode() == 202, "status=" + raw.statusCode());
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw.body());
            orderIds.add(node.get("id").asText());
        }
        String firstId = orderIds.get(0);
        for (String id : orderIds) {
            assertEquals(firstId, id);
        }

        assertEquals(1, JdbcTestSupport.countOrders(harness.ordersJdbcUrl));
        assertEquals(1, JdbcTestSupport.countOrdersByIdempotencyKey(harness.ordersJdbcUrl, idempotencyKey));

        UUID orderId = UUID.fromString(firstId);
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "SUCCEEDED".equals(JdbcTestSupport.paymentStatus(harness.paymentsJdbcUrl, orderId)));
        assertEquals(1, JdbcTestSupport.countSucceededPayments(harness.paymentsJdbcUrl));
    }
}
