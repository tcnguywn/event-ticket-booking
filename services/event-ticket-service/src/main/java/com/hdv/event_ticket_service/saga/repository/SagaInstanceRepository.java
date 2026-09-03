package com.hdv.event_ticket_service.saga.repository;

import com.hdv.event_ticket_service.saga.domain.SagaInstance;
import com.hdv.event_ticket_service.saga.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByCorrelationId(UUID correlationId);
    Optional<SagaInstance> findByBusinessId(String businessId);
}
