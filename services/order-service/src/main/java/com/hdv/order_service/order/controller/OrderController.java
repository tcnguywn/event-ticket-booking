package com.hdv.order_service.order.controller;

import com.hdv.order_service.order.domain.dto.ApiResponse;
import com.hdv.order_service.order.domain.dto.OrderResponse;
import com.hdv.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

        private final OrderService orderService;

        /**
         * GET /api/orders/{id} — Xem trạng thái của một đơn hàng cụ thể
         */
        @GetMapping("/{id}")
        public ApiResponse<OrderResponse> getOrderById(
                        @PathVariable UUID id,
                        @RequestHeader("X-User-Id") String userId) {
                
                OrderResponse orderResponse = orderService.getOrderByIdAndUserId(id, UUID.fromString(userId));

                return ApiResponse.<OrderResponse>builder()
                                .status(HttpStatus.OK.value())
                                .message("Lấy thông tin đơn hàng thành công")
                                .body(orderResponse)
                                .build();
        }

        /**
         * GET /api/orders — Xem lịch sử đặt vé của user hiện tại
         */
        @GetMapping
        public ApiResponse<List<OrderResponse>> getOrders(
                        @RequestHeader("X-User-Id") String userId) {

                List<OrderResponse> orders = orderService.getOrdersByUserId(UUID.fromString(userId));

                return ApiResponse.<List<OrderResponse>>builder()
                                .status(HttpStatus.OK.value())
                                .message("Lấy danh sách lịch sử đơn hàng thành công")
                                .body(orders)
                                .build();
        }

        /**
         * GET /api/orders/my — Xem lịch sử đặt vé của user hiện tại
         */
        @GetMapping("/my")
        public ApiResponse<List<OrderResponse>> getMyOrders(
                        @RequestHeader("X-User-Id") String userId) {
                return getOrders(userId);
        }

        /**
         * POST /api/orders/{id}/cancel — Hủy đơn hàng trước khi thanh toán
         */
        @PostMapping("/{id}/cancel")
        public ApiResponse<String> cancelOrder(
                        @PathVariable UUID id,
                        @RequestHeader("X-User-Id") String userId) {
                
                // Check quyen so huu => call get roi thoi
                orderService.getOrderByIdAndUserId(id, UUID.fromString(userId));
                orderService.cancelIfStillPending(id);

                return ApiResponse.<String>builder()
                                .status(HttpStatus.OK.value())
                                .message("Hủy đơn hàng thành công")
                                .body("CANCELLED")
                                .build();
        }
}