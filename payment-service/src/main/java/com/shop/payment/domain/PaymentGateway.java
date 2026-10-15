package com.shop.payment.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class PaymentGateway {

    private final int failureRatePercent;

    public PaymentGateway(@Value("${payment.failure-rate-percent:0}") int failureRatePercent) {
        this.failureRatePercent = failureRatePercent;
    }

    public boolean charge(String paymentToken) {
        if (paymentToken != null && "fail".equals(paymentToken)) {
            return false;
        }
        if (failureRatePercent <= 0) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) >= failureRatePercent;
    }

    public void refund(String paymentToken) {
        // simulated gateway — always succeeds
    }
}
