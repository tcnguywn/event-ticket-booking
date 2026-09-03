package com.hdv.event_ticket_service.outbox;

import com.hdv.event_ticket_service.outbox.domain.Outbox;
import com.hdv.event_ticket_service.outbox.domain.OutboxStatus;
import com.hdv.event_ticket_service.outbox.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher outboxPublisher;
    private final TransactionTemplate transactionTemplate;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    private static final int BATCH_SIZE = 50;
    private static final int LEASE_SECONDS = 30;

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "OutboxPoller_processOutbox", lockAtLeastFor = "1s", lockAtMostFor = "10s")
    public void processOutbox() {
        // PHASE 1: CLAIM BATCH (Short DB Transaction)
        List<Outbox> claimedEvents = claimAvailableEvents();
        if (claimedEvents == null || claimedEvents.isEmpty()) {
            return;
        }

        log.info("[OutboxPoller-{}] Claimed {} events for fallback publishing", instanceId, claimedEvents.size());

        // PHASE 2 & 3: PUBLISH OUTSIDE DB TRANSACTION & COMPLETE INDIVIDUALLY
        for (Outbox event : claimedEvents) {
            try {
                // Publish Kafka without holding DB Transaction
                outboxPublisher.publish(
                        event.getTopic(),
                        event.getEventId().toString(),
                        event.getPayload()
                );

                // Update SENT in short isolated transaction
                markEventSuccess(event.getId());
            } catch (Exception e) {
                log.error("[OutboxPoller-{}] Failed to publish eventId: {}, error: {}", instanceId, event.getEventId(), e.getMessage());
                markEventFailure(event.getId(), event.getRetryCount(), event.getMaxRetry(), e.getMessage());
            }
        }
    }

    public List<Outbox> claimAvailableEvents() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();
            List<Outbox> available = outboxRepository.findAndLockAvailableEvents(now, BATCH_SIZE);
            if (available.isEmpty()) {
                return List.of();
            }

            List<UUID> ids = available.stream().map(Outbox::getId).toList();
            LocalDateTime leaseUntil = now.plusSeconds(LEASE_SECONDS);
            outboxRepository.markAsProcessing(ids, instanceId, now, leaseUntil);
            return available;
        });
    }

    public void markEventSuccess(UUID eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            outboxRepository.markAsSent(eventId, LocalDateTime.now());
        });
    }

    public void markEventFailure(UUID eventId, int currentRetry, int maxRetry, String errorMsg) {
        transactionTemplate.executeWithoutResult(status -> {
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
                    long delaySeconds = (long) Math.pow(2, Math.min(newRetry, 6)) * 2; // 4s, 8s, 16s, 32s, 64s, 128s
                    outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
                }
                outboxRepository.save(outbox);
            });
        });
    }
}
