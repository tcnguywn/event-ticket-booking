package com.hdv.order_service.outbox.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
