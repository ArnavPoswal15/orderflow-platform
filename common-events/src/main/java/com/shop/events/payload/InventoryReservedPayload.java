package com.shop.events.payload;

import java.util.List;

public record InventoryReservedPayload(List<OrderLineItem> items) {}
