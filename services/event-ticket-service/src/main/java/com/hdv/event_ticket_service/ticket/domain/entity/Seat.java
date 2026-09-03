package com.hdv.event_ticket_service.ticket.domain.entity;

import com.hdv.event_ticket_service.ticket.domain.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seat_event_ticket_type", columnList = "event_id, ticket_type_id"),
        @Index(name = "idx_seat_unique", columnList = "event_id, seat_row, seat_number", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "seat_row", nullable = false, length = 16)
    private String seatRow; // Ví dụ: "A", "B", "VIP"

    @Column(name = "seat_number", nullable = false, length = 16)
    private String seatNumber; // Ví dụ: "01", "12"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private SeatStatus status = SeatStatus.FREE;

    @Column(name = "locked_by_user_id")
    private UUID lockedByUserId;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
