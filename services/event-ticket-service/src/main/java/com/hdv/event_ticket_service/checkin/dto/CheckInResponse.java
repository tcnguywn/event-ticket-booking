package com.hdv.event_ticket_service.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {
    private String status; // SUCCESS, ALREADY_CHECKED_IN, INVALID_SIGNATURE, NOT_FOUND
    private String message;
    private UUID ticketId;
    private LocalDateTime checkInTime;
}
