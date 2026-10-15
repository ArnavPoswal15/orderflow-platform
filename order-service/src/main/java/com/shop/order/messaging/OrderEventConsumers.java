package com.shop.order.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.TraceContext;
import com.shop.events.payload.InventoryRejectedPayload;
import com.shop.events.payload.InventoryReleasedPayload;
import com.shop.events.payload.InventoryReservedPayload;
import com.shop.events.payload.PaymentFailedPayload;
import com.shop.order.domain.OrderApplicationService;
import com.shop.order.domain.OrderStatus;
import com.shop.order.domain.StockCacheService;
import com.shop.order.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OrderEventConsumers {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumers.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderApplicationService orderApplicationService;
    private final StockCacheService stockCacheService;

    public OrderEventConsumers(
            ProcessedEventRepository processedEventRepository,
            OrderApplicationService orderApplicationService,
            StockCacheService stockCacheService) {
        this.processedEventRepository = processedEventRepository;
        this.orderApplicationService = orderApplicationService;
        this.stockCacheService = stockCacheService;
    }

    @KafkaListener(topics = EventTopics.INVENTORY, groupId = "order-service-inventory")
    @Transactional
    public void onInventoryEvent(String raw, Acknowledgment ack) {
        handle(raw, ack, envelope -> {
            if (EventTypes.INVENTORY_RESERVED.equals(envelope.type())) {
                invalidateStockFromReserved(envelope);
                orderApplicationService.transition(
                        envelope.orderId(), OrderStatus.RESERVED, EventTypes.INVENTORY_RESERVED, Map.of());
            } else if (EventTypes.INVENTORY_REJECTED.equals(envelope.type())) {
                InventoryRejectedPayload payload =
                        EventJson.mapper().convertValue(envelope.payload(), InventoryRejectedPayload.class);
                if (payload.sku() != null) {
                    stockCacheService.invalidate(payload.sku());
                }
                orderApplicationService.transition(
                        envelope.orderId(),
                        OrderStatus.CANCELLED,
                        EventTypes.INVENTORY_REJECTED,
                        Map.of("reason", payload.reason() != null ? payload.reason() : "out_of_stock"));
            } else if (EventTypes.INVENTORY_RELEASED.equals(envelope.type())) {
                invalidateStockFromReleased(envelope);
            }
        });
    }

    @KafkaListener(topics = EventTopics.PAYMENTS, groupId = "order-service-payments")
    @Transactional
    public void onPaymentEvent(String raw, Acknowledgment ack) {
        handle(raw, ack, envelope -> {
            if (EventTypes.PAYMENT_SUCCEEDED.equals(envelope.type())) {
                orderApplicationService.transition(
                        envelope.orderId(), OrderStatus.CONFIRMED, EventTypes.PAYMENT_SUCCEEDED, Map.of());
            } else if (EventTypes.PAYMENT_FAILED.equals(envelope.type())) {
                PaymentFailedPayload payload =
                        EventJson.mapper().convertValue(envelope.payload(), PaymentFailedPayload.class);
                orderApplicationService.transition(
                        envelope.orderId(),
                        OrderStatus.CANCELLED,
                        EventTypes.PAYMENT_FAILED,
                        Map.of("reason", payload.reason() != null ? payload.reason() : "payment_failed"));
            }
        });
    }

    private void invalidateStockFromReserved(EventEnvelope envelope) {
        InventoryReservedPayload payload =
                EventJson.mapper().convertValue(envelope.payload(), InventoryReservedPayload.class);
        stockCacheService.invalidateAll(skusFromItems(payload != null ? payload.items() : null));
    }

    private void invalidateStockFromReleased(EventEnvelope envelope) {
        InventoryReleasedPayload payload =
                EventJson.mapper().convertValue(envelope.payload(), InventoryReleasedPayload.class);
        stockCacheService.invalidateAll(skusFromItems(payload != null ? payload.items() : null));
    }

    private static List<String> skusFromItems(List<com.shop.events.payload.OrderLineItem> items) {
        if (items == null) {
            return List.of();
        }
        List<String> skus = new ArrayList<>();
        for (var item : items) {
            if (item.sku() != null) {
                skus.add(item.sku());
            }
        }
        return skus;
    }

    private void handle(String raw, Acknowledgment ack, Consumer<EventEnvelope> handler) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        TraceContext.bind(envelope);
        log.info("consumed");
        handler.accept(envelope);
        ack.acknowledge();
    }
}
