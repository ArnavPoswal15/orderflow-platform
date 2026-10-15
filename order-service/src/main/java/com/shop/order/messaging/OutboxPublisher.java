package com.shop.order.messaging;

import com.shop.order.persistence.OutboxEntity;
import com.shop.order.persistence.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:200}")
    public void publishPending() {
        List<OutboxEntity> batch =
                transactionTemplate.execute(status -> outboxRepository.lockUnpublished(100));
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (OutboxEntity row : batch) {
            try {
                kafkaTemplate
                        .send(row.getTopic(), row.getKey(), row.getPayload())
                        .get(30, TimeUnit.SECONDS);
                transactionTemplate.executeWithoutResult(
                        status -> outboxRepository.markPublished(row.getEventId(), Instant.now()));
            } catch (Exception e) {
                log.warn("Outbox publish failed for eventId={}: {}", row.getEventId(), e.getMessage());
            }
        }
    }
}
