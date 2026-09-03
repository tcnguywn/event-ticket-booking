package com.hdv.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundRequestedEvent implements Serializable {
    private UUID eventId;
    private UUID orderId;
    private UUID userId;
    private Long amount;
    private String reason;
    @Builder.Default
    private Instant requestedAt = Instant.now();
}
