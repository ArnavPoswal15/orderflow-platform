package com.shop.events.payload;

import java.util.List;

public record InventoryReleasedPayload(List<OrderLineItem> items) {}
