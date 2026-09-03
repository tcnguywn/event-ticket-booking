package com.hdv.payment_service.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.OrderCreatedEvent;
import com.hdv.payment_service.idempotency.ProcessedEventRepository;
import com.hdv.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private static final String CONSUMER_NAME = "PaymentConsumer";

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "order.created",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
        @Payload String rawMessage,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        Acknowledgment ack
    ) {
        log.info("Received raw message from topic={}: {}", topic, rawMessage);
        try {
            OrderCreatedEvent event = objectMapper.readValue(rawMessage, OrderCreatedEvent.class);
            UUID eventId = event.getIdempotencyKey() != null ? event.getIdempotencyKey() : UUID.randomUUID();

            // Check Idempotency với composite uniqueness
            if (processedEventRepository.insertIfNotExists(eventId, CONSUMER_NAME) == 0) {
                log.warn("[{}] Bỏ qua message order.created trùng lặp: {}", CONSUMER_NAME, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Received payment request for orderId={}", event.getOrderId());
            paymentService.processOrderPaymentRequest(event);
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message: {}", rawMessage, e);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment event: {}", e.getMessage(), e);
        }
    }
}
