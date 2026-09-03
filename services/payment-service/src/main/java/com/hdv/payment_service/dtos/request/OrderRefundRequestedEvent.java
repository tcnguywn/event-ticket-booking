package com.hdv.payment_service.dtos.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundRequestedEvent {
    private String eventId;
    private String orderId;
    private String userId;
    private Long amount;
    private String reason;
    private LocalDateTime requestedAt;
}
