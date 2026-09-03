package com.hdv.order_service.expiry;

import com.hdv.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryConsumer implements ApplicationRunner {

    private final RBlockingQueue<String> orderExpiryQueue;
    private final OrderService orderService;

    @Override
    public void run(ApplicationArguments args) {
        // Sử dụng Java 21 Virtual Thread để chạy consumer ngầm, rất nhẹ và không tốn thread của Tomcat
        Thread.ofVirtual().start(() -> {
            log.info("Khởi động luồng lắng nghe đơn hàng hết hạn từ Redisson...");

            while (true) {
                try {
                    // Lệnh take() sẽ block thread lại (không tốn CPU) cho đến khi có đơn hàng rớt vào
                    String orderId = orderExpiryQueue.take();
                    log.info("Nhận được đơn hàng hết hạn 10 phút từ Redis: {}", orderId);

                    // Tiến hành hủy đơn (nếu nó vẫn đang PENDING)
                    orderService.cancelIfStillPending(UUID.fromString(orderId));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Tiến trình lắng nghe đơn hàng hết hạn bị gián đoạn", e);
                    break;
                } catch (Exception e) {
                    // Catch tổng quát để nếu có lỗi xử lý 1 đơn, vòng lặp while(true) vẫn không bị chết
                    log.error("Lỗi khi xử lý đơn hàng hết hạn: {}", e.getMessage(), e);
                }
            }
        });
    }
}