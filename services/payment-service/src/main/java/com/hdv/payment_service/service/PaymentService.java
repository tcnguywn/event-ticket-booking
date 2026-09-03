package com.hdv.payment_service.service;

import com.hdv.common.dto.OrderCreatedEvent;
import com.hdv.payment_service.enums.PaymentStatus;
import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Transactional
    public void processOrderPaymentRequest(OrderCreatedEvent event) {
        try {
            long orderCode = generateOrderCode();
            Payment payment = Payment.builder()
                .orderId(event.getOrderId().toString())
                .userId(event.getUserId().toString())
                .eventId(event.getEventId().toString())
                .amount(event.getTotalAmount())
                .status(PaymentStatus.PENDING)
                .transactionRef(orderCode)
                .idempotencyKey(event.getIdempotencyKey().toString())
                .payload("") // Không cần payload phức tạp
                .expiredAt(LocalDateTime.now().plusMinutes(15))
                .build();

            // Lưu payment tạm thời
            paymentRepository.save(payment);

            log.info("Payment record created for orderId={}, transactionRef={}",
                event.getOrderId(), orderCode);

        } catch (Exception e) {
            log.error("Failed to create payment for orderId={}", event.getOrderId(), e);
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void handlePaymentSuccess(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Payment already processed for orderId: {}", orderId);
            return;
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Tạo outbox event để bắn về Order Service
        outboxService.createOutboxEvent(payment, PaymentStatus.COMPLETED, null);
        log.info("Payment SUCCESS for orderId={}", payment.getOrderId());
    }

    @Transactional
    public void handlePaymentFailed(String orderId, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        if (payment.getStatus() != PaymentStatus.PENDING) return;

        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        outboxService.createOutboxEvent(payment, PaymentStatus.FAILED, reason);
        log.info("Payment FAILED for orderId={}, reason={}", payment.getOrderId(), reason);
    }

    private long generateOrderCode() {
        return System.currentTimeMillis() % 1_000_000_000L;
    }
}
