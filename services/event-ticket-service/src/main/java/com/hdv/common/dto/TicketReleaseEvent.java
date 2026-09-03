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
public class TicketReleaseEvent implements Serializable {
    private UUID orderId;
    private UUID bookingGroupId;
    private UUID idempotencyKey;
    private String reason;
    private List<ReleaseItemDto> items;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
