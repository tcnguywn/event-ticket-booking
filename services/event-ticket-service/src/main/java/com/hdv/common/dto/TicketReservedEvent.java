package com.hdv.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketReservedEvent implements Serializable {
    private UUID bookingGroupId;
    private UUID userId;
    private String email;
    private UUID eventId;
    private Long totalPrice;
    private UUID idempotencyKey;
    private List<TicketItemDto> items;
    @Builder.Default
    private String timestamp = Instant.now().toString();
}
