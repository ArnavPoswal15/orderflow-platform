package com.shop.events;

import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.OrderLineItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventJsonTest {

    @Test
    void roundTripEnvelope() {
        UUID orderId = UUID.randomUUID();
        var payload = new OrderCreatedPayload(List.of(new OrderLineItem("MILK", 1)), 399, "tok");
        EventEnvelope envelope = EventJson.newEnvelope(EventTypes.ORDER_CREATED, orderId, UUID.randomUUID(), payload);
        String json = EventJson.serialize(envelope);
        EventEnvelope back = EventJson.deserialize(json);
        assertEquals(envelope.type(), back.type());
        assertEquals(orderId, back.orderId());
    }
}
