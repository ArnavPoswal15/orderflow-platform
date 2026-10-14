package com.shop.order.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.order.domain.OrderApplicationService;
import com.shop.order.domain.OrderStatus;
import com.shop.order.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Component
public class OrderEventConsumers {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumers.class);

    private final ProcessedEventRepository processedEventRepository;
    private final OrderApplicationService orderApplicationService;

    public OrderEventConsumers(
            ProcessedEventRepository processedEventRepository,
            OrderApplicationService orderApplicationService) {
        this.processedEventRepository = processedEventRepository;
        this.orderApplicationService = orderApplicationService;
    }

    @KafkaListener(topics = EventTopics.INVENTORY, groupId = "order-service-inventory")
    @Transactional
    public void onInventoryEvent(String raw, Acknowledgment ack) {
        handle(raw, ack, envelope -> {
            if (EventTypes.INVENTORY_RESERVED.equals(envelope.type())) {
                orderApplicationService.transition(
                        envelope.orderId(), OrderStatus.RESERVED, EventTypes.INVENTORY_RESERVED, Map.of());
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
            }
        });
    }

    private void handle(String raw, Acknowledgment ack, java.util.function.Consumer<EventEnvelope> handler) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        handler.accept(envelope);
        ack.acknowledge();
    }
}
