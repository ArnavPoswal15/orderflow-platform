package com.shop.payment.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentGatewayTest {

    @Test
    void rate0_acceptsTokenOk() {
        PaymentGateway gateway = new PaymentGateway(new PaymentFailureRate(0));
        assertTrue(gateway.charge("ok"));
    }

    @Test
    void rate100_rejectsTokenOk() {
        PaymentFailureRate rate = new PaymentFailureRate(0);
        PaymentGateway gateway = new PaymentGateway(rate);
        assertTrue(gateway.charge("ok"));
        rate.setPercent(100);
        for (int i = 0; i < 20; i++) {
            assertFalse(gateway.charge("ok"));
        }
    }

    @Test
    void tokenFail_rejectsAtRate0() {
        PaymentGateway gateway = new PaymentGateway(new PaymentFailureRate(0));
        assertFalse(gateway.charge("fail"));
    }

    @Test
    void percentOutside0To100_isRejected() {
        PaymentFailureRate rate = new PaymentFailureRate(0);
        assertThrows(PaymentFailureRate.InvalidPercentException.class, () -> rate.setPercent(-1));
        assertThrows(PaymentFailureRate.InvalidPercentException.class, () -> rate.setPercent(101));
        assertThrows(PaymentFailureRate.InvalidPercentException.class, () -> new PaymentFailureRate(-1));
        assertThrows(PaymentFailureRate.InvalidPercentException.class, () -> new PaymentFailureRate(101));
        assertEquals(0, rate.getPercent());
    }
}
