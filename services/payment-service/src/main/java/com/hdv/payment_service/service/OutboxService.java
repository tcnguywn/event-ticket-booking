package com.hdv.payment_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.PaymentResultEvent;
import com.hdv.common.dto.RefundResultEvent;
import com.hdv.payment_service.enums.OutboxStatus;
import com.hdv.payment_service.enums.PaymentStatus;
import com.hdv.payment_service.enums.RefundStatus;
import com.hdv.payment_service.kafka.Producer;
import com.hdv.payment_service.model.Outbox;
import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutboxRepository outboxRepository;
    private final Producer producer;
    private final ObjectMapper objectMapper;

    public void createOutboxEvent(Payment payment, PaymentStatus status, String reason) {
        UUID eventId = UUID.randomUUID();
        PaymentResultEvent resultEvent = PaymentResultEvent.builder()
            .eventId(payment.getEventId() != null ? UUID.fromString(payment.getEventId()) : UUID.randomUUID())
            .orderId(payment.getOrderId() != null ? UUID.fromString(payment.getOrderId()) : UUID.randomUUID())
            .paymentId(payment.getId())
            .status(status.name())
            .paymentGateway("VNPAY")
            .failureReason(reason)
            .idempotencyKey(payment.getIdempotencyKey() != null ? UUID.fromString(payment.getIdempotencyKey()) : UUID.randomUUID())
            .processedAt(Instant.now())
            .build();

        Outbox outbox = Outbox.builder()
            .eventId(eventId)
            .aggregateId(payment.getOrderId())
            .aggregateType("PAYMENT")
            .eventType("PAYMENT_RESULT")
            .topic(status == PaymentStatus.COMPLETED ? "payment.completed" : "payment.failed")
            .payload(serialize(resultEvent))
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .maxRetry(5)
            .createdAt(LocalDateTime.now())
            .nextRetryAt(LocalDateTime.now())
            .build();

        saveOutboxAndRegisterFastPath(outbox);
    }

    public void createRefundOutboxEvent(Payment payment, RefundStatus status, String reason) {
        UUID eventId = UUID.randomUUID();
        RefundResultEvent resultEvent = RefundResultEvent.builder()
            .eventId(eventId)
            .orderId(payment.getOrderId() != null ? UUID.fromString(payment.getOrderId()) : UUID.randomUUID())
            .paymentId(payment.getId())
            .status(status.name())
            .reason(reason)
            .processedAt(Instant.now())
            .build();

        Outbox outbox = Outbox.builder()
            .eventId(eventId)
            .aggregateId(payment.getOrderId())
            .aggregateType("PAYMENT")
            .eventType("REFUND_RESULT")
            .topic("refund.completed")
            .payload(serialize(resultEvent))
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .maxRetry(5)
            .createdAt(LocalDateTime.now())
            .nextRetryAt(LocalDateTime.now())
            .build();

        saveOutboxAndRegisterFastPath(outbox);
    }

    public Outbox saveOutboxAndRegisterFastPath(Outbox outbox) {
        Outbox saved = outboxRepository.save(outbox);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> tryPublishFastPath(saved));
                }
            });
        } else {
            CompletableFuture.runAsync(() -> tryPublishFastPath(saved));
        }

        return saved;
    }

    public void tryPublishFastPath(Outbox outbox) {
        try {
            log.info("Triggering AFTER_COMMIT Fast-Path for payment outbox eventId: {}, topic: {}", outbox.getEventId(), outbox.getTopic());
            producer.send(outbox.getTopic(), outbox.getAggregateId(), outbox.getPayload());
            markAsSent(outbox.getId());
            log.info("Fast-path publish SUCCESS for payment outbox eventId: {}", outbox.getEventId());
        } catch (Exception e) {
            log.warn("Fast-path publish failed for eventId: {}. Event remains PENDING for Poller fallback. Error: {}",
                    outbox.getEventId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSent(UUID outboxId) {
        outboxRepository.markAsSent(outboxId, LocalDateTime.now());
    }

    private String serialize(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
