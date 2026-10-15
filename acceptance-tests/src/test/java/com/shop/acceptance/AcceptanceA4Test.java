package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA4Test extends AcceptanceTestBase {

    @Test
    void A4_duplicateKafkaEvent_idempotent() throws Exception {
        JsonNode order = client.createOrder("MILK", 1, "ok", "a4-" + UUID.randomUUID());
        UUID orderId = UUID.fromString(order.get("id").asText());
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CONFIRMED".equals(client.getOrder(orderId).get("status").asText()));

        int stockAfter = JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "MILK");
        String payStatus = JdbcTestSupport.paymentStatus(harness.paymentsJdbcUrl, orderId);

        EventEnvelope reserved = findInventoryReservedEvent(orderId);
        publishToKafka(EventTopics.INVENTORY, reserved.orderId().toString(), EventJson.serialize(reserved));

        Thread.sleep(3000);
        assertEquals("CONFIRMED", client.getOrder(orderId).get("status").asText());
        assertEquals(stockAfter, JdbcTestSupport.availableStock(harness.inventoryJdbcUrl, "MILK"));
        assertEquals(payStatus, JdbcTestSupport.paymentStatus(harness.paymentsJdbcUrl, orderId));
    }

    private EventEnvelope findInventoryReservedEvent(UUID orderId) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, harness.kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-find-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(EventTopics.INVENTORY));
            long deadline = System.currentTimeMillis() + 60_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    EventEnvelope env = EventJson.deserialize(record.value());
                    if (orderId.equals(env.orderId()) && EventTypes.INVENTORY_RESERVED.equals(env.type())) {
                        return env;
                    }
                }
            }
        }
        throw new IllegalStateException("inventory.reserved not found for " + orderId);
    }

    private void publishToKafka(String topic, String key, String value) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, harness.kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
