package com.shop.events.payload;

public record InventoryRejectedPayload(String sku, String reason) {}
