package com.hdv.payment_service.dtos.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderPaymentRequestedEvent {
    private String eventId;
    private String orderId;
    private String userId;
    private Long amount;
    private String description;
    private List<OrderItemDto> items;
    private LocalDateTime requestedAt;
    private String idempotencyKey;
    private String payload;
}
