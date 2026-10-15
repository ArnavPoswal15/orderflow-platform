package com.shop.payment.domain;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class PaymentGateway {

    private final PaymentFailureRate failureRate;

    public PaymentGateway(PaymentFailureRate failureRate) {
        this.failureRate = failureRate;
    }

    public boolean charge(String paymentToken) {
        if (paymentToken != null && "fail".equals(paymentToken)) {
            return false;
        }
        int failureRatePercent = failureRate.getPercent();
        if (failureRatePercent <= 0) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) >= failureRatePercent;
    }

    public void refund(String paymentToken) {
        // simulated gateway — always succeeds
    }
}
