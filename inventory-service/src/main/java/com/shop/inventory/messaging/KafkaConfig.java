package com.shop.inventory.messaging;

import com.shop.events.EventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

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

    @Bean
    NewTopic ordersDltTopic() {
        return TopicBuilder.name(EventTopics.dlt(EventTopics.ORDERS)).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic inventoryDltTopic() {
        return TopicBuilder.name(EventTopics.dlt(EventTopics.INVENTORY)).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic paymentsDltTopic() {
        return TopicBuilder.name(EventTopics.dlt(EventTopics.PAYMENTS)).partitions(6).replicas(1).build();
    }
}
