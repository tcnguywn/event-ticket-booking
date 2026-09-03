package com.hdv.event_ticket_service.ticket.repository;

import com.hdv.event_ticket_service.ticket.domain.entity.UserTicketBooking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTicketBookingRepository extends JpaRepository<UserTicketBooking, UUID> {

    /**
     * Đếm tổng số vé user đã đặt cho 1 SỰ KIỆN (cộng dồn tất cả loại vé).
     */
    @Query("SELECT COALESCE(SUM(u.quantity), 0) FROM UserTicketBooking u " +
            "WHERE u.userId = :userId AND u.eventId = :eventId " +
            "AND u.status IN (com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.PENDING, " +
            "com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.CONFIRMED, " +
            "com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.SENT)")
    int countBookedByUserAndEvent(@Param("userId") UUID userId, @Param("eventId") UUID eventId);

    /**
     * Tìm tất cả booking thuộc cùng 1 nhóm (bookingGroupId) - dùng cho release vé
     */
    List<UserTicketBooking> findByBookingGroupId(UUID bookingGroupId);

    /**
     * Tìm booking theo idempotencyKey - dùng cho release vé từ order-service
     */
    List<UserTicketBooking> findByIdempotencyKey(UUID idempotencyKey);
}
