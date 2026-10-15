package com.shop.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

abstract class AcceptanceTestBase {

    protected static PlatformTestHarness harness;
    protected static HttpTestClient client;

    @BeforeAll
    static void startPlatform() throws Exception {
        harness = new PlatformTestHarness();
        harness.startAll();
        client = new HttpTestClient("http://localhost:" + harness.orderPort);
    }

    @AfterAll
    static void stopPlatform() {
        if (harness != null) {
            harness.stopAll();
        }
    }
}
