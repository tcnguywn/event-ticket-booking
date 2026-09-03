package com.hdv.event_ticket_service.checkin.controller;

import com.hdv.event_ticket_service.checkin.dto.CheckInRequest;
import com.hdv.event_ticket_service.checkin.dto.CheckInResponse;
import com.hdv.event_ticket_service.checkin.service.CheckInService;
import com.hdv.event_ticket_service.exception.AppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/check-in")
    public ResponseEntity<CheckInResponse> checkIn(
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role,
            @Valid @RequestBody CheckInRequest request) {

        // Chỉ nhân viên soát vé (STAFF) hoặc ADMIN mới được phép quét vé
        if (!"STAFF".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            throw new AppException("Chỉ nhân viên soát vé (STAFF) mới có quyền thực hiện check-in", HttpStatus.FORBIDDEN);
        }

        CheckInResponse response = checkInService.processCheckIn(request);
        return ResponseEntity.ok(response);
    }
}
