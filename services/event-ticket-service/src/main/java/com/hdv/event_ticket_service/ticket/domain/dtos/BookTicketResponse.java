package com.hdv.event_ticket_service.ticket.domain.dtos;

import com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BookTicketResponse {
    private UUID bookingGroupId;
    private String message;
    private BookingStatus status;
}
