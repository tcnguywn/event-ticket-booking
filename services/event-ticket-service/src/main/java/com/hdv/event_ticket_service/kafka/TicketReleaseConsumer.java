package com.hdv.event_ticket_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.TicketReleaseEvent;
import com.hdv.event_ticket_service.idempotency.ProcessedEventRepository;
import com.hdv.event_ticket_service.ticket.service.BookingService;
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
public class TicketReleaseConsumer {

    private static final String CONSUMER_NAME = "TicketReleaseConsumer";

    private final ProcessedEventRepository processedEventRepository;
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ticket.release", groupId = "event-ticket-group")
    @Transactional
    public void consumeTicketRelease(String payload, MessageHeaders headers) {
        log.info("Received ticket release message: {}", payload);
        try {
            TicketReleaseEvent event = objectMapper.readValue(payload, TicketReleaseEvent.class);
            UUID eventId = event.getIdempotencyKey() != null ? event.getIdempotencyKey() : UUID.randomUUID();

            // Atomic Idempotency Check with Composite Uniqueness (event_id, consumer_name)
            if (processedEventRepository.insertIfNotExists(eventId, CONSUMER_NAME) == 0) {
                log.info("[{}] Event {} already processed. Skipping duplicate.", CONSUMER_NAME, eventId);
                return;
            }

            // Thực thi release ghế và hoàn kho nguyên tử, an toàn trong BookingService
            bookingService.releaseBooking(event.getBookingGroupId(), event.getItems());

        } catch (Exception e) {
            log.error("Failed to process ticket release: {}", e.getMessage(), e);
            throw new RuntimeException("Retry ticket.release", e);
        }
    }
}
