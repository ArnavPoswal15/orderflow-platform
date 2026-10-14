package com.shop.order.domain;

import com.shop.order.persistence.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final IdempotencyService idempotencyService;
    private final OrderCreator orderCreator;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            IdempotencyService idempotencyService,
            OrderCreator orderCreator) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.idempotencyService = idempotencyService;
        this.orderCreator = orderCreator;
    }

    @Transactional(readOnly = true)
    public Optional<OrderView> getOrder(UUID id) {
        return orderRepository.findById(id).map(this::toView);
    }

    public CreateOrderResult createOrder(String idempotencyKey, String requestHash, List<LineRequest> lines, String paymentToken) {
        String cached = idempotencyService.getCachedOrderId(idempotencyKey);
        if (cached != null) {
            return orderRepository.findById(UUID.fromString(cached))
                    .map(o -> new CreateOrderResult(toView(o), true, false))
                    .orElseGet(() -> createNew(idempotencyKey, requestHash, lines, paymentToken));
        }
        return createNew(idempotencyKey, requestHash, lines, paymentToken);
    }

    private CreateOrderResult createNew(String idempotencyKey, String requestHash, List<LineRequest> lines, String paymentToken) {
        try {
            OrderEntity created = orderCreator.create(idempotencyKey, requestHash, lines, paymentToken);
            return new CreateOrderResult(toView(created), false, false);
        } catch (DataIntegrityViolationException dup) {
            Optional<OrderEntity> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isEmpty()) {
                throw dup;
            }
            OrderEntity order = existing.get();
            if (!order.getRequestHash().equals(requestHash)) {
                return new CreateOrderResult(null, false, true);
            }
            idempotencyService.cacheAfterCommit(idempotencyKey, order.getId().toString());
            return new CreateOrderResult(toView(order), true, false);
        }
    }

    @Transactional
    public void transition(UUID orderId, String toStatus, String eventType, Map<String, Object> data) {
        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        String from = order.getStatus();
        if (from.equals(toStatus)) {
            return;
        }
        if (OrderStatus.CANCELLED.equals(from) && OrderStatus.CONFIRMED.equals(toStatus)) {
            return;
        }
        order.setStatus(toStatus);
        order.setUpdatedAt(Instant.now());
        Map<String, Object> hist = new HashMap<>(data != null ? data : Map.of());
        hist.put("from", from);
        hist.put("to", toStatus);
        appendHistory(orderId, eventType, hist);
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

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
