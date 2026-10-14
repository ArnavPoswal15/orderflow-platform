package com.shop.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderView(
        UUID id,
        String status,
        long totalCents,
        String paymentToken,
        List<Line> items,
        List<HistoryEntry> history,
        Instant createdAt,
        Instant updatedAt) {

    public record Line(String sku, int qty, long unitPriceCents) {}

    public record HistoryEntry(String type, Map<String, Object> data, Instant at) {}
}
