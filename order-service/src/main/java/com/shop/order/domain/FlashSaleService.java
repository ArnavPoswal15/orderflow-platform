package com.shop.order.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class FlashSaleService {

    private static final Logger log = LoggerFactory.getLogger(FlashSaleService.class);
    private static final long POLL_TIMEOUT_MS = 120_000L;

    private final InventoryClient inventoryClient;
    private final StockCacheService stockCacheService;
    private final OrderApplicationService orderApplicationService;

    public FlashSaleService(
            InventoryClient inventoryClient,
            StockCacheService stockCacheService,
            OrderApplicationService orderApplicationService) {
        this.inventoryClient = inventoryClient;
        this.stockCacheService = stockCacheService;
        this.orderApplicationService = orderApplicationService;
    }

    public FlashSaleResult run(String sku, int concurrentOrders) {
        inventoryClient.setAvailable(sku, 1);
        stockCacheService.invalidate(sku);
        List<UUID> orderIds = placeOrders(sku, concurrentOrders);
        FlashSaleResult result = awaitTerminal(orderIds, sku, concurrentOrders);
        log.info(
                "flash sale sku={} requested={} confirmed={} cancelled={}",
                sku,
                concurrentOrders,
                result.confirmed(),
                result.cancelled());
        return result;
    }

    private List<UUID> placeOrders(String sku, int concurrentOrders) {
        ExecutorService pool = Executors.newFixedThreadPool(concurrentOrders);
        try {
            List<Future<UUID>> futures = new ArrayList<>(concurrentOrders);
            for (int i = 0; i < concurrentOrders; i++) {
                futures.add(pool.submit(() -> placeOne(sku)));
            }
            List<UUID> ids = new ArrayList<>(concurrentOrders);
            for (Future<UUID> future : futures) {
                ids.add(future.get());
            }
            return ids;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("flash sale interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("flash sale order failed", cause);
        } finally {
            pool.shutdown();
        }
    }

    private UUID placeOne(String sku) {
        String idempotencyKey = "flash-" + UUID.randomUUID();
        String hash = OrderApplicationService.hashRequest(sku + "|1|ok|" + idempotencyKey);
        OrderApplicationService.CreateOrderResult result = orderApplicationService.createOrder(
                idempotencyKey,
                hash,
                List.of(new OrderApplicationService.LineRequest(sku, 1)),
                "ok");
        if (result.hashMismatch() || result.order() == null) {
            throw new IllegalStateException("flash sale order was not created");
        }
        return result.order().id();
    }

    private FlashSaleResult awaitTerminal(List<UUID> orderIds, String sku, int concurrentOrders) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        List<OrderOutcome> outcomes = snapshot(orderIds);
        while (!allTerminal(outcomes) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outcomes = snapshot(orderIds);
        }
        return summarize(outcomes, sku, concurrentOrders);
    }

    private List<OrderOutcome> snapshot(List<UUID> orderIds) {
        List<OrderOutcome> outcomes = new ArrayList<>(orderIds.size());
        for (UUID orderId : orderIds) {
            OrderView view = orderApplicationService.getOrder(orderId).orElse(null);
            if (view == null) {
                outcomes.add(new OrderOutcome(orderId, OrderStatus.PENDING, null));
            } else {
                outcomes.add(new OrderOutcome(view.id(), view.status(), view.cancelReason()));
            }
        }
        return outcomes;
    }

    private static boolean allTerminal(List<OrderOutcome> outcomes) {
        for (OrderOutcome outcome : outcomes) {
            if (!isTerminal(outcome.status())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTerminal(String status) {
        return OrderStatus.CONFIRMED.equals(status)
                || OrderStatus.CANCELLED.equals(status)
                || OrderStatus.SHIPPED.equals(status)
                || OrderStatus.DELIVERED.equals(status);
    }

    private static FlashSaleResult summarize(List<OrderOutcome> outcomes, String sku, int concurrentOrders) {
        int confirmed = 0;
        int cancelled = 0;
        for (OrderOutcome outcome : outcomes) {
            if (OrderStatus.CONFIRMED.equals(outcome.status())) {
                confirmed++;
            } else if (OrderStatus.CANCELLED.equals(outcome.status())) {
                cancelled++;
            }
        }
        return new FlashSaleResult(confirmed, cancelled, concurrentOrders, sku, List.copyOf(outcomes));
    }

    public record FlashSaleResult(
            int confirmed, int cancelled, int concurrentOrders, String sku, List<OrderOutcome> orders) {}

    public record OrderOutcome(UUID orderId, String status, String cancelReason) {}
}
