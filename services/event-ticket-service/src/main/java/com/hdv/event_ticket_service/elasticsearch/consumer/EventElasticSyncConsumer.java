package com.hdv.event_ticket_service.elasticsearch.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.event_ticket_service.elasticsearch.event.EventElasticDocument;
import com.hdv.event_ticket_service.elasticsearch.event.EventSearchService;
import com.hdv.event_ticket_service.event.domain.entity.Event;
import com.hdv.event_ticket_service.event.repository.EventRepository;
import com.hdv.event_ticket_service.idempotency.ProcessedEventRepository;
import com.hdv.event_ticket_service.ticket.domain.entity.TicketType;
import com.hdv.event_ticket_service.ticket.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventElasticSyncConsumer {

    private static final String CONSUMER_NAME = "EventElasticSyncConsumer";

    private final EventSearchService eventSearchService;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "event.cdc.sync", groupId = "event-elastic-sync-group")
    public void consumeEventSync(String payload, MessageHeaders headers) {
        log.info("Received event.cdc.sync message: {}", payload);
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String eventIdStr = (String) data.get("eventId");
            if (eventIdStr == null) return;

            UUID eventId = UUID.fromString(eventIdStr);
            UUID eventMessageId = UUID.randomUUID();

            if (processedEventRepository.insertIfNotExists(eventMessageId, CONSUMER_NAME) == 0) {
                log.info("Event sync {} already processed. Skipping.", eventMessageId);
                return;
            }

            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                log.warn("Event {} not found for elasticsearch sync", eventId);
                return;
            }

            List<TicketType> ticketTypes = ticketTypeRepository.findByEventId(eventId);
            double minPrice = ticketTypes.stream()
                    .mapToDouble(TicketType::getPrice)
                    .min()
                    .orElse(0.0);

            EventElasticDocument doc = EventElasticDocument.builder()
                    .title(event.getTitle())
                    .description(event.getDescription())
                    .location(event.getLocation())
                    .category("MUSIC") // default category
                    .minPrice(minPrice)
                    .startTime(event.getStartTime())
                    .endTime(event.getEndTime())
                    .status(event.getStatus() != null ? event.getStatus().name() : "ON_SALE")
                    .build();
            doc.setId(event.getId().toString());
            doc.setCreatedAt(event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            eventSearchService.save(doc);
            log.info("Synchronized Event {} to Elasticsearch index successfully", eventId);

            // Invalidate L2 Redis Cache for this event
            redisTemplate.delete("event_details:" + eventId);

        } catch (Exception e) {
            log.error("Failed to sync event to Elasticsearch: {}", e.getMessage(), e);
        }
    }
}
