package com.hdv.payment_service.scheduler;

import com.hdv.payment_service.enums.OutboxStatus;
import com.hdv.payment_service.kafka.Producer;
import com.hdv.payment_service.model.Outbox;
import com.hdv.payment_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {

    private final OutboxRepository outboxRepository;
    private final Producer producer;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private static final int BATCH_SIZE = 50;
    private static final int LEASE_SECONDS = 30;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        // PHASE 1: CLAIM BATCH (Short DB Transaction)
        List<Outbox> claimedEvents = claimAvailableEvents();
        if (claimedEvents.isEmpty()) {
            return;
        }

        log.info("[OutboxPoller-Payment-{}] Claimed {} events for fallback publishing", instanceId, claimedEvents.size());

        // PHASE 2 & 3: PUBLISH OUTSIDE DB TRANSACTION & COMPLETE INDIVIDUALLY
        for (Outbox event : claimedEvents) {
            try {
                // Publish Kafka without holding DB Transaction
                producer.send(event.getTopic(), event.getAggregateId(), event.getPayload());

                // Update SENT in short isolated transaction
                markEventSuccess(event.getId());
            } catch (Exception e) {
                log.error("[OutboxPoller-Payment-{}] Failed to publish eventId: {}, error: {}", instanceId, event.getEventId(), e.getMessage());
                markEventFailure(event.getId(), event.getRetryCount(), event.getMaxRetry(), e.getMessage());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Outbox> claimAvailableEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Outbox> available = outboxRepository.findAndLockAvailableEvents(now, BATCH_SIZE);
        if (available.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = available.stream().map(Outbox::getId).toList();
        LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);
        outboxRepository.markAsProcessing(ids, instanceId, now, leaseUntil);
        return available;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventSuccess(UUID eventId) {
        outboxRepository.markAsSent(eventId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEventFailure(UUID eventId, int currentRetry, int maxRetry, String errorMsg) {
        outboxRepository.findById(eventId).ifPresent(outbox -> {
            int newRetry = currentRetry + 1;
            outbox.setRetryCount(newRetry);
            outbox.setLastError(errorMsg);
            outbox.setLockedBy(null);
            outbox.setLockedAt(null);
            outbox.setLeaseUntil(null);

            if (newRetry >= maxRetry) {
                outbox.setStatus(OutboxStatus.FAILED);
            } else {
                outbox.setStatus(OutboxStatus.PENDING);
                long delaySeconds = (long) Math.pow(2, Math.min(newRetry, 6)) * 2;
                outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
            }
            outboxRepository.save(outbox);
        });
    }
}
