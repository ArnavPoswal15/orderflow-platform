package com.shop.order.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.shop.events.EventTopics;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name(EventTopics.ORDERS).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic inventoryTopic() {
        return TopicBuilder.name(EventTopics.INVENTORY).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic paymentsTopic() {
        return TopicBuilder.name(EventTopics.PAYMENTS).partitions(6).replicas(1).build();
    }
}
