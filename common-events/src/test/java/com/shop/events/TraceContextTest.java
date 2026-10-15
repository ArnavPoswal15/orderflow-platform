package com.shop.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextTest {

    @AfterEach
    void clearMdc() {
        TraceContext.clear();
    }

    @Test
    void bindWritesMdcAndClearRemovesIt() {
        TraceContext.bind("order-1", "event-1", "trace-1");
        assertEquals("order-1", MDC.get(TraceContext.ORDER_ID));
        assertEquals("event-1", MDC.get(TraceContext.EVENT_ID));
        assertEquals("trace-1", MDC.get(TraceContext.TRACE_ID));

        TraceContext.clear();
        assertNull(MDC.get(TraceContext.ORDER_ID));
        assertNull(MDC.get(TraceContext.EVENT_ID));
        assertNull(MDC.get(TraceContext.TRACE_ID));
    }

    @Test
    void headerMapFromEnvelopeJson() {
        UUID orderId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        EventEnvelope envelope = EventJson.newEnvelope(
                EventTypes.ORDER_CREATED, orderId, traceId, Map.of("sku", "MILK"));
        Map<String, String> headers = TraceContext.headerMapFromEnvelopeJson(EventJson.serialize(envelope));

        assertEquals(orderId.toString(), headers.get(TraceContext.ORDER_ID));
        assertEquals(envelope.eventId().toString(), headers.get(TraceContext.EVENT_ID));
        assertEquals(traceId.toString(), headers.get(TraceContext.TRACE_ID));
        assertTrue(TraceContext.headerMapFromEnvelopeJson("not-json").isEmpty());
        assertTrue(TraceContext.headerMapFromEnvelopeJson(null).isEmpty());
    }

    @Test
    void currentOrNewReusesBoundTrace() {
        UUID traceId = UUID.randomUUID();
        TraceContext.bind(null, null, traceId.toString());

        assertEquals(traceId, TraceContext.currentOrNew());
        assertEquals(traceId, TraceContext.currentOrNew());

        TraceContext.clear();
        UUID minted = TraceContext.currentOrNew();
        assertNotEquals(traceId, minted);

        TraceContext.bind(null, null, "not-a-uuid");
        UUID replacement = TraceContext.currentOrNew();
        assertNotEquals(traceId, replacement);
        UUID.fromString(replacement.toString());
    }
}
