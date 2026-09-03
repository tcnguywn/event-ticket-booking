package com.hdv.event_ticket_service.ticket.domain.entity;

import com.hdv.event_ticket_service.ticket.domain.enums.BookingSeatStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_seats", indexes = {
        @Index(name = "idx_booking_seats_group", columnList = "booking_group_id"),
        @Index(name = "idx_booking_seats_booking", columnList = "booking_id"),
        @Index(name = "idx_booking_seats_seat", columnList = "seat_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "booking_group_id", nullable = false)
    private UUID bookingGroupId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingSeatStatus status = BookingSeatStatus.HOLD;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
