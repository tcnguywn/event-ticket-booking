package com.hdv.event_ticket_service.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CheckInRequest {
    @NotNull
    private UUID ticketId;

    @NotNull
    private UUID eventId;

    @NotBlank
    private String signature;
}
