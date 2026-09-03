package com.hdv.event_ticket_service.event.domain.dtos;

import com.hdv.event_ticket_service.event.domain.enums.EventStatus;
import com.hdv.event_ticket_service.ticket.domain.dtos.TicketTypeResponse;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EventResponse {
    private UUID id;
    private String title;
    private String description;
    private UUID organizerId;
    private EventStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private Integer maxTicketsPerUser;
    private LocalDateTime createdAt;
    private List<TicketTypeResponse> ticketTypes;
}
