package com.hdv.event_ticket_service.ticket.domain.enums;

public enum SeatStatus {
    FREE,    // Ghế trống có thể đặt
    HOLD,    // Đang bị giữ trong 10 phút thanh toán
    BOOKED   // Đã thanh toán thành công
}
