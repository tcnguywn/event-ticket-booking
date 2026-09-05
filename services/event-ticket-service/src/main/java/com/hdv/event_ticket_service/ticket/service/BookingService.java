package com.hdv.event_ticket_service.ticket.service;

import com.hdv.common.dto.ReleaseItemDto;
import com.hdv.event_ticket_service.ticket.domain.entity.BookingSeat;
import com.hdv.event_ticket_service.ticket.domain.entity.UserTicketBooking;
import com.hdv.event_ticket_service.ticket.domain.enums.BookingSeatStatus;
import com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus;
import com.hdv.event_ticket_service.ticket.repository.BookingSeatRepository;
import com.hdv.event_ticket_service.ticket.repository.UserTicketBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final UserTicketBookingRepository userTicketBookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatReservationService seatReservationService;
    private final InventoryService inventoryService;

    /**
     * Xác nhận chốt đơn vé khi nhận order.confirmed:
     * 1. Kiểm tra Idempotent (nếu đã CONFIRMED thì bỏ qua an toàn).
     * 2. Lấy danh sách ghế từ booking_seats (tránh N+1).
     * 3. Chốt trạng thái ghế sang BOOKED trên Redis & DB.
     * 4. Cập nhật booking status -> CONFIRMED.
     */
    @Transactional
    public void confirmBooking(UUID bookingGroupId) {
        if (bookingGroupId == null) return;

        List<UserTicketBooking> bookings = userTicketBookingRepository.findByBookingGroupId(bookingGroupId);
        if (bookings.isEmpty()) {
            log.warn("Không tìm thấy bookings cho bookingGroupId: {}", bookingGroupId);
            return;
        }

        // 1. Kiểm tra Idempotency: nếu tất cả đã CONFIRMED -> Không làm lại
        boolean allConfirmed = bookings.stream().allMatch(b -> b.getStatus() == BookingStatus.CONFIRMED);
        if (allConfirmed) {
            log.info("BookingGroup {} đã được CONFIRMED trước đó. Bỏ qua.", bookingGroupId);
            return;
        }

        UUID userId = bookings.get(0).getUserId();

        // 2. Lấy danh sách ghế thuộc booking group này (1 câu query batch)
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingGroupId(bookingGroupId);

        // 3. Nếu có ghế -> Chốt ghế vĩnh viễn
        if (!bookingSeats.isEmpty()) {
            Map<UUID, List<UUID>> seatsByEvent = bookingSeats.stream()
                    .collect(Collectors.groupingBy(
                            BookingSeat::getEventId,
                            Collectors.mapping(BookingSeat::getSeatId, Collectors.toList())
                    ));

            for (Map.Entry<UUID, List<UUID>> entry : seatsByEvent.entrySet()) {
                seatReservationService.confirmSeats(entry.getKey(), entry.getValue(), userId);
            }

            for (BookingSeat bs : bookingSeats) {
                bs.setStatus(BookingSeatStatus.BOOKED);
            }
            bookingSeatRepository.saveAll(bookingSeats);
            log.info("Đã chốt {} ghế thành công cho bookingGroup {}", bookingSeats.size(), bookingGroupId);
        }

        // 4. Cập nhật trạng thái booking sang CONFIRMED
        for (UserTicketBooking booking : bookings) {
            booking.setStatus(BookingStatus.CONFIRMED);
        }
        userTicketBookingRepository.saveAll(bookings);

        log.info("Xác nhận hoàn tất đặt vé cho BookingGroup: {}", bookingGroupId);
    }

    /**
     * Nhả ghế và hoàn kho khi nhận ticket.release:
     * 1. Kiểm tra Idempotent (chống hoàn kho nhiều lần).
     * 2. Nhả ghế trước (chỉ nhả ghế đúng reservation của user).
     * 3. Hoàn kho Redis chỉ cho các vé khu vực/vé đứng (vé không có số ghế).
     * 4. Cập nhật booking status -> CANCELLED, seat status -> RELEASED.
     */
    @Transactional
    public void releaseBooking(UUID bookingGroupId, List<ReleaseItemDto> items) {
        if (bookingGroupId == null) return;

        List<UserTicketBooking> bookings = userTicketBookingRepository.findByBookingGroupId(bookingGroupId);
        if (bookings.isEmpty()) {
            log.warn("Không tìm thấy bookings cho bookingGroupId: {}", bookingGroupId);
            return;
        }

        // 1. Kiểm tra Idempotency: nếu tất cả đã CANCELLED -> Không hoàn kho lại
        boolean allCancelled = bookings.stream().allMatch(b -> b.getStatus() == BookingStatus.CANCELLED);
        if (allCancelled) {
            log.info("BookingGroup {} đã được CANCELLED trước đó. Bỏ qua hoàn kho trùng lặp.", bookingGroupId);
            return;
        }

        UUID userId = bookings.get(0).getUserId();

        // 2. Lấy danh sách ghế thuộc booking group này (1 câu query batch)
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingGroupId(bookingGroupId);

        // 3. Nhả ghế an toàn (nếu có ghế)
        Set<UUID> ticketTypeIdsWithSeats = new HashSet<>();
        if (!bookingSeats.isEmpty()) {
            Map<UUID, List<UUID>> seatsByEvent = bookingSeats.stream()
                    .collect(Collectors.groupingBy(
                            BookingSeat::getEventId,
                            Collectors.mapping(BookingSeat::getSeatId, Collectors.toList())
                    ));

            for (Map.Entry<UUID, List<UUID>> entry : seatsByEvent.entrySet()) {
                seatReservationService.releaseSeats(entry.getKey(), entry.getValue(), userId);
            }

            for (BookingSeat bs : bookingSeats) {
                bs.setStatus(BookingSeatStatus.RELEASED);
            }
            bookingSeatRepository.saveAll(bookingSeats);
            log.info("Đã nhả {} ghế thành công cho bookingGroup {}", bookingSeats.size(), bookingGroupId);

            // Xác định các loại vé đã dùng ghế để không hoàn kho Redis trùng
            Map<UUID, UUID> bookingToTicketType = bookings.stream()
                    .collect(Collectors.toMap(UserTicketBooking::getId, UserTicketBooking::getTicketTypeId));
            for (BookingSeat bs : bookingSeats) {
                UUID ttId = bookingToTicketType.get(bs.getBookingId());
                if (ttId != null) {
                    ticketTypeIdsWithSeats.add(ttId);
                }
            }
        }

        // 4. Hoàn kho Redis cho vé đứng / khu vực (không gắn số ghế)
        if (items != null && !items.isEmpty()) {
            for (ReleaseItemDto item : items) {
                UUID ticketTypeId = item.getTicketTypeId();
                // Chỉ hoàn kho Redis nếu loại vé này KHÔNG quản lý theo số ghế riêng lẻ
                if (!ticketTypeIdsWithSeats.contains(ticketTypeId)) {
                    inventoryService.incrementStock(ticketTypeId.toString(), item.getQuantity());
                    log.info("Đã hoàn {} vé về kho Redis cho ticketTypeId: {}", item.getQuantity(), ticketTypeId);
                }
            }
        }

        // 5. Cập nhật trạng thái booking sang CANCELLED
        for (UserTicketBooking booking : bookings) {
            booking.setStatus(BookingStatus.CANCELLED);
        }
        userTicketBookingRepository.saveAll(bookings);

        log.info("Hoàn tất bồi hoàn (Release) cho BookingGroup: {}", bookingGroupId);
    }
}
