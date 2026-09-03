package com.hdv.order_service.report.controller;

import com.hdv.order_service.report.dto.SalesReportResponse;
import com.hdv.order_service.report.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/reports")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService salesReportService;

    @GetMapping("/sales/{eventId}")
    public ResponseEntity<SalesReportResponse> getSalesReport(
            @PathVariable UUID eventId,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {

        // Chỉ Organizer hoặc Admin mới được xem doanh số
        if (!"ORGANIZER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(salesReportService.getSalesReport(eventId));
    }
}
