package com.hdv.event_ticket_service.ticket.domain.dtos;

import lombok.Data;
import java.util.UUID;


@Data
public class TicketTypeResponse {
    private UUID id;
    private String name;
    private Long price;
    private Integer quantity;
    private Integer maxOrderQuantity;
}
