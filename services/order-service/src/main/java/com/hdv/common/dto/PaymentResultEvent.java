package com.hdv.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent implements Serializable {
    private UUID eventId;
    private UUID orderId;
    private UUID paymentId;
    private String status;
    private String paymentGateway;
    private String transactionNo;
    private String bankCode;
    private Long amount;
    private String failureReason;
    private UUID idempotencyKey;
    @Builder.Default
    private Instant processedAt = Instant.now();
}
