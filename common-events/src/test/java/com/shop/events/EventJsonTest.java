package com.shop.events;

import com.shop.events.payload.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventJsonTest {

    @Test
    void roundTripOrderCreated() {
        UUID orderId = UUID.randomUUID();
        var payload = new OrderCreatedPayload(List.of(new OrderLineItem("MILK", 1)), 399, "tok");
        EventEnvelope envelope = EventJson.newEnvelope(EventTypes.ORDER_CREATED, orderId, UUID.randomUUID(), payload);
        String json = EventJson.serialize(envelope);
        EventEnvelope back = EventJson.deserialize(json);
        assertEquals(envelope.type(), back.type());
        assertEquals(orderId, back.orderId());
    }

    @Test
    void roundTripInventoryRejected() {
        UUID orderId = UUID.randomUUID();
        var payload = new InventoryRejectedPayload("LASTITEM", "out_of_stock");
        EventEnvelope envelope =
                EventJson.newEnvelope(EventTypes.INVENTORY_REJECTED, orderId, UUID.randomUUID(), payload);
        EventEnvelope back = EventJson.deserialize(EventJson.serialize(envelope));
        InventoryRejectedPayload p = EventJson.mapper().convertValue(back.payload(), InventoryRejectedPayload.class);
        assertEquals("LASTITEM", p.sku());
        assertEquals("out_of_stock", p.reason());
    }

    @Test
    void roundTripInventoryReleased() {
        UUID orderId = UUID.randomUUID();
        var payload = new InventoryReleasedPayload(List.of(new OrderLineItem("MILK", 2)));
        EventEnvelope envelope =
                EventJson.newEnvelope(EventTypes.INVENTORY_RELEASED, orderId, UUID.randomUUID(), payload);
        EventEnvelope back = EventJson.deserialize(EventJson.serialize(envelope));
        InventoryReleasedPayload p = EventJson.mapper().convertValue(back.payload(), InventoryReleasedPayload.class);
        assertEquals(1, p.items().size());
        assertEquals("MILK", p.items().get(0).sku());
    }

    @Test
    void roundTripPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        var payload = new PaymentFailedPayload("declined");
        EventEnvelope envelope = EventJson.newEnvelope(EventTypes.PAYMENT_FAILED, orderId, UUID.randomUUID(), payload);
        EventEnvelope back = EventJson.deserialize(EventJson.serialize(envelope));
        PaymentFailedPayload p = EventJson.mapper().convertValue(back.payload(), PaymentFailedPayload.class);
        assertEquals("declined", p.reason());
    }

    @Test
    void roundTripPaymentRefunded() {
        UUID orderId = UUID.randomUUID();
        var payload = new PaymentRefundedPayload(1999L);
        EventEnvelope envelope = EventJson.newEnvelope(EventTypes.PAYMENT_REFUNDED, orderId, UUID.randomUUID(), payload);
        EventEnvelope back = EventJson.deserialize(EventJson.serialize(envelope));
        PaymentRefundedPayload p = EventJson.mapper().convertValue(back.payload(), PaymentRefundedPayload.class);
        assertEquals(1999L, p.amountCents());
    }

    @Test
    void roundTripOrderCancelled() {
        UUID orderId = UUID.randomUUID();
        var payload = new OrderCancelledPayload("customer_requested", true);
        EventEnvelope envelope = EventJson.newEnvelope(EventTypes.ORDER_CANCELLED, orderId, UUID.randomUUID(), payload);
        EventEnvelope back = EventJson.deserialize(EventJson.serialize(envelope));
        OrderCancelledPayload p = EventJson.mapper().convertValue(back.payload(), OrderCancelledPayload.class);
        assertEquals("customer_requested", p.reason());
        assertEquals(true, p.paidBeforeCancel());
    }

    @Test
    void dltTopicName() {
        assertEquals("orders.events.DLT", EventTopics.dlt(EventTopics.ORDERS));
    }
}
