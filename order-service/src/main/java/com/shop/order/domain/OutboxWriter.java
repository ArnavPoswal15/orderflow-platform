package com.shop.order.domain;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.order.persistence.OutboxEntity;
import com.shop.order.persistence.OutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;

    public OutboxWriter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public void enqueue(EventEnvelope envelope, String topic) {
        OutboxEntity row = new OutboxEntity();
        row.setEventId(envelope.eventId());
        row.setTopic(topic);
        row.setKey(envelope.orderId().toString());
        row.setPayload(EventJson.serialize(envelope));
        row.setCreatedAt(Instant.now());
        outboxRepository.save(row);
    }
}
