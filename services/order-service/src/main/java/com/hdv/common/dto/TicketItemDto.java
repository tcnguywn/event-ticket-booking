package com.hdv.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketItemDto implements Serializable {
    private UUID ticketTypeId;
    private String ticketTypeName;
    private Integer quantity;
    private Long price;
}
