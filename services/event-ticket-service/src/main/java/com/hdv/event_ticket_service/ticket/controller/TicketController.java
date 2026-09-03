package com.hdv.event_ticket_service.ticket.controller;

import com.hdv.event_ticket_service.exception.AppException;
import com.hdv.event_ticket_service.ticket.domain.dtos.BookTicketRequest;
import com.hdv.event_ticket_service.ticket.domain.dtos.BookTicketResponse;
import com.hdv.event_ticket_service.ticket.service.TicketBookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketBookingService ticketBookingService;

    @PostMapping("/book")
    public BookTicketResponse bookTicket(
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String roleHeader,
            @Valid @RequestBody BookTicketRequest request) {

        if (!"USER".equalsIgnoreCase(roleHeader)) {
            throw new AppException("Only users can book tickets", HttpStatus.FORBIDDEN);
        }

        UUID userId = UUID.fromString(userIdHeader);
        return ticketBookingService.bookTicket(userId, email, request);
    }
}
