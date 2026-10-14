package com.shop.payment.domain;

public final class PaymentStatus {
    public static final String PENDING = "PENDING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String REFUNDED = "REFUNDED";

    private PaymentStatus() {}
}
