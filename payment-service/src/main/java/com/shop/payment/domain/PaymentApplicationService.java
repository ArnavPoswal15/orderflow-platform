package com.shop.payment.domain;

import com.shop.events.EventEnvelope;
import com.shop.events.EventJson;
import com.shop.events.EventTopics;
import com.shop.events.EventTypes;
import com.shop.events.TraceContext;
import com.shop.events.payload.OrderCreatedPayload;
import com.shop.events.payload.PaymentFailedPayload;
import com.shop.events.payload.PaymentRefundedPayload;
import com.shop.events.payload.PaymentSucceededPayload;
import com.shop.payment.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplicationService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentGateway paymentGateway;

    public PaymentApplicationService(
            PaymentRepository paymentRepository,
            OutboxRepository outboxRepository,
            PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.paymentGateway = paymentGateway;
    }

    @Transactional
    public void recordOrderCreated(UUID orderId, OrderCreatedPayload payload) {
        if (paymentRepository.existsById(orderId)) {
            return;
        }
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderId(orderId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmountCents(payload.amountCents());
        payment.setPaymentToken(payload.paymentToken());
        payment.setCancelRequested(false);
        Instant now = Instant.now();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);
    }

    @Transactional
    public void handleOrderCancelled(UUID orderId) {
        PaymentEntity payment = paymentRepository.findById(orderId).orElse(null);
        if (payment == null) {
            return;
        }
        payment.setCancelRequested(true);
        payment.setUpdatedAt(Instant.now());
        if (PaymentStatus.SUCCEEDED.equals(payment.getStatus())) {
            refundPayment(payment);
        }
        paymentRepository.save(payment);
    }

    @Transactional
    public void chargeOnReserved(UUID orderId) {
        PaymentEntity payment = paymentRepository.findById(orderId)
                .orElseThrow(() -> new PaymentNotReadyException(orderId));
        if (payment.isCancelRequested() || !PaymentStatus.PENDING.equals(payment.getStatus())) {
            return;
        }
        boolean ok = paymentGateway.charge(payment.getPaymentToken());
        if (!ok) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setUpdatedAt(Instant.now());
            var envelope = EventJson.newEnvelope(
                    EventTypes.PAYMENT_FAILED,
                    orderId,
                    TraceContext.currentOrNew(),
                    new PaymentFailedPayload("declined"));
            enqueue(envelope);
            TraceContext.bind(envelope);
            TraceContext.putFailureReason(envelope);
            log.info("payment declined");
            return;
        }
        paymentRepository.flush();
        PaymentEntity fresh = paymentRepository.findById(orderId).orElseThrow();
        if (fresh.isCancelRequested()) {
            fresh.setStatus(PaymentStatus.SUCCEEDED);
            fresh.setUpdatedAt(Instant.now());
            paymentGateway.refund(fresh.getPaymentToken());
            fresh.setStatus(PaymentStatus.REFUNDED);
            fresh.setUpdatedAt(Instant.now());
            enqueueRefund(fresh);
            return;
        }
        fresh.setStatus(PaymentStatus.SUCCEEDED);
        fresh.setUpdatedAt(Instant.now());
        var envelope = EventJson.newEnvelope(
                EventTypes.PAYMENT_SUCCEEDED,
                orderId,
                TraceContext.currentOrNew(),
                new PaymentSucceededPayload(fresh.getAmountCents()));
        enqueue(envelope);
    }

    private void refundPayment(PaymentEntity payment) {
        if (PaymentStatus.REFUNDED.equals(payment.getStatus())) {
            return;
        }
        paymentGateway.refund(payment.getPaymentToken());
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        enqueueRefund(payment);
    }

    private void enqueueRefund(PaymentEntity payment) {
        var envelope = EventJson.newEnvelope(
                EventTypes.PAYMENT_REFUNDED,
                payment.getOrderId(),
                TraceContext.currentOrNew(),
                new PaymentRefundedPayload(payment.getAmountCents()));
        enqueue(envelope);
    }

    private void enqueue(EventEnvelope envelope) {
        OutboxEntity row = new OutboxEntity();
        row.setEventId(envelope.eventId());
        row.setTopic(EventTopics.PAYMENTS);
        row.setKey(envelope.orderId().toString());
        row.setPayload(EventJson.serialize(envelope));
        row.setCreatedAt(Instant.now());
        outboxRepository.save(row);
    }

    public static class PaymentNotReadyException extends RuntimeException {
        public PaymentNotReadyException(UUID orderId) {
            super("Payment row not ready for order " + orderId);
        }
    }
}
