package com.hdv.event_ticket_service.exception;

import org.springframework.http.HttpStatus;

public class ExceedOrderQuantityException extends AppException {
    public ExceedOrderQuantityException() {
        super("Order quantity exceeds the allowed maximum per order", HttpStatus.BAD_REQUEST);
    }
}
