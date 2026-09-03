package com.hdv.event_ticket_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.OrderConfirmedEvent;
import com.hdv.event_ticket_service.idempotency.ProcessedEventRepository;
import com.hdv.event_ticket_service.ticket.service.BookingSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    private static final String CONSUMER_NAME = "OrderConfirmedConsumer";

    private final ProcessedEventRepository processedEventRepository;
    private final BookingSagaService bookingSagaService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.confirmed", groupId = "event-ticket-group")
    @Transactional
    public void consumeOrderConfirmed(String payload, MessageHeaders headers) {
        log.info("Received order.confirmed event: {}", payload);
        try {
            OrderConfirmedEvent event = objectMapper.readValue(payload, OrderConfirmedEvent.class);
            UUID eventId = event.getIdempotencyKey() != null ? event.getIdempotencyKey() : UUID.randomUUID();

            // Atomic Idempotency Check with Composite Uniqueness (event_id, consumer_name)
            if (processedEventRepository.insertIfNotExists(eventId, CONSUMER_NAME) == 0) {
                log.info("[{}] Event {} already processed. Skipping duplicate.", CONSUMER_NAME, eventId);
                return;
            }

            // Chốt booking và chốt trạng thái ghế vĩnh viễn trong BookingSagaService
            bookingSagaService.confirmBooking(event.getBookingGroupId());

        } catch (Exception e) {
            log.error("Failed to process order.confirmed event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing order.confirmed event", e);
        }
    }
}
