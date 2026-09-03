package com.hdv.event_ticket_service.ticket.service;

import com.hdv.event_ticket_service.exception.AppException;
import com.hdv.event_ticket_service.ticket.domain.entity.Seat;
import com.hdv.event_ticket_service.ticket.domain.enums.SeatStatus;
import com.hdv.event_ticket_service.ticket.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatReservationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SeatRepository seatRepository;

    private static final String SEAT_LOCK_PREFIX = "seat_lock:";
    private static final Duration SEAT_HOLD_DURATION = Duration.ofMinutes(10);

    /**
     * Khóa danh sách ghế nguyên tử trên Redis trong 10 phút.
     * Nếu có bất kỳ ghế nào đã bị giữ hoặc bán -> Rollback toàn bộ ghế vừa khóa và ném lỗi 409 Conflict.
     */
    @Transactional
    public List<Seat> lockSeats(UUID eventId, List<UUID> seatIds, UUID userId) {
        List<UUID> lockedRedisSeats = new ArrayList<>();

        try {
            for (UUID seatId : seatIds) {
                String key = SEAT_LOCK_PREFIX + eventId + ":" + seatId;
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, userId.toString(), SEAT_HOLD_DURATION);

                if (Boolean.FALSE.equals(acquired)) {
                    throw new AppException("Ghế " + seatId + " đang được giữ chỗ hoặc đã bán. Vui lòng chọn ghế khác!", HttpStatus.CONFLICT);
                }
                lockedRedisSeats.add(seatId);
            }

            // Cập nhật trạng thái ghế trong DB sang HOLD
            List<Seat> seats = seatRepository.findAllById(seatIds);
            for (Seat seat : seats) {
                if (seat.getStatus() != SeatStatus.FREE) {
                    throw new AppException("Ghế " + seat.getSeatRow() + seat.getSeatNumber() + " không khả dụng!", HttpStatus.CONFLICT);
                }
                seat.setStatus(SeatStatus.HOLD);
                seat.setLockedByUserId(userId);
                seat.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            }

            seatRepository.saveAll(seats);
            log.info("Locked {} seats successfully for user {} in event {}", seatIds.size(), userId, eventId);
            return seats;

        } catch (Exception ex) {
            // Rollback các lock Redis đã cấp bằng Batch delete
            if (!lockedRedisSeats.isEmpty()) {
                List<String> keys = lockedRedisSeats.stream()
                        .map(id -> SEAT_LOCK_PREFIX + eventId + ":" + id)
                        .toList();
                redisTemplate.delete(keys);
            }
            throw ex;
        }
    }

    /**
     * Nhả ghế khi hủy đơn hàng hoặc hết hạn 10 phút.
     * Đảm bảo Idempotent và KHÔNG release nhầm reservation của user khác.
     */
    @Transactional
    public void releaseSeats(UUID eventId, List<UUID> seatIds, UUID userId) {
        if (seatIds == null || seatIds.isEmpty()) return;

        // 1. Xóa Redis Lock trong 1 batch duy nhất
        List<String> keys = seatIds.stream()
                .map(id -> SEAT_LOCK_PREFIX + eventId + ":" + id)
                .toList();
        redisTemplate.delete(keys);

        // 2. Cập nhật DB: Chỉ nhả các ghế thuộc đúng reservation của user này và đang ở trạng thái HOLD
        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> seatsToUpdate = new ArrayList<>();

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.HOLD) {
                // Kiểm tra an toàn: nếu truyền userId thì chỉ release nếu đúng user giữ ghế
                if (userId == null || userId.equals(seat.getLockedByUserId())) {
                    seat.setStatus(SeatStatus.FREE);
                    seat.setLockedByUserId(null);
                    seat.setLockedUntil(null);
                    seatsToUpdate.add(seat);
                } else {
                    log.warn("Bỏ qua release ghế {} vì lockedByUserId ({}) khác userId ({})",
                            seat.getId(), seat.getLockedByUserId(), userId);
                }
            }
        }

        if (!seatsToUpdate.isEmpty()) {
            seatRepository.saveAll(seatsToUpdate);
            log.info("Released {} seats successfully for event {}", seatsToUpdate.size(), eventId);
        }
    }

    // Overload cho backward compatibility
    @Transactional
    public void releaseSeats(UUID eventId, List<UUID> seatIds) {
        releaseSeats(eventId, seatIds, null);
    }

    /**
     * Xác nhận chốt ghế vĩnh viễn khi thanh toán thành công.
     * Đảm bảo Idempotent (gọi nhiều lần an toàn).
     */
    @Transactional
    public void confirmSeats(UUID eventId, List<UUID> seatIds, UUID userId) {
        if (seatIds == null || seatIds.isEmpty()) return;

        // 1. Xóa Redis lock tạm thời bằng Batch delete
        List<String> keys = seatIds.stream()
                .map(id -> SEAT_LOCK_PREFIX + eventId + ":" + id)
                .toList();
        redisTemplate.delete(keys);

        // 2. Chuyển trạng thái sang BOOKED
        List<Seat> seats = seatRepository.findAllById(seatIds);
        List<Seat> seatsToUpdate = new ArrayList<>();

        for (Seat seat : seats) {
            // Nếu là HOLD hoặc đã BOOKED thì chốt BOOKED an toàn
            if (seat.getStatus() == SeatStatus.HOLD || seat.getStatus() == SeatStatus.BOOKED) {
                seat.setStatus(SeatStatus.BOOKED);
                seat.setLockedUntil(null);
                seatsToUpdate.add(seat);
            }
        }

        if (!seatsToUpdate.isEmpty()) {
            seatRepository.saveAll(seatsToUpdate);
            log.info("Confirmed {} seats for event {}", seatsToUpdate.size(), eventId);
        }
    }

    // Overload cho backward compatibility
    @Transactional
    public void confirmSeats(UUID eventId, List<UUID> seatIds) {
        confirmSeats(eventId, seatIds, null);
    }
}
