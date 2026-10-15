package com.shop.order.messaging;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shop.kafka.listeners.enabled", havingValue = "true", matchIfMissing = true)
public class SseFeedKafkaConsumer {

    private final SseHub sseHub;

    public SseFeedKafkaConsumer(SseHub sseHub) {
        this.sseHub = sseHub;
    }

    @KafkaListener(
            topics = {EventTopics.ORDERS, EventTopics.INVENTORY, EventTopics.PAYMENTS},
            groupId = "${shop.sse.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onEvent(String raw, Acknowledgment ack) {
        EventEnvelope envelope = EventJson.deserialize(raw);
        sseHub.send(envelope);
        ack.acknowledge();
    }
}
