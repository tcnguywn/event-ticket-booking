package com.hdv.event_ticket_service.saga.domain;

public enum SagaStatus {
    STARTED,
    PROCESSING,
    WAITING,
    RETRYING,
    COMPENSATING,
    COMPLETED,
    FAILED,
    COMPENSATED
}
