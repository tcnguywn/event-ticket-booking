package com.hdv.payment_service.service;

import com.hdv.common.dto.OrderRefundRequestedEvent;
import com.hdv.payment_service.enums.PaymentStatus;
import com.hdv.payment_service.enums.RefundStatus;
import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Transactional
    public void processRefundRequest(OrderRefundRequestedEvent event) {
        log.info("Processing refund request for orderId={}", event.getOrderId());
        
        Payment payment = paymentRepository.findByOrderId(event.getOrderId().toString())
            .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + event.getOrderId()));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.warn("Cannot refund payment with status={} for orderId={}",
                payment.getStatus(), event.getOrderId());

            outboxService.createRefundOutboxEvent(payment, RefundStatus.FAILED,
                "Payment status is not SUCCESS: " + payment.getStatus());
            return;
        }

        try {
            // Simulate VNPay Refund success
            log.info("Simulating VNPay Refund API call for transaction: {}", payment.getTransactionRef());

            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            outboxService.createRefundOutboxEvent(payment, RefundStatus.SUCCESS, null);
            log.info("Refund SUCCESS for orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("Refund FAILED for orderId={}", event.getOrderId(), e);

            payment.setStatus(PaymentStatus.REFUND_FAILED);
            paymentRepository.save(payment);

            outboxService.createRefundOutboxEvent(payment, RefundStatus.FAILED, e.getMessage());
        }
    }
}
