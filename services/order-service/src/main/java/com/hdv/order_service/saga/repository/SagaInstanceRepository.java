package com.hdv.order_service.saga.repository;

import com.hdv.order_service.saga.domain.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, UUID> {
    Optional<SagaInstance> findByCorrelationId(UUID correlationId);
    Optional<SagaInstance> findByBusinessId(String businessId);
}
