package com.hdv.event_ticket_service.ticket.repository;

import com.hdv.event_ticket_service.ticket.domain.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, UUID> {

    List<BookingSeat> findByBookingGroupId(UUID bookingGroupId);

    List<BookingSeat> findByBookingIdIn(List<UUID> bookingIds);

    List<BookingSeat> findBySeatId(UUID seatId);
}
