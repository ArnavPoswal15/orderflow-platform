package com.shop.payment.domain;

import com.shop.payment.persistence.OutboxEntity;
import com.shop.payment.persistence.OutboxRepository;
import com.shop.payment.persistence.PaymentEntity;
import com.shop.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    PaymentGateway paymentGateway;

    PaymentApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentApplicationService(paymentRepository, outboxRepository, paymentGateway);
    }

    @Test
    void chargeFailure_emitsPaymentFailed() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = pendingPayment(orderId);
        payment.setPaymentToken("fail");
        when(paymentRepository.findById(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.charge("fail")).thenReturn(false);

        service.chargeOnReserved(orderId);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(outboxRepository).save(any(OutboxEntity.class));
    }

    @Test
    void cancelAfterSuccess_refunds() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = pendingPayment(orderId);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(orderId)).thenReturn(Optional.of(payment));

        service.handleOrderCancelled(orderId);

        assertTrue(payment.isCancelRequested());
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentGateway).refund("tok");
    }

    @Test
    void chargeSkipsWhenCancelAlreadyRequested() {
        UUID orderId = UUID.randomUUID();
        PaymentEntity payment = pendingPayment(orderId);
        payment.setCancelRequested(true);
        when(paymentRepository.findById(orderId)).thenReturn(Optional.of(payment));

        service.chargeOnReserved(orderId);

        verify(paymentGateway, never()).charge(any());
        verify(outboxRepository, never()).save(any());
    }

    private static PaymentEntity pendingPayment(UUID orderId) {
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(orderId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmountCents(399);
        payment.setPaymentToken("tok");
        payment.setCancelRequested(false);
        return payment;
    }
}
