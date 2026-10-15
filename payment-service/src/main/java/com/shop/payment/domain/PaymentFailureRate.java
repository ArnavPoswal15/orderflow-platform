package com.shop.payment.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PaymentFailureRate {

    private final AtomicInteger percent = new AtomicInteger();

    public PaymentFailureRate(@Value("${payment.failure-rate-percent:0}") int initialPercent) {
        setPercent(initialPercent);
    }

    public int getPercent() {
        return percent.get();
    }

    public void setPercent(int next) {
        if (next < 0 || next > 100) {
            throw new InvalidPercentException();
        }
        percent.set(next);
    }

    public static class InvalidPercentException extends RuntimeException {
        public InvalidPercentException() {
            super("percent must be between 0 and 100");
        }
    }
}
