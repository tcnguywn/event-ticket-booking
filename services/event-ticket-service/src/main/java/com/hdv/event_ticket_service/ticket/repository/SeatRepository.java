package com.hdv.event_ticket_service.ticket.repository;

import com.hdv.event_ticket_service.ticket.domain.entity.Seat;
import com.hdv.event_ticket_service.ticket.domain.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByEventId(UUID eventId);

    List<Seat> findByEventIdAndTicketTypeId(UUID eventId, UUID ticketTypeId);

    List<Seat> findByEventIdAndStatus(UUID eventId, SeatStatus status);
}
