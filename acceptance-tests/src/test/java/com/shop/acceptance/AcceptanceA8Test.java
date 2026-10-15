package com.shop.acceptance;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA8Test extends AcceptanceTestBase {

    @Test
    void A8_burstRateLimit_twentyAllowedFiveRejected() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(25);
        List<Future<HttpTestClient.RawOrderResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            int idx = i;
            futures.add(pool.submit(() ->
                    client.createOrderRaw("MILK", 1, "ok", "a8-burst-" + idx + "-" + UUID.randomUUID())));
        }
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.MINUTES);

        int ok = 0;
        int rateLimited = 0;
        for (Future<HttpTestClient.RawOrderResponse> f : futures) {
            HttpTestClient.RawOrderResponse raw = f.get();
            if (raw.statusCode() == 200 || raw.statusCode() == 202) {
                ok++;
            } else if (raw.statusCode() == 429) {
                rateLimited++;
                List<String> retryAfter = raw.headers().get("retry-after");
                assertTrue(retryAfter != null && !retryAfter.isEmpty());
                assertFalse(retryAfter.get(0).isBlank());
            }
        }
        assertEquals(20, ok);
        assertEquals(5, rateLimited);
    }
}
