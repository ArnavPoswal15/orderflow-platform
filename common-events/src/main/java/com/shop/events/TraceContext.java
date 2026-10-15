package com.shop.events;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MDC and header names shared by HTTP, outbox publish, and Kafka consume.
 * No Kafka client types: callers copy these strings onto records themselves.
 */
public final class TraceContext {

    public static final String ORDER_ID = "orderId";
    public static final String EVENT_ID = "eventId";
    public static final String TRACE_ID = "traceId";
    public static final String EVENT_TYPE = "eventType";
    public static final String ORDER_STATUS = "orderStatus";
    public static final String FAILURE_REASON = "failureReason";
    public static final String LATENCY_MS = "latencyMs";
    public static final String HTTP_TRACE_HEADER = "X-Trace-Id";

    private TraceContext() {}

    public static void bind(String orderId, String eventId, String traceId) {
        put(ORDER_ID, orderId);
        put(EVENT_ID, eventId);
        put(TRACE_ID, traceId);
    }

    public static void bind(EventEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        bind(
                envelope.orderId() == null ? null : envelope.orderId().toString(),
                envelope.eventId() == null ? null : envelope.eventId().toString(),
                envelope.traceId() == null ? null : envelope.traceId().toString());
        put(EVENT_TYPE, envelope.type());
    }

    public static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    public static void clear() {
        MDC.remove(ORDER_ID);
        MDC.remove(EVENT_ID);
        MDC.remove(TRACE_ID);
        MDC.remove(EVENT_TYPE);
        MDC.remove(ORDER_STATUS);
        MDC.remove(FAILURE_REASON);
        MDC.remove(LATENCY_MS);
    }

    /**
     * Reuse the trace already bound on this thread (HTTP request or consumed record).
     * A non-UUID MDC value is ignored so a bad header cannot stick.
     */
    public static UUID currentOrNew() {
        Optional<UUID> current = parseUuid(MDC.get(TRACE_ID));
        return current.orElseGet(UUID::randomUUID);
    }

    public static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Header map for {@code orderId}, {@code eventId}, and {@code traceId} taken from an
     * envelope JSON payload. Empty when the payload is missing or not an envelope.
     */
    public static Map<String, String> headerMapFromEnvelopeJson(String json) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return headers;
        }
        try {
            JsonNode node = EventJson.mapper().readTree(json);
            copyText(node, ORDER_ID, headers);
            copyText(node, EVENT_ID, headers);
            copyText(node, TRACE_ID, headers);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
        return headers;
    }

    /** Sets {@code failureReason} for inventory reject and payment decline payloads. */
    public static void putFailureReason(EventEnvelope envelope) {
        if (envelope == null || envelope.payload() == null || envelope.type() == null) {
            return;
        }
        if (!EventTypes.INVENTORY_REJECTED.equals(envelope.type())
                && !EventTypes.PAYMENT_FAILED.equals(envelope.type())) {
            return;
        }
        JsonNode reason = envelope.payload().get("reason");
        if (reason != null && reason.isTextual()) {
            put(FAILURE_REASON, reason.asText());
        }
    }

    private static void copyText(JsonNode node, String field, Map<String, String> headers) {
        if (node == null) {
            return;
        }
        JsonNode value = node.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            headers.put(field, value.asText());
        }
    }
}
