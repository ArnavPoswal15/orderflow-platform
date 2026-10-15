package com.shop.order.domain;

import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.OrderLineItem;
import com.shop.order.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderCreator {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final IdempotencyService idempotencyService;
    private final InventoryClient inventoryClient;
    private final OutboxWriter outboxWriter;

    public OrderCreator(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            IdempotencyService idempotencyService,
            InventoryClient inventoryClient,
            OutboxWriter outboxWriter) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.idempotencyService = idempotencyService;
        this.inventoryClient = inventoryClient;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderEntity createInNewTransaction(
            String idempotencyKey, String requestHash, List<OrderApplicationService.LineRequest> lines, String paymentToken) {
        return create(idempotencyKey, requestHash, lines, paymentToken);
    }

    @Transactional
    public OrderEntity create(String idempotencyKey, String requestHash, List<OrderApplicationService.LineRequest> lines, String paymentToken) {
        List<String> skus = lines.stream().map(OrderApplicationService.LineRequest::sku).distinct().toList();
        Map<String, InventoryClient.SkuInfo> catalog = inventoryClient.fetchSkus(skus);
        if (catalog.size() != skus.size()) {
            throw new OrderApplicationService.BadRequestException("unknown sku");
        }
        long total = 0;
        for (OrderApplicationService.LineRequest line : lines) {
            total += catalog.get(line.sku()).priceCents() * line.qty();
        }

        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestHash(requestHash);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalCents(total);
        order.setPaymentToken(paymentToken);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        for (OrderApplicationService.LineRequest line : lines) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(orderId);
            item.setSku(line.sku());
            item.setQty(line.qty());
            item.setUnitPriceCents(catalog.get(line.sku()).priceCents());
            item.setOrder(order);
            order.getItems().add(item);
        }

        orderRepository.save(order);

        appendHistory(orderId, "order.created", Map.of("status", OrderStatus.PENDING, "totalCents", total));

        List<OrderLineItem> payloadItems = lines.stream().map(l -> new OrderLineItem(l.sku(), l.qty())).toList();
        var envelope = EventJson.newEnvelope(
                EventTypes.ORDER_CREATED,
                orderId,
                UUID.randomUUID(),
                new OrderCreatedPayload(payloadItems, total, paymentToken));
        outboxWriter.enqueue(envelope, EventTopics.ORDERS);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                idempotencyService.cacheAfterCommit(idempotencyKey, orderId.toString());
            }
        });

        return order;
    }

    private void appendHistory(UUID orderId, String type, Map<String, Object> data) {
        OrderEventEntity ev = new OrderEventEntity();
        ev.setOrderId(orderId);
        ev.setType(type);
        ev.setData(data);
        ev.setAt(Instant.now());
        orderEventRepository.save(ev);
    }
}
