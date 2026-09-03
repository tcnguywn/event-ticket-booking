package com.hdv.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent implements Serializable {
    private UUID orderId;
    private UUID bookingGroupId;
    private UUID userId;
    private String userEmail;
    private UUID eventId;
    private Long totalAmount;
    private UUID idempotencyKey;
    private List<ConfirmedItemDto> items;
    @Builder.Default
    private Instant confirmedAt = Instant.now();
}
