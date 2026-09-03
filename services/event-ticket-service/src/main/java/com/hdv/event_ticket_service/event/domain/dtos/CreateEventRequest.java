package com.hdv.event_ticket_service.event.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    @NotBlank
    private String title;
    
    private String description;
    
    @NotEmpty
    @Valid
    private List<TicketTypeRequest> ticketTypes;
    
    @NotNull
    private LocalDateTime startTime;
    
    @NotNull
    private LocalDateTime endTime;
    
    private String location;

    private Integer maxTicketsPerUser = 10;

    @Data
    public static class TicketTypeRequest {
        @NotBlank
        private String name;
        
        @NotNull
        private Long price;
        
        @NotNull
        private Integer quantity;
        
        private Integer maxOrderQuantity = 10;
    }
}
