package com.shop.inventory.messaging;

import com.shop.events.TraceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class TraceRecordInterceptor implements RecordInterceptor<String, String> {

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        TraceContext.clear();
        String orderId = header(record, TraceContext.ORDER_ID);
        String eventId = header(record, TraceContext.EVENT_ID);
        String traceId = header(record, TraceContext.TRACE_ID);
        if (orderId == null || eventId == null || traceId == null) {
            Map<String, String> fromJson = TraceContext.headerMapFromEnvelopeJson(record.value());
            if (orderId == null) {
                orderId = fromJson.get(TraceContext.ORDER_ID);
            }
            if (eventId == null) {
                eventId = fromJson.get(TraceContext.EVENT_ID);
            }
            if (traceId == null) {
                traceId = fromJson.get(TraceContext.TRACE_ID);
            }
        }
        TraceContext.bind(orderId, eventId, traceId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        TraceContext.clear();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null || header.value().length == 0) {
            return null;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return value.isBlank() ? null : value;
    }
}
