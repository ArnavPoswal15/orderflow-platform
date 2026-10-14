package com.shop.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope(
        UUID eventId,
        String type,
        int version,
        UUID orderId,
        Instant occurredAt,
        UUID traceId,
        JsonNode payload
) {
    public static final int CURRENT_VERSION = 1;
}
