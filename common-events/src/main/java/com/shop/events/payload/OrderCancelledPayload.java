package com.shop.events.payload;

public record OrderCancelledPayload(String reason, boolean paidBeforeCancel) {}
