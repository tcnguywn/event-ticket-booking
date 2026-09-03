package com.hdv.event_ticket_service.outbox.repository;

import com.hdv.event_ticket_service.outbox.domain.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    @Query(value = """
            SELECT * FROM outbox
            WHERE (status IN ('PENDING', 'FAILED') AND (next_retry_at IS NULL OR next_retry_at <= :now))
               OR (status = 'PROCESSING' AND lease_until < :now)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Outbox> findAndLockAvailableEvents(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Outbox o SET o.status = com.hdv.event_ticket_service.outbox.domain.OutboxStatus.SENT, " +
            "o.processedAt = :now, o.lockedBy = null, o.lockedAt = null, o.leaseUntil = null " +
            "WHERE o.id = :id")
    int markAsSent(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Outbox o SET o.status = com.hdv.event_ticket_service.outbox.domain.OutboxStatus.PROCESSING, " +
            "o.lockedBy = :lockedBy, o.lockedAt = :now, o.leaseUntil = :leaseUntil " +
            "WHERE o.id IN :ids")
    int markAsProcessing(@Param("ids") List<UUID> ids, @Param("lockedBy") String lockedBy,
                         @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
}
