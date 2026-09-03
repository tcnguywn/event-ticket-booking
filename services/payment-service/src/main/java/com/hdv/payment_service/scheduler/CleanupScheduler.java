package com.hdv.payment_service.scheduler;

//import com.hdv.payment_service.repository.IdempotencyRepository;
import com.hdv.payment_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CleanupScheduler {
//    private final IdempotencyRepository idempotencyKeyRepository;
    private final OutboxRepository outboxRepository;

//    // Dọn idempotency keys hết hạn – chạy mỗi 1 giờ
//    @Scheduled(fixedDelay = 3_600_000)
//    @Transactional
//    public void cleanExpiredIdempotencyKeys() {
//        long count = idempotencyKeyRepository.countExpiredKeys(LocalDateTime.now());
//        if (count == 0) return;
//
//        int deleted = idempotencyKeyRepository.deleteExpiredKeys(LocalDateTime.now());
//        log.info("Cleaned {} expired idempotency keys", deleted);
//    }

    // Dọn outbox events đã xử lý hoặc dead quá 7 ngày – chạy mỗi 24 giờ
    @Scheduled(fixedDelay = 86_400_000)
    @Transactional
    public void cleanOldOutboxEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int deleted = outboxRepository.deleteOldProcessedEvents(threshold);
        log.info("Cleaned {} old outbox events", deleted);
    }
}
