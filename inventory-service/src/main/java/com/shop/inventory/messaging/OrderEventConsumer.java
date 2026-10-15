package com.shop.inventory.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.TraceContext;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.inventory.domain.ReleaseService;
import com.shop.inventory.domain.ReservationService;
import com.shop.inventory.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ReservationService reservationService;
    private final ReleaseService releaseService;

    public OrderEventConsumer(
            ProcessedEventRepository processedEventRepository,
            ReservationService reservationService,
            ReleaseService releaseService) {
        this.processedEventRepository = processedEventRepository;
        this.reservationService = reservationService;
        this.releaseService = releaseService;
    }

    @KafkaListener(topics = EventTopics.ORDERS, groupId = "inventory-service")
    @Transactional
    public void onOrderEvent(String raw, Acknowledgment ack) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        String type = envelope.type();
        if (!EventTypes.ORDER_CREATED.equals(type) && !EventTypes.ORDER_CANCELLED.equals(type)) {
            ack.acknowledge();
            return;
        }
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        TraceContext.bind(envelope);
        log.info("consumed");
        if (EventTypes.ORDER_CREATED.equals(type)) {
            OrderCreatedPayload payload = EventJson.mapper().convertValue(envelope.payload(), OrderCreatedPayload.class);
            reservationService.handleOrderCreated(envelope.orderId(), payload);
        } else {
            releaseService.releaseForOrder(envelope.orderId());
        }
        ack.acknowledge();
    }
}
