package com.hdv.event_ticket_service.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO processed_events (id, event_id, consumer_name, processed_at) " +
            "VALUES (gen_random_uuid(), :eventId, :consumerName, CURRENT_TIMESTAMP) " +
            "ON CONFLICT ON CONSTRAINT uk_event_consumer DO NOTHING", nativeQuery = true)
    int insertIfNotExists(@Param("eventId") UUID eventId, @Param("consumerName") String consumerName);
}
