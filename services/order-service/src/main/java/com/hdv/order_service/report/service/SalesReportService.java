package com.hdv.order_service.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.order_service.order.repository.OrderRepository;
import com.hdv.order_service.report.dto.SalesReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesReportService {

    private final OrderRepository orderRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REPORT_CACHE_PREFIX = "sales_report:";
    private static final Duration REPORT_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Báo cáo doanh số sự kiện theo dạng Point-in-Time Snapshot (Cache 10 phút trên Redis).
     * Loại bỏ hoàn toàn sự phức tạp của Kafka Streams / Realtime OLAP.
     */
    public SalesReportResponse getSalesReport(UUID eventId) {
        String cacheKey = REPORT_CACHE_PREFIX + eventId;

        // 1. Kiểm tra cache Redis
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                SalesReportResponse cached = objectMapper.readValue(cachedJson, SalesReportResponse.class);
                cached.setFromCache(true);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Failed to read sales report from Redis cache: {}", e.getMessage());
        }

        // 2. Query trực tiếp từ DB với Composite Index
        List<Object[]> results = orderRepository.getSalesReportByEventId(eventId);
        long totalOrders = 0;
        long totalRevenue = 0;

        if (results != null && !results.isEmpty() && results.get(0) != null) {
            Object[] row = results.get(0);
            totalOrders = ((Number) row[0]).longValue();
            totalRevenue = ((Number) row[1]).longValue();
        }

        SalesReportResponse response = SalesReportResponse.builder()
                .eventId(eventId)
                .totalOrdersConfirmed(totalOrders)
                .totalRevenue(totalRevenue)
                .fromCache(false)
                .build();

        // 3. Ghi vào Redis Cache với TTL 10 phút
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), REPORT_CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to save sales report to Redis cache: {}", e.getMessage());
        }

        return response;
    }
}
