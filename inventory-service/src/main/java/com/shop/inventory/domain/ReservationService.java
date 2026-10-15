package com.shop.inventory.domain;

import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.InventoryRejectedPayload;
import com.shop.events.payload.InventoryReservedPayload;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.OrderLineItem;
import com.shop.inventory.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ReservationService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final String poisonOrderIdPrefix;

    public ReservationService(
            InventoryRepository inventoryRepository,
            ReservationRepository reservationRepository,
            OutboxRepository outboxRepository,
            @Value("${inventory.test.poison-order-id-prefix:}") String poisonOrderIdPrefix) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.poisonOrderIdPrefix = poisonOrderIdPrefix;
    }

    @Transactional
    public void handleOrderCreated(UUID orderId, OrderCreatedPayload payload) {
        if (!poisonOrderIdPrefix.isEmpty() && orderId.toString().startsWith(poisonOrderIdPrefix)) {
            throw new IllegalStateException("test poison order " + orderId);
        }
        if (reservationRepository.existsById(orderId)) {
            return;
        }
        List<OrderLineItem> sorted = new ArrayList<>(payload.items());
        sorted.sort(Comparator.comparing(OrderLineItem::sku));

        List<OrderLineItem> decremented = new ArrayList<>();
        for (OrderLineItem line : sorted) {
            int updated = inventoryRepository.decrementStock(line.sku(), line.qty());
            if (updated == 0) {
                for (OrderLineItem prior : decremented) {
                    inventoryRepository.incrementStock(prior.sku(), prior.qty());
                }
                var envelope = EventJson.newEnvelope(
                        EventTypes.INVENTORY_REJECTED,
                        orderId,
                        UUID.randomUUID(),
                        new InventoryRejectedPayload(line.sku(), "out_of_stock"));
                enqueue(envelope);
                return;
            }
            decremented.add(line);
        }

        ReservationEntity reservation = new ReservationEntity();
        reservation.setOrderId(orderId);
        reservation.setStatus("RESERVED");
        List<Map<String, Object>> itemsJson = payload.items().stream()
                .map(i -> Map.<String, Object>of("sku", i.sku(), "qty", i.qty()))
                .toList();
        reservation.setItems(itemsJson);
        reservation.setCreatedAt(Instant.now());
        reservationRepository.save(reservation);

        var envelope = EventJson.newEnvelope(
                EventTypes.INVENTORY_RESERVED,
                orderId,
                UUID.randomUUID(),
                new InventoryReservedPayload(payload.items()));
        enqueue(envelope);
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
