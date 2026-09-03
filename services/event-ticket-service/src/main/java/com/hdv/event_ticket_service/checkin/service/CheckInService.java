package com.hdv.event_ticket_service.checkin.service;

import com.hdv.event_ticket_service.checkin.dto.CheckInRequest;
import com.hdv.event_ticket_service.checkin.dto.CheckInResponse;
import com.hdv.event_ticket_service.ticket.domain.entity.UserTicketBooking;
import com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus;
import com.hdv.event_ticket_service.ticket.repository.UserTicketBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final UserTicketBookingRepository userTicketBookingRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.ticket.crypto-secret:SecretConcertKeyForCheckIn2026!}")
    private String cryptoSecret;

    private static final String CHECKIN_LOCK_PREFIX = "checkin:";
    private static final Duration CHECKIN_TTL = Duration.ofDays(2);

    /**
     * Sinh chữ ký HMAC-SHA256 cho vé để in vào QR code
     */
    public String generateTicketSignature(UUID ticketId, UUID eventId) {
        try {
            String data = ticketId.toString() + ":" + eventId.toString();
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi sinh chữ ký vé", e);
        }
    }

    /**
     * Xác minh vé và thực hiện Check-in tại cổng sự kiện:
     * 1. Xác thực chữ ký số HMAC (Xác minh vé thật/giả tức thì < 0.1ms kể cả khi mất mạng).
     * 2. Ngăn chặn Double Check-in bằng Redis SET NX (Nếu 2 cửa cùng quét cùng lúc).
     * 3. Cập nhật trạng thái vé trong DB.
     */
    @Transactional
    public CheckInResponse processCheckIn(CheckInRequest request) {
        UUID ticketId = request.getTicketId();
        UUID eventId = request.getEventId();

        // 1. Kiểm tra chữ ký mật mã
        String expectedSignature = generateTicketSignature(ticketId, eventId);
        if (!expectedSignature.equalsIgnoreCase(request.getSignature())) {
            log.warn("CẢNH BÁO VÉ GIẢ: Chữ ký không hợp lệ cho ticketId: {}", ticketId);
            return CheckInResponse.builder()
                    .status("INVALID_SIGNATURE")
                    .message("Vé không hợp lệ hoặc đã bị chỉnh sửa mã QR!")
                    .ticketId(ticketId)
                    .build();
        }

        // 2. Chống quét trùng tại các cổng (Double Check-in) qua Redis SET NX
        String redisKey = CHECKIN_LOCK_PREFIX + ticketId;
        String nowStr = LocalDateTime.now().toString();
        Boolean isFirstScan = redisTemplate.opsForValue().setIfAbsent(redisKey, nowStr, CHECKIN_TTL);

        if (Boolean.FALSE.equals(isFirstScan)) {
            String scannedTime = redisTemplate.opsForValue().get(redisKey);
            log.warn("VÉ ĐÃ SỬ DỤNG: Ticket {} đã được quét trước đó vào lúc {}", ticketId, scannedTime);
            return CheckInResponse.builder()
                    .status("ALREADY_CHECKED_IN")
                    .message("Vé này đã được quét vào cửa lúc: " + scannedTime)
                    .ticketId(ticketId)
                    .build();
        }

        // 3. Cập nhật trạng thái vé trong cơ sở dữ liệu
        UserTicketBooking booking = userTicketBookingRepository.findById(ticketId).orElse(null);
        if (booking != null) {
            booking.setStatus(BookingStatus.CONFIRMED); // Hoặc CHECKED_IN
            userTicketBookingRepository.save(booking);
        }

        log.info("Check-in thành công cho vé: {}", ticketId);
        return CheckInResponse.builder()
                .status("SUCCESS")
                .message("Soát vé thành công! Mời khách vào khán đài.")
                .ticketId(ticketId)
                .checkInTime(LocalDateTime.now())
                .build();
    }
}
