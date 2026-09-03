package com.hdv.payment_service.dtos.response;

import com.hdv.payment_service.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResultEvent {
    private String eventId;
    private String orderId;
    private String paymentId;
    private RefundStatus status;    // SUCCESS, FAILED
    private String reason;
    private LocalDateTime processedAt;
}

