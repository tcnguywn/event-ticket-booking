package com.hdv.event_ticket_service.common;

import com.hdv.event_ticket_service.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public abstract class BaseService {

    /**
     * Lấy User ID đã được xác thực an toàn từ Gateway
     */
    protected UUID getCurrentUserId(HttpServletRequest request) {
        String userIdStr = request.getHeader("X-User-Id");
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new AppException("Unauthenticated: Missing user identity header", HttpStatus.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(userIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException("Invalid user ID format in request header", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Lấy Email user từ Gateway
     */
    protected String getCurrentUserEmail(HttpServletRequest request) {
        return request.getHeader("X-User-Email");
    }

    /**
     * Lấy Role user từ Gateway (USER, ORGANIZER, ADMIN)
     */
    protected String getCurrentUserRole(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        return (role != null) ? role.trim().toUpperCase() : "USER";
    }

    /**
     * KIỂM TRA QUYỀN SỞ HỮU TÀI NGUYÊN (CHỐNG LỖ HỔNG IDOR):
     * Chỉ cho phép chính chủ nhân tài nguyên hoặc ADMIN truy cập.
     */
    protected void validateResourceOwnership(UUID resourceOwnerId, HttpServletRequest request) {
        if (resourceOwnerId == null) {
            return;
        }

        String role = getCurrentUserRole(request);
        if ("ADMIN".equalsIgnoreCase(role)) {
            return; // Quản trị viên có toàn quyền kiểm tra
        }

        UUID currentUserId = getCurrentUserId(request);
        if (!currentUserId.equals(resourceOwnerId)) {
            throw new AppException("Access Denied: Bạn không có quyền truy cập hoặc chỉnh sửa tài nguyên này!", HttpStatus.FORBIDDEN);
        }
    }
}
