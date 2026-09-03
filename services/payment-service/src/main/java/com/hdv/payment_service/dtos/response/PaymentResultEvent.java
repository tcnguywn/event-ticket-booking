package com.hdv.payment_service.dtos.response;

import com.hdv.payment_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResultEvent {
    private String eventId;
    private String orderId;
    private String paymentId;
    private PaymentStatus status;
    private String reason;
    private LocalDateTime processedAt;
}
