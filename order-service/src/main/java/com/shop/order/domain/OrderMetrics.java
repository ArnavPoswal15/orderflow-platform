package com.shop.order.domain;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter created;
    private final Counter cancelled;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.created = Counter.builder("shop.orders.created")
                .description("Orders created")
                .register(meterRegistry);
        this.cancelled = Counter.builder("shop.orders.cancelled")
                .description("Orders cancelled")
                .register(meterRegistry);
    }

    public void orderCreated() {
        created.increment();
    }

    public void orderCancelled() {
        cancelled.increment();
    }
}
