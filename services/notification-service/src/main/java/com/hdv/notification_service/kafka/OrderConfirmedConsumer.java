package com.hdv.notification_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.OrderConfirmedEvent;
import com.hdv.notification_service.idempotency.ProcessedEventCache;
import com.hdv.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    private final NotificationService notificationService;
    private final ProcessedEventCache cache;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.confirmed", groupId = "${spring.kafka.consumer.group-id}")
    public void listenPaymentCompleted(@Payload String message,
                                       @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey) {
        log.info("Received order.confirmed message: {}", message);
        try {
            OrderConfirmedEvent event = objectMapper.readValue(message, OrderConfirmedEvent.class);

            String orderId = event.getOrderId().toString();
            String email = event.getUserEmail();
            String eventId = event.getEventId().toString();
            long totalPrice = event.getTotalAmount();

            String idempotencyKey = "order-confirmation-" + orderId;

            // Idempotency check with Redis
            if (cache.isProcessed(idempotencyKey)) {
                log.info("Message for idempotencyKey {} was already processed. Skipping.", idempotencyKey);
                return;
            }

            notificationService.processOrderConfirmed(orderId, email, eventId, event.getItems(), totalPrice);
            
        } catch (Exception e) {
            log.error("Failed to process order.confirmed message: {}. Reason: {}", message, e.getMessage());
        }
    }
}
