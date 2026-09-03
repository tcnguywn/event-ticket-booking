package com.hdv.event_ticket_service.saga.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saga_instances", indexes = {
        @Index(name = "idx_saga_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "idx_saga_correlation", columnList = "correlation_id"),
        @Index(name = "idx_saga_business", columnList = "business_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "saga_id")
    private UUID sagaId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "business_id", nullable = false, length = 255)
    private String businessId;

    @Column(name = "saga_type", nullable = false, length = 100)
    private String sagaType;

    @Column(name = "current_step", nullable = false, length = 100)
    private String currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SagaStatus status = SagaStatus.STARTED;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
