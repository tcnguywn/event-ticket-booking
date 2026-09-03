package com.hdv.event_ticket_service.event.repository;

import com.hdv.event_ticket_service.event.domain.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByOrganizerId(UUID organizerId);
}
