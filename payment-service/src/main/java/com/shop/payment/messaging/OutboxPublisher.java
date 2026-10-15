package com.shop.payment.messaging;

import com.shop.events.EventJson;
import com.shop.events.TraceContext;
import com.shop.payment.persistence.OutboxEntity;
import com.shop.payment.persistence.OutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
            TraceContext.clear();
            try {
                Map<String, String> headers = TraceContext.headerMapFromEnvelopeJson(row.getPayload());
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(row.getTopic(), row.getKey(), row.getPayload());
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    record.headers().add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
                }
                try {
                    var envelope = EventJson.deserialize(row.getPayload());
                    TraceContext.bind(envelope);
                    TraceContext.putFailureReason(envelope);
                } catch (RuntimeException ex) {
                    TraceContext.bind(
                            headers.get(TraceContext.ORDER_ID),
                            headers.get(TraceContext.EVENT_ID),
                            headers.get(TraceContext.TRACE_ID));
                }
                kafkaTemplate.send(record).get(30, TimeUnit.SECONDS);
                transactionTemplate.executeWithoutResult(
                        status -> outboxRepository.markPublished(row.getEventId(), Instant.now()));
                log.info("published");
            } catch (Exception e) {
                log.warn("Outbox publish failed for eventId={}: {}", row.getEventId(), e.getMessage());
            } finally {
                TraceContext.clear();
            }
        }
    }
}
