package com.hdv.order_service.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportResponse {
    private UUID eventId;
    private long totalOrdersConfirmed;
    private long totalRevenue;
    private boolean fromCache;
}
