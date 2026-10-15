package com.shop.order.domain;

import com.shop.events.EventTopics;
import com.shop.order.persistence.OrderRepository;
import com.shop.order.persistence.OutboxRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class AdminStatsService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final String bootstrapServers;

    public AdminStatsService(
            OrderRepository orderRepository,
            OutboxRepository outboxRepository,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.bootstrapServers = bootstrapServers;
    }

    public AdminStatsView stats() throws ExecutionException, InterruptedException {
        Map<String, Long> ordersByStatus = new HashMap<>();
        for (Object[] row : orderRepository.countGroupedByStatus()) {
            ordersByStatus.put((String) row[0], (Long) row[1]);
        }
        long outboxBacklog = outboxRepository.countByPublishedAtIsNull();
        Map<String, Long> dltCounts = dltMessageCounts();
        return new AdminStatsView(ordersByStatus, outboxBacklog, dltCounts);
    }

    private Map<String, Long> dltMessageCounts() throws ExecutionException, InterruptedException {
        Map<String, Long> counts = new HashMap<>();
        Map<String, Object> config =
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(config)) {
            counts.put(EventTopics.dlt(EventTopics.ORDERS), topicEndOffsetSum(admin, EventTopics.dlt(EventTopics.ORDERS)));
            counts.put(
                    EventTopics.dlt(EventTopics.INVENTORY),
                    topicEndOffsetSum(admin, EventTopics.dlt(EventTopics.INVENTORY)));
            counts.put(
                    EventTopics.dlt(EventTopics.PAYMENTS),
                    topicEndOffsetSum(admin, EventTopics.dlt(EventTopics.PAYMENTS)));
        }
        return counts;
    }

    private static long topicEndOffsetSum(AdminClient admin, String topic)
            throws ExecutionException, InterruptedException {
        TopicDescription description = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
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
            Long offset = offsets.partitionResult(tp).get().offset();
            if (offset != null) {
                sum += offset;
            }
        }
        return sum;
    }

    public record AdminStatsView(
            Map<String, Long> ordersByStatus, long outboxBacklog, Map<String, Long> dltCounts) {}
}
