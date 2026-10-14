package com.shop.inventory.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.inventory.domain.ReservationService;
import com.shop.inventory.persistence.ProcessedEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OrderEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final ReservationService reservationService;

    public OrderEventConsumer(
            ProcessedEventRepository processedEventRepository,
            ReservationService reservationService) {
        this.processedEventRepository = processedEventRepository;
        this.reservationService = reservationService;
    }

    @KafkaListener(topics = EventTopics.ORDERS, groupId = "inventory-service")
    @Transactional
    public void onOrderEvent(String raw, Acknowledgment ack) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        if (!EventTypes.ORDER_CREATED.equals(envelope.type())) {
            ack.acknowledge();
            return;
        }
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        OrderCreatedPayload payload = EventJson.mapper().convertValue(envelope.payload(), OrderCreatedPayload.class);
        reservationService.handleOrderCreated(envelope.orderId(), payload);
        ack.acknowledge();
    }
}
