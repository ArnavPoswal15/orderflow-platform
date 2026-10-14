package com.shop.order.domain;

public final class OrderStatus {
    public static final String PENDING = "PENDING";
    public static final String RESERVED = "RESERVED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    private OrderStatus() {}
}
