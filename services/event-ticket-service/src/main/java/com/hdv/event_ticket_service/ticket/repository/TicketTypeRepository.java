package com.hdv.event_ticket_service.ticket.repository;

import com.hdv.event_ticket_service.ticket.domain.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {
    List<TicketType> findByEventId(UUID eventId);
}
