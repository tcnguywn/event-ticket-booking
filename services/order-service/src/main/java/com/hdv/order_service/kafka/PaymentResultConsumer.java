package com.hdv.order_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.PaymentResultEvent;
import com.hdv.order_service.idempotency.ProcessedEventRepository;
import com.hdv.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultConsumer {

    private static final String CONSUMER_NAME = "PaymentResultConsumer";

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.completed", groupId = "order-service-group")
    public void consumeCompleted(String message) {
        processPaymentResult(message);
    }

    @KafkaListener(topics = "payment.failed", groupId = "order-service-group")
    public void consumeFailed(String message) {
        processPaymentResult(message);
    }

    private void processPaymentResult(String message) {
        try {
            PaymentResultEvent event = objectMapper.readValue(message, PaymentResultEvent.class);

            UUID paymentId = event.getPaymentId() != null ? event.getPaymentId() : UUID.randomUUID();
            UUID orderId   = event.getOrderId();
            String status  = event.getStatus();
            String reason  = event.getFailureReason();

            // Check Idempotency bằng paymentId + CONSUMER_NAME
            if (processedEventRepository.insertIfNotExists(paymentId, CONSUMER_NAME) == 0) {
                log.warn("[{}] Bỏ qua payment result trùng lặp, paymentId: {}", CONSUMER_NAME, paymentId);
                return;
            }

            log.info("Xử lý payment result: orderId={}, paymentId={}, status={}", orderId, paymentId, status);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                orderService.confirmOrder(orderId);
            } else {
                log.warn("Thanh toán thất bại cho orderId: {}, lý do: {}", orderId, reason);
                orderService.cancelIfStillPending(orderId);
            }

        } catch (Exception e) {
            log.error("Lỗi khi xử lý payment result: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}