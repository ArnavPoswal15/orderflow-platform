package com.shop.order.messaging;

import com.shop.order.persistence.OutboxEntity;
import com.shop.order.persistence.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long pollIntervalMs;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.poll-interval-ms:200}") long pollIntervalMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.pollIntervalMs = pollIntervalMs;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:200}")
    @Transactional
    public void publishPending() {
        List<OutboxEntity> batch = outboxRepository.lockUnpublished(100);
        for (OutboxEntity row : batch) {
            try {
                kafkaTemplate.send(row.getTopic(), row.getKey(), row.getPayload()).get();
                outboxRepository.markPublished(row.getEventId(), Instant.now());
            } catch (Exception e) {
                log.warn("Outbox publish failed for eventId={}: {}", row.getEventId(), e.getMessage());
            }
        }
    }
}
