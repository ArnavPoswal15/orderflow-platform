package com.shop.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.OrderLineItem;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class AcceptanceA6Test {

    private static PlatformTestHarness harness;
    private static HttpTestClient client;

    @BeforeAll
    static void startPlatform() throws Exception {
        harness = new PlatformTestHarness();
        harness.startAll(Map.of(), Map.of("inventory.test.poison-order-id-prefix", "00000000-0000-4000-8000"));
        client = new HttpTestClient("http://localhost:" + harness.orderPort);
    }

    @AfterAll
    static void stopPlatform() {
        if (harness != null) {
            harness.stopAll();
        }
    }

    @Test
    void A6_poisonMessage_goesToDlt() throws Exception {
        UUID poisonOrderId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        EventEnvelope poison = EventJson.newEnvelope(
                EventTypes.ORDER_CREATED,
                poisonOrderId,
                UUID.randomUUID(),
                new OrderCreatedPayload(List.of(new OrderLineItem("MILK", 1)), 399, "ok"));
        publishToKafka(EventTopics.ORDERS, poisonOrderId.toString(), EventJson.serialize(poison));

        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() -> hasDltMessage(EventTopics.dlt(EventTopics.ORDERS)));

        JsonNode good = client.createOrder("MILK", 1, "ok", "a6-good-" + UUID.randomUUID());
        UUID goodId = UUID.fromString(good.get("id").asText());
        Awaitility.await().atMost(Duration.ofSeconds(120)).until(() ->
                "CONFIRMED".equals(client.getOrder(goodId).get("status").asText()));
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

    private boolean hasDltMessage(String dltTopic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, harness.kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(dltTopic));
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
