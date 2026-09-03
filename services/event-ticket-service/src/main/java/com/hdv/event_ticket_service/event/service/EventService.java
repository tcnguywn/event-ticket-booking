package com.hdv.event_ticket_service.event.service;

import com.hdv.event_ticket_service.event.domain.dtos.CreateEventRequest;
import com.hdv.event_ticket_service.event.domain.dtos.EventResponse;
import com.hdv.event_ticket_service.event.domain.entity.Event;
import com.hdv.event_ticket_service.event.domain.enums.EventStatus;
import com.hdv.event_ticket_service.event.repository.EventRepository;
import com.hdv.event_ticket_service.exception.AppException;
import com.hdv.event_ticket_service.ticket.domain.entity.TicketType;
import com.hdv.event_ticket_service.ticket.repository.TicketTypeRepository;
import com.hdv.event_ticket_service.ticket.domain.dtos.TicketTypeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final com.hdv.event_ticket_service.outbox.service.OutboxService outboxService;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, UUID organizerId) {
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new AppException("Start time must be before end time", HttpStatus.BAD_REQUEST);
        }
        log.info("Creating event with organizer ID: {}", organizerId);
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .organizerId(organizerId)
                .status(EventStatus.DRAFT)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .location(request.getLocation())
                .maxTicketsPerUser(request.getMaxTicketsPerUser())
                .build();

        event = eventRepository.save(event);

        for (CreateEventRequest.TicketTypeRequest tr : request.getTicketTypes()) {
            TicketType ticketType = TicketType.builder()
                    .eventId(event.getId())
                    .name(tr.getName())
                    .price(tr.getPrice())
                    .quantity(tr.getQuantity())
                    .maxOrderQuantity(tr.getMaxOrderQuantity())
                    .build();
            ticketType = ticketTypeRepository.save(ticketType);

            // Pre-warm the redis cache for this ticket type
            String key = "ticket_stock:" + ticketType.getId();
            redisTemplate.opsForValue().set(key, String.valueOf(ticketType.getQuantity()));
        }

        // Bắn CDC Event vào Outbox để đồng bộ sang Elasticsearch
        com.hdv.event_ticket_service.outbox.domain.Outbox outbox = com.hdv.event_ticket_service.outbox.domain.Outbox.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("EVENT")
                .aggregateId(event.getId().toString())
                .eventType("EVENT_UPSERTED")
                .topic("event.cdc.sync")
                .payload(String.format("{\"eventId\":\"%s\"}", event.getId()))
                .status(com.hdv.event_ticket_service.outbox.domain.OutboxStatus.PENDING)
                .build();
        outboxService.saveOutboxAndRegisterFastPath(outbox);

        log.info("Event created with ID: {}", event.getId());

        return mapToResponse(event);
    }

    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public EventResponse getEvent(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AppException("Event not found", HttpStatus.NOT_FOUND));
        return mapToResponse(event);
    }

    private EventResponse mapToResponse(Event event) {
        List<TicketTypeResponse> ticketTypeResponses = ticketTypeRepository.findByEventId(event.getId()).stream()
                .map(ticketType -> {
                    TicketTypeResponse response = new TicketTypeResponse();
                    response.setId(ticketType.getId());
                    response.setName(ticketType.getName());
                    response.setPrice(ticketType.getPrice());
                    response.setQuantity(ticketType.getQuantity());
                    response.setMaxOrderQuantity(ticketType.getMaxOrderQuantity());
                    return response;
                }).collect(Collectors.toList());

        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .organizerId(event.getOrganizerId())
                .status(event.getStatus())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .location(event.getLocation())
                .maxTicketsPerUser(event.getMaxTicketsPerUser())
                .createdAt(event.getCreatedAt())
                .ticketTypes(ticketTypeResponses)
                .build();
    }
}
