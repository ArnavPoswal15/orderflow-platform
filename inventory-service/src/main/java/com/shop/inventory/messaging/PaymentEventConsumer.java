package com.shop.inventory.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.TraceContext;
import com.shop.inventory.domain.ReleaseService;
import com.shop.inventory.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final ReleaseService releaseService;

    public PaymentEventConsumer(
            ProcessedEventRepository processedEventRepository,
            ReleaseService releaseService) {
        this.processedEventRepository = processedEventRepository;
        this.releaseService = releaseService;
    }

    @KafkaListener(topics = EventTopics.PAYMENTS, groupId = "inventory-service-payments")
    @Transactional
    public void onPaymentEvent(String raw, Acknowledgment ack) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        if (!EventTypes.PAYMENT_FAILED.equals(envelope.type())) {
            ack.acknowledge();
            return;
        }
        if (processedEventRepository.tryInsert(envelope.eventId(), Instant.now()) == 0) {
            ack.acknowledge();
            return;
        }
        TraceContext.bind(envelope);
        log.info("consumed");
        releaseService.releaseForOrder(envelope.orderId());
        ack.acknowledge();
    }
}
