package com.hdv.event_ticket_service.outbox.service;

import com.hdv.event_ticket_service.outbox.OutboxPublisher;
import com.hdv.event_ticket_service.outbox.domain.Outbox;
import com.hdv.event_ticket_service.outbox.domain.OutboxStatus;
import com.hdv.event_ticket_service.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;

    /**
     * Ghi nhận Outbox vào DB trong cùng transaction hiện tại,
     * đồng thời đăng ký afterCommit hook để bắn Kafka siêu tốc (Fast-Path).
     */
    public Outbox saveOutboxAndRegisterFastPath(Outbox outbox) {
        Outbox saved = outboxRepository.save(outbox);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> tryPublishFastPath(saved));
                }
            });
        } else {
            // Trường hợp không có active transaction (fallback)
            CompletableFuture.runAsync(() -> tryPublishFastPath(saved));
        }

        return saved;
    }

    /**
     * Fast-path: Thử bắn Kafka ngay lập tức sau khi DB commit.
     */
    public void tryPublishFastPath(Outbox outbox) {
        try {
            log.info("Triggering AFTER_COMMIT Fast-Path for outbox eventId: {}, topic: {}", outbox.getEventId(), outbox.getTopic());
            outboxPublisher.publish(outbox.getTopic(), outbox.getEventId().toString(), outbox.getPayload());
            markAsSent(outbox.getId());
            log.info("Fast-path publish SUCCESS for outbox eventId: {}", outbox.getEventId());
        } catch (Exception e) {
            log.warn("Fast-path publish failed for eventId: {}. Event remains PENDING for Poller fallback. Error: {}",
                    outbox.getEventId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSent(UUID outboxId) {
        outboxRepository.markAsSent(outboxId, LocalDateTime.now());
    }
}
