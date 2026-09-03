package com.hdv.event_ticket_service.exception;

import org.springframework.http.HttpStatus;

public class TicketTypeNotFoundException extends AppException {
    public TicketTypeNotFoundException() {
        super("Ticket type not found", HttpStatus.NOT_FOUND);
    }

    public TicketTypeNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
