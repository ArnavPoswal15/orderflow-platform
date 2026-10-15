package com.shop.inventory.domain;

import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.InventoryReleasedPayload;
import com.shop.events.payload.OrderLineItem;
import com.shop.inventory.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ReleaseService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final OutboxRepository outboxRepository;

    public ReleaseService(
            ReservationRepository reservationRepository,
            InventoryRepository inventoryRepository,
            OutboxRepository outboxRepository) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void releaseForOrder(UUID orderId) {
        Optional<ReservationEntity> reservation = reservationRepository.findByOrderIdAndStatus(orderId, "RESERVED");
        if (reservation.isEmpty()) {
            return;
        }
        ReservationEntity row = reservation.get();
        row.setStatus("RELEASED");
        reservationRepository.save(row);

        List<OrderLineItem> items = parseItems(row.getItems());
        for (OrderLineItem line : items) {
            inventoryRepository.incrementStock(line.sku(), line.qty());
        }

        var envelope = EventJson.newEnvelope(
                EventTypes.INVENTORY_RELEASED,
                orderId,
                UUID.randomUUID(),
                new InventoryReleasedPayload(items));
        enqueue(envelope);
    }

    private List<OrderLineItem> parseItems(List<Map<String, Object>> itemsJson) {
        List<OrderLineItem> items = new ArrayList<>();
        for (Map<String, Object> m : itemsJson) {
            String sku = m.get("sku").toString();
            int qty = ((Number) m.get("qty")).intValue();
            items.add(new OrderLineItem(sku, qty));
        }
        return items;
    }

    private void enqueue(com.shop.events.EventEnvelope envelope) {
        OutboxEntity row = new OutboxEntity();
        row.setEventId(envelope.eventId());
        row.setTopic(EventTopics.INVENTORY);
        row.setKey(envelope.orderId().toString());
        row.setPayload(EventJson.serialize(envelope));
        row.setCreatedAt(Instant.now());
        outboxRepository.save(row);
    }
}
