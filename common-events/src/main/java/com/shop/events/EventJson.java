package com.shop.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.UUID;

public final class EventJson {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private EventJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String serialize(EventEnvelope envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }

    public static EventEnvelope deserialize(String json) {
        try {
            return MAPPER.readValue(json, EventEnvelope.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize event", e);
        }
    }

    public static EventEnvelope newEnvelope(String type, UUID orderId, UUID traceId, Object payload) {
        try {
            JsonNode node = MAPPER.valueToTree(payload);
            return new EventEnvelope(
                    UUID.randomUUID(),
                    type,
                    EventEnvelope.CURRENT_VERSION,
                    orderId,
                    Instant.now(),
                    traceId != null ? traceId : UUID.randomUUID(),
                    node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build event", e);
        }
    }
}
