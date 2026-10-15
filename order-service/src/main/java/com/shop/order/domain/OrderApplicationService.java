package com.shop.order.domain;

import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.TraceContext;
import com.shop.events.payload.OrderCancelledPayload;
import com.shop.order.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final IdempotencyService idempotencyService;
    private final OrderCreator orderCreator;
    private final OutboxWriter outboxWriter;
    private final OrderMetrics orderMetrics;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            IdempotencyService idempotencyService,
            OrderCreator orderCreator,
            OutboxWriter outboxWriter,
            OrderMetrics orderMetrics) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.idempotencyService = idempotencyService;
        this.orderCreator = orderCreator;
        this.outboxWriter = outboxWriter;
        this.orderMetrics = orderMetrics;
    }

    @Transactional(readOnly = true)
    public Optional<OrderView> getOrder(UUID id) {
        return orderRepository.findById(id).map(this::toView);
    }

    @Transactional(readOnly = true)
    public OrderPageView listOrders(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderEntity> result;
        if (status != null && !status.isBlank()) {
            result = orderRepository.findByStatus(status, pageable);
        } else {
            result = orderRepository.findAll(pageable);
        }
        List<OrderView> content = result.getContent().stream().map(this::toSummaryView).toList();
        return new OrderPageView(content, page, size, result.getTotalElements());
    }

    public CreateOrderResult createOrder(String idempotencyKey, String requestHash, List<LineRequest> lines, String paymentToken) {
        String cached = idempotencyService.getCachedOrderId(idempotencyKey);
        if (cached != null) {
            return orderRepository
                    .findByIdempotencyKeyWithItems(idempotencyKey)
                    .map(o -> new CreateOrderResult(toSummaryView(o), true, false))
                    .orElseGet(() -> createNew(idempotencyKey, requestHash, lines, paymentToken));
        }
        return createNew(idempotencyKey, requestHash, lines, paymentToken);
    }

    private CreateOrderResult createNew(String idempotencyKey, String requestHash, List<LineRequest> lines, String paymentToken) {
        try {
            OrderEntity created = orderCreator.createInNewTransaction(idempotencyKey, requestHash, lines, paymentToken);
            return new CreateOrderResult(toSummaryView(created), false, false);
        } catch (DataIntegrityViolationException dup) {
            Optional<OrderEntity> existing = awaitExistingByIdempotencyKey(idempotencyKey);
            if (existing.isEmpty()) {
                throw dup;
            }
            OrderEntity order = existing.get();
            if (!order.getRequestHash().equals(requestHash)) {
                return new CreateOrderResult(null, false, true);
            }
            idempotencyService.cacheAfterCommit(idempotencyKey, order.getId().toString());
            return new CreateOrderResult(toSummaryView(order), true, false);
        }
    }

    private Optional<OrderEntity> awaitExistingByIdempotencyKey(String idempotencyKey) {
        for (int attempt = 0; attempt < 20; attempt++) {
            Optional<OrderEntity> existing = orderRepository.findByIdempotencyKeyWithItems(idempotencyKey);
            if (existing.isPresent()) {
                return existing;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return orderRepository.findByIdempotencyKeyWithItems(idempotencyKey);
    }

    @Transactional
    public OrderView cancelOrder(UUID orderId, String reason) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));
        String from = order.getStatus();
        OrderStateMachine.assertTransition(from, OrderStatus.CANCELLED);
        applyCancelled(order, from, reason != null ? reason : "customer_requested", EventTypes.ORDER_CANCELLED);
        return toView(order);
    }

    @Transactional
    public OrderView shipOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));
        String from = order.getStatus();
        OrderStateMachine.assertTransition(from, OrderStatus.SHIPPED);
        order.setStatus(OrderStatus.SHIPPED);
        order.setUpdatedAt(Instant.now());
        appendHistory(orderId, EventTypes.ORDER_SHIPPED, Map.of("from", from, "to", OrderStatus.SHIPPED));
        var envelope = EventJson.newEnvelope(EventTypes.ORDER_SHIPPED, orderId, TraceContext.currentOrNew(), Map.of());
        outboxWriter.enqueue(envelope, EventTopics.ORDERS);
        TraceContext.bind(envelope);
        logOrderStatus(orderId, OrderStatus.SHIPPED, null);
        return toView(order);
    }

    @Transactional
    public OrderView deliverOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(orderId));
        String from = order.getStatus();
        OrderStateMachine.assertTransition(from, OrderStatus.DELIVERED);
        order.setStatus(OrderStatus.DELIVERED);
        order.setUpdatedAt(Instant.now());
        appendHistory(orderId, EventTypes.ORDER_DELIVERED, Map.of("from", from, "to", OrderStatus.DELIVERED));
        var envelope = EventJson.newEnvelope(EventTypes.ORDER_DELIVERED, orderId, TraceContext.currentOrNew(), Map.of());
        outboxWriter.enqueue(envelope, EventTopics.ORDERS);
        TraceContext.bind(envelope);
        logOrderStatus(orderId, OrderStatus.DELIVERED, null);
        return toView(order);
    }

    @Transactional
    public void transition(UUID orderId, String toStatus, String eventType, Map<String, Object> data) {
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for event {}", orderId, eventType);
            return;
        }
        String from = order.getStatus();
        if (from.equals(toStatus)) {
            return;
        }
        if (!OrderStateMachine.canTransition(from, toStatus)) {
            log.warn("Ignoring illegal transition for order {} from {} to {} on {}", orderId, from, toStatus, eventType);
            return;
        }
        if (OrderStatus.CANCELLED.equals(toStatus)) {
            String reason = data != null && data.get("reason") != null ? data.get("reason").toString() : eventType;
            applyCancelled(order, from, reason, eventType);
            return;
        }
        order.setStatus(toStatus);
        order.setUpdatedAt(Instant.now());
        Map<String, Object> hist = new HashMap<>(data != null ? data : Map.of());
        hist.put("from", from);
        hist.put("to", toStatus);
        appendHistory(orderId, eventType, hist);
        Long latencyMs = null;
        if (EventTypes.PAYMENT_SUCCEEDED.equals(eventType)) {
            latencyMs = latencySinceCreated(order.getCreatedAt());
        }
        TraceContext.put(TraceContext.EVENT_TYPE, eventType);
        logOrderStatus(orderId, toStatus, latencyMs);
    }

    private void applyCancelled(OrderEntity order, String from, String reason, String eventType) {
        UUID orderId = order.getId();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setUpdatedAt(Instant.now());
        boolean paidBeforeCancel = OrderStatus.CONFIRMED.equals(from);
        Map<String, Object> hist = Map.of("from", from, "to", OrderStatus.CANCELLED, "reason", reason);
        appendHistory(orderId, eventType, hist);
        var envelope = EventJson.newEnvelope(
                EventTypes.ORDER_CANCELLED,
                orderId,
                TraceContext.currentOrNew(),
                new OrderCancelledPayload(reason, paidBeforeCancel));
        outboxWriter.enqueue(envelope, EventTopics.ORDERS);
        orderMetrics.orderCancelled();
        TraceContext.bind(envelope);
        logOrderStatus(orderId, OrderStatus.CANCELLED, null);
    }

    private void logOrderStatus(UUID orderId, String status, Long latencyMs) {
        TraceContext.put(TraceContext.ORDER_ID, orderId.toString());
        TraceContext.put(TraceContext.ORDER_STATUS, status);
        if (latencyMs != null) {
            TraceContext.put(TraceContext.LATENCY_MS, Long.toString(latencyMs));
        } else {
            MDC.remove(TraceContext.LATENCY_MS);
        }
        log.info("order status");
    }

    private static Long latencySinceCreated(Instant createdAt) {
        if (createdAt == null) {
            return null;
        }
        return Math.max(0L, Duration.between(createdAt, Instant.now()).toMillis());
    }

    private void appendHistory(UUID orderId, String type, Map<String, Object> data) {
        OrderEventEntity ev = new OrderEventEntity();
        ev.setOrderId(orderId);
        ev.setType(type);
        ev.setData(data);
        ev.setAt(Instant.now());
        orderEventRepository.save(ev);
    }

    private OrderView toView(OrderEntity order) {
        List<OrderEventEntity> history = orderEventRepository.findByOrderIdOrderByIdAsc(order.getId());
        return buildView(order, history);
    }

    private OrderView toSummaryView(OrderEntity order) {
        return buildView(order, List.of());
    }

    private OrderView buildView(OrderEntity order, List<OrderEventEntity> history) {
        List<OrderView.Line> lines = order.getItems().stream()
                .map(i -> new OrderView.Line(i.getSku(), i.getQty(), i.getUnitPriceCents()))
                .toList();
        List<OrderView.HistoryEntry> hist = history.stream()
                .map(h -> new OrderView.HistoryEntry(h.getType(), h.getData(), h.getAt()))
                .toList();
        return new OrderView(
                order.getId(),
                order.getStatus(),
                order.getTotalCents(),
                order.getPaymentToken(),
                order.getCancelReason(),
                lines,
                hist,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    public static String hashRequest(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record LineRequest(String sku, int qty) {}

    public record CreateOrderResult(OrderView order, boolean replay, boolean hashMismatch) {}

    public record OrderPageView(List<OrderView> content, int page, int size, long totalElements) {}

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(UUID id) {
            super("Order not found: " + id);
        }
    }
}
