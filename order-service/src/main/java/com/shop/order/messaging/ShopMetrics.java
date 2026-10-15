package com.shop.order.messaging;

import com.shop.events.EventTopics;
import com.shop.order.persistence.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ShopMetrics {

    private static final Logger log = LoggerFactory.getLogger(ShopMetrics.class);

    private final OutboxRepository outboxRepository;
    private final String bootstrapServers;
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final Map<GroupTopic, AtomicLong> consumerLag = new LinkedHashMap<>();
    private final Map<String, AtomicLong> dltMessages = new LinkedHashMap<>();

    public ShopMetrics(
            MeterRegistry meterRegistry,
            OutboxRepository outboxRepository,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${shop.sse.consumer-group}") String sseGroup) {
        this.outboxRepository = outboxRepository;
        this.bootstrapServers = bootstrapServers;

        Gauge.builder("shop.outbox.backlog", outboxBacklog, AtomicLong::doubleValue)
                .description("Unpublished outbox rows")
                .register(meterRegistry);

        List<GroupTopic> subscriptions = List.of(
                new GroupTopic("order-service-inventory", EventTopics.INVENTORY),
                new GroupTopic("order-service-payments", EventTopics.PAYMENTS),
                new GroupTopic(sseGroup, EventTopics.ORDERS),
                new GroupTopic(sseGroup, EventTopics.INVENTORY),
                new GroupTopic(sseGroup, EventTopics.PAYMENTS));
        for (GroupTopic subscription : subscriptions) {
            AtomicLong value = new AtomicLong();
            consumerLag.put(subscription, value);
            Gauge.builder("shop.consumer.lag", value, AtomicLong::doubleValue)
                    .description("End offset minus committed offset")
                    .tag("group", subscription.group())
                    .tag("topic", subscription.topic())
                    .register(meterRegistry);
        }

        List<String> dltTopics = List.of(
                EventTopics.dlt(EventTopics.ORDERS),
                EventTopics.dlt(EventTopics.INVENTORY),
                EventTopics.dlt(EventTopics.PAYMENTS));
        for (String topic : dltTopics) {
            AtomicLong value = new AtomicLong();
            dltMessages.put(topic, value);
            Gauge.builder("shop.dlt.messages", value, AtomicLong::doubleValue)
                    .description("Dead-letter topic end-offset sum")
                    .tag("topic", topic)
                    .register(meterRegistry);
        }

        refreshOutbox();
    }

    @Scheduled(
            initialDelayString = "${shop.metrics.refresh-ms:15000}",
            fixedDelayString = "${shop.metrics.refresh-ms:15000}")
    public void refreshOutbox() {
        try {
            outboxBacklog.set(outboxRepository.countByPublishedAtIsNull());
        } catch (RuntimeException ex) {
            log.warn("outbox backlog refresh failed: {}", ex.getMessage());
        }
    }

    /**
     * Kafka admin is not called from the constructor. Context tests that exclude
     * KafkaAutoConfiguration finish before this initial delay.
     */
    @Scheduled(
            initialDelayString = "${shop.metrics.initial-delay-ms:120000}",
            fixedDelayString = "${shop.metrics.refresh-ms:15000}")
    public void refreshKafka() {
        AdminClient admin;
        try {
            admin = AdminClient.create(adminConfig());
        } catch (RuntimeException ex) {
            log.debug("kafka admin client not created: {}", ex.toString());
            return;
        }
        try {
            for (Map.Entry<GroupTopic, AtomicLong> entry : consumerLag.entrySet()) {
                try {
                    entry.getValue().set(consumerLag(admin, entry.getKey().group(), entry.getKey().topic()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.debug(
                            "consumer lag unavailable group={} topic={}: {}",
                            entry.getKey().group(),
                            entry.getKey().topic(),
                            ex.toString());
                }
            }
            for (Map.Entry<String, AtomicLong> entry : dltMessages.entrySet()) {
                try {
                    entry.getValue().set(topicEndOffsetSum(admin, entry.getKey()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.debug("dlt count unavailable topic={}: {}", entry.getKey(), ex.toString());
                }
            }
        } finally {
            try {
                admin.close(Duration.ofSeconds(1));
            } catch (RuntimeException ignored) {
                // leave the last gauge values
            }
        }
    }

    private Map<String, Object> adminConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 5000);
        return config;
    }

    private static long consumerLag(AdminClient admin, String group, String topic) throws Exception {
        Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group)
                .partitionsToOffsetAndMetadata()
                .get(5, TimeUnit.SECONDS);
        TopicDescription description =
                admin.describeTopics(List.of(topic)).allTopicNames().get(5, TimeUnit.SECONDS).get(topic);
        if (description == null || description.partitions().isEmpty()) {
            return 0L;
        }
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        for (var partition : description.partitions()) {
            request.put(new TopicPartition(topic, partition.partition()), OffsetSpec.latest());
        }
        ListOffsetsResult ends = admin.listOffsets(request);
        long lag = 0L;
        for (TopicPartition tp : request.keySet()) {
            long end = ends.partitionResult(tp).get(5, TimeUnit.SECONDS).offset();
            OffsetAndMetadata meta = committed.get(tp);
            long current = meta == null ? 0L : meta.offset();
            lag += Math.max(0L, end - current);
        }
        return lag;
    }

    private static long topicEndOffsetSum(AdminClient admin, String topic) throws Exception {
        TopicDescription description =
                admin.describeTopics(List.of(topic)).allTopicNames().get(5, TimeUnit.SECONDS).get(topic);
        if (description == null || description.partitions().isEmpty()) {
            return 0L;
        }
        Map<TopicPartition, OffsetSpec> request = new HashMap<>();
        for (var partition : description.partitions()) {
            request.put(new TopicPartition(topic, partition.partition()), OffsetSpec.latest());
        }
        ListOffsetsResult offsets = admin.listOffsets(request);
        long sum = 0L;
        for (TopicPartition tp : request.keySet()) {
            sum += offsets.partitionResult(tp).get(5, TimeUnit.SECONDS).offset();
        }
        return sum;
    }

    private record GroupTopic(String group, String topic) {}
}
