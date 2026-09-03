package com.hdv.event_ticket_service.exception;

import org.springframework.http.HttpStatus;

public class ExceedPurchaseLimitException extends AppException {
    public ExceedPurchaseLimitException() {
        super("Total tickets booked exceed the per-user limit", HttpStatus.BAD_REQUEST);
    }
}
