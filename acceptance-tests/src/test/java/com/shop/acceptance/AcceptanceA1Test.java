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

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA1Test extends AcceptanceTestBase {

    @Test
    void A1_concurrentOrders_oneConfirmed() throws Exception {
        JdbcTestSupport.setStock(harness.inventoryJdbcUrl, "LASTITEM", 1);
        ExecutorService pool = Executors.newFixedThreadPool(50);
        List<Future<UUID>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                JsonNode node = client.createOrder("LASTITEM", 1, "ok", "a1-" + idx + "-" + UUID.randomUUID());
                return UUID.fromString(node.get("id").asText());
            }));
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);
        List<UUID> orderIds = new ArrayList<>();
        for (Future<UUID> f : futures) {
            orderIds.add(f.get());
        }
        harness.ensureAllRunning();

        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() -> {
            int confirmed = 0;
            int cancelled = 0;
            for (UUID id : orderIds) {
                String status = client.getOrder(id).get("status").asText();
                if ("CONFIRMED".equals(status)) {
                    confirmed++;
                } else if ("CANCELLED".equals(status)) {
                    cancelled++;
                }
            }
            return confirmed == 1 && cancelled == 49;
        });
        assertEquals(0, JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "LASTITEM"));
    }
}
