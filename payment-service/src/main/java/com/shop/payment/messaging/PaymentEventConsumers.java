package com.shop.payment.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.payment.domain.PaymentApplicationService;
import com.shop.payment.persistence.PaymentRepository;
import com.shop.payment.persistence.ProcessedEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class PaymentEventConsumers {

    private final ProcessedEventRepository processedEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentApplicationService paymentApplicationService;

    public PaymentEventConsumers(
            ProcessedEventRepository processedEventRepository,
            PaymentRepository paymentRepository,
            PaymentApplicationService paymentApplicationService) {
        this.processedEventRepository = processedEventRepository;
        this.paymentRepository = paymentRepository;
        this.paymentApplicationService = paymentApplicationService;
    }

    @KafkaListener(topics = EventTopics.ORDERS, groupId = "payment-service-orders")
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
        if (EventTypes.ORDER_CREATED.equals(type)) {
            OrderCreatedPayload payload = EventJson.mapper().convertValue(envelope.payload(), OrderCreatedPayload.class);
            paymentApplicationService.recordOrderCreated(envelope.orderId(), payload);
        } else {
            paymentApplicationService.handleOrderCancelled(envelope.orderId());
        }
        ack.acknowledge();
    }

    @KafkaListener(topics = EventTopics.INVENTORY, groupId = "payment-service-inventory")
    @Transactional
    public void onInventoryEvent(String raw, Acknowledgment ack) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        if (!EventTypes.INVENTORY_RESERVED.equals(envelope.type())) {
            ack.acknowledge();
            return;
        }
        if (!paymentRepository.existsById(envelope.orderId())) {
            throw new PaymentApplicationService.PaymentNotReadyException(envelope.orderId());
        }
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        paymentApplicationService.chargeOnReserved(envelope.orderId());
        ack.acknowledge();
    }
}
