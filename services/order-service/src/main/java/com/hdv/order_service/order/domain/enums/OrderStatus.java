package com.hdv.order_service.order.domain.enums;

public enum OrderStatus {
    PENDING,   // Đơn hàng vừa tạo, đang chờ thanh toán hoặc khóa vé
    CONFIRMED, // Đã thanh toán thành công qua VNPay
    CANCELLED  // Bị hủy do quá hạn 10 phút hoặc lỗi thanh toán
}