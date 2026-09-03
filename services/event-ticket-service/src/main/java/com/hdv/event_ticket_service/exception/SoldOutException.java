package com.hdv.event_ticket_service.exception;

import org.springframework.http.HttpStatus;

public class SoldOutException extends AppException {
    public SoldOutException() {
        super("Tickets are sold out", HttpStatus.BAD_REQUEST);
    }
}
