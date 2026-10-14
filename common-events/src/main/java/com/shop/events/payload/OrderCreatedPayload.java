package com.shop.events.payload;

import java.util.List;

public record OrderCreatedPayload(List<OrderLineItem> items, long amountCents, String paymentToken) {}
