package com.shop.order;

import com.redis.testcontainers.RedisContainer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.enabled=false",
        "shop.kafka.listeners.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class OrderServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders")
            .withUsername("shop")
            .withPassword("shop");

    @Container
    static RedisContainer redis = new RedisContainer("redis:7-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("inventory.base-url", () -> "http://localhost:8081");
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void contextLoads() {
    }

    @Test
    void outboxBacklogGaugeIsRegistered() {
        assertNotNull(meterRegistry.find("shop.outbox.backlog").gauge());
    }
}
