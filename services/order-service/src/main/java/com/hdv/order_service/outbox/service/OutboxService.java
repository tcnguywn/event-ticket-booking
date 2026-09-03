package com.hdv.order_service.outbox.service;

import com.hdv.order_service.outbox.OutboxPublisher;
import com.hdv.order_service.outbox.domain.Outbox;
import com.hdv.order_service.outbox.repository.OutboxRepository;
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
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
            CompletableFuture.runAsync(() -> tryPublishFastPath(saved));
        }

        return saved;
    }

    public void tryPublishFastPath(Outbox outbox) {
        try {
            log.info("Triggering AFTER_COMMIT Fast-Path for order outbox eventId: {}, topic: {}", outbox.getEventId(), outbox.getTopic());
            outboxPublisher.publish(outbox.getTopic(), outbox.getPayload());
            markAsSent(outbox.getId());
            log.info("Fast-path publish SUCCESS for order outbox eventId: {}", outbox.getEventId());
        } catch (Exception e) {
            log.warn("Fast-path publish failed for eventId: {}. Event remains PENDING for Poller fallback. Error: {}",
                    outbox.getEventId(), e.getMessage());
        }
    }

    public void markAsSent(UUID outboxId) {
        transactionTemplate.executeWithoutResult(status -> {
            outboxRepository.markAsSent(outboxId, LocalDateTime.now());
        });
    }
}
