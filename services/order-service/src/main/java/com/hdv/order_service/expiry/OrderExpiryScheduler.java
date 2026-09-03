package com.hdv.order_service.expiry;

import com.hdv.order_service.order.domain.entity.Order;
import com.hdv.order_service.order.repository.OrderRepository;
import com.hdv.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * Chạy định kỳ mỗi 15 phút (900,000 ms)
     * Giữ lock tối đa 14 phút để tránh 2 instance cùng chạy job này.
     */
    @Scheduled(fixedDelay = 900_000)
    @SchedulerLock(name = "orderExpiryCleanup", lockAtMostFor = "PT14M")
    public void cleanupExpiredOrders() {
        log.info("Bat dau chay Safety Net : don dep cac don hang PENDING ket trong he thong.");

        // Quét các đơn hàng PENDING tạo trước 12 phút.
        // Chúng ta để dư 2 phút (so với 10 phút chuẩn) để tránh dẫm chân lên luồng xử lý chính của Redisson.
        LocalDateTime thresholdTime = LocalDateTime.now().minusMinutes(12);
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(thresholdTime);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.warn("Phat hien {} don hang bi ket, tien hanh don dep...", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {
                // Hủy đơn và nhả vé [cite: 449]
                orderService.cancelIfStillPending(order.getId());
            } catch (Exception e) {
                // Bắt lỗi riêng từng đơn để vòng lặp không bị đứt gánh giữa chừng
                log.error("Loi khi don dep don hang voi ID {}: {}", order.getId(), e.getMessage());
            }
        }

        log.info("Hoan tat don dep.");
    }
}