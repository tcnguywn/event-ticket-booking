package com.hdv.event_ticket_service.ticket.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BookTicketRequest {
    @NotNull
    private UUID eventId;

    @NotEmpty
    @Valid
    private List<BookTicketItemRequest> items;

    @Data
    public static class BookTicketItemRequest {
        @NotNull
        private UUID ticketTypeId;

        @Min(1)
        private int quantity;

        // Danh sách ID ghế cụ thể (nếu là vé chọn ghế có số, để trống nếu là vé đứng/khu vực)
        private List<UUID> seatIds;
    }
}
