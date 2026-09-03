package com.hdv.order_service.saga.domain;

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
