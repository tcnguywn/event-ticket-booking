package com.hdv.event_ticket_service.ticket.domain.enums;

public enum BookingSeatStatus {
    HOLD,       // Đang giữ chỗ chờ thanh toán
    BOOKED,     // Đã thanh toán thành công
    RELEASED    // Đã nhả ghế do hủy đơn hoặc hết hạn thanh toán
}
