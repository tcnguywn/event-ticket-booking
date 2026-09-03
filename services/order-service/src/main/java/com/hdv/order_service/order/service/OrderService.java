package com.hdv.order_service.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.order_service.exception.AppException;
import com.hdv.order_service.exception.ErrorCode;
import com.hdv.order_service.order.domain.dto.OrderResponse;
import com.hdv.order_service.order.domain.entity.Order;
import com.hdv.order_service.order.domain.entity.OrderItem;
import com.hdv.order_service.order.domain.enums.OrderStatus;
import com.hdv.order_service.order.repository.OrderRepository;
import com.hdv.order_service.outbox.domain.Outbox;
import com.hdv.order_service.outbox.domain.OutboxStatus;
import com.hdv.order_service.outbox.repository.OutboxRepository;
import com.hdv.order_service.outbox.service.OutboxService;
import com.hdv.order_service.saga.domain.SagaInstance;
import com.hdv.order_service.saga.domain.SagaStatus;
import com.hdv.order_service.saga.repository.SagaInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hdv.common.dto.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final RDelayedQueue<String> orderDelayedQueue;
    private final ObjectMapper objectMapper;

    /**
     * DTO nội bộ để truyền dữ liệu item từ Kafka Consumer vào Service
     */
    public record OrderItemData(UUID ticketTypeId, String ticketTypeName, int quantity, long price) {}

    @Transactional(readOnly = true)
    public OrderResponse getOrderByIdAndUserId(UUID orderId, UUID userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Bạn không có quyền xem đơn hàng này");
        }

        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(UUID userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    /**
     * Hàm này được gọi khi nhận Kafka message "ticket.reserved" từ event-ticket-service
     */
    @Transactional
    public void createOrder(UUID eventId, UUID userId, String email, Long totalPrice,
                            List<OrderItemData> items, UUID bookingGroupId, UUID idempotencyKey) {
        // 1. Lưu Order vào Database với trạng thái PENDING
        Order order = Order.builder()
                .eventId(eventId)
                .userId(userId)
                .email(email)
                .totalPrice(totalPrice)
                .status(OrderStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .bookingGroupId(bookingGroupId)
                .build();

        // 2. Tạo các OrderItem từ danh sách items
        for (OrderItemData itemData : items) {
            OrderItem orderItem = OrderItem.builder()
                    .ticketTypeId(itemData.ticketTypeId())
                    .ticketTypeName(itemData.ticketTypeName())
                    .quantity(itemData.quantity())
                    .price(itemData.price())
                    .build();
            order.addItem(orderItem);
        }

        orderRepository.save(order);

        // 3. Ghi nhận Saga State
        com.hdv.order_service.saga.domain.SagaInstance saga = com.hdv.order_service.saga.domain.SagaInstance.builder()
                .correlationId(idempotencyKey)
                .businessId(order.getId().toString())
                .sagaType("TICKET_BOOKING_SAGA")
                .currentStep("CREATE_ORDER")
                .status(com.hdv.order_service.saga.domain.SagaStatus.PROCESSING)
                .build();
        sagaInstanceRepository.save(saga);

        // 4. Ghi sự kiện "order.created" vào Outbox và kích hoạt Fast-Path
        String payload = buildPayloadForPayment(order);
        Outbox outbox = Outbox.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .eventType("ORDER_CREATED")
                .topic("order.created")
                .payload(payload)
                .status(com.hdv.order_service.outbox.domain.OutboxStatus.PENDING)
                .build();
        outboxService.saveOutboxAndRegisterFastPath(outbox);

        // 5. Đưa ID đơn hàng vào Redisson Delayed Queue. Đúng 10 phút sau, nó sẽ tự động trồi lên!
        orderDelayedQueue.offer(order.getId().toString(), 10, TimeUnit.MINUTES);

        log.info("Tạo đơn hàng PENDING thành công [{}] với {} loại vé, chờ thanh toán trong 10 phút.",
                order.getId(), items.size());
    }

    /**
     * Hàm này được gọi khi VNPay báo thanh toán THÀNH CÔNG (từ Kafka payment.completed)
     */
    @Transactional
    public void confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng: " + orderId));

        // Idempotency check: Chỉ update nếu đơn hàng vẫn đang PENDING
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            // Cập nhật Saga state
            sagaInstanceRepository.findByBusinessId(orderId.toString()).ifPresent(saga -> {
                saga.setStatus(com.hdv.order_service.saga.domain.SagaStatus.COMPLETED);
                saga.setCurrentStep("ORDER_CONFIRMED");
                sagaInstanceRepository.save(saga);
            });

            // Publish sự kiện order.confirmed cho Notification Service via Fast-Path
            String payload = buildPayloadForOrderConfirmed(order);
            Outbox outbox = Outbox.builder()
                    .eventId(UUID.randomUUID())
                    .aggregateType("ORDER")
                    .aggregateId(order.getId().toString())
                    .eventType("ORDER_CONFIRMED")
                    .topic("order.confirmed")
                    .payload(payload)
                    .status(com.hdv.order_service.outbox.domain.OutboxStatus.PENDING)
                    .build();
            outboxService.saveOutboxAndRegisterFastPath(outbox);

            log.info("Xác nhận thanh toán thành công cho đơn hàng: {}", orderId);
        }
    }

    /**
     * Hàm này được dùng cho logic Hết hạn 10 phút. Chỉ hủy nếu nó chưa được thanh toán.
     */
    @Transactional
    public void cancelIfStillPending(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.PENDING) {
            cancelOrder(order);
        }
    }

    /**
     * Hàm hủy đơn: Đổi trạng thái và bắt buộc phải nhả vé (release inventory)
     */
    @Transactional
    public void cancelOrder(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Cập nhật Saga state
        sagaInstanceRepository.findByBusinessId(order.getId().toString()).ifPresent(saga -> {
            saga.setStatus(com.hdv.order_service.saga.domain.SagaStatus.COMPENSATING);
            saga.setCurrentStep("RELEASING_TICKET");
            sagaInstanceRepository.save(saga);
        });

        // Ghi sự kiện "ticket.release" vào Outbox via Fast-Path để event-ticket-service hoàn lại vé
        String releasePayload = buildPayloadForRelease(order);
        Outbox outbox = Outbox.builder()
                .eventId(UUID.randomUUID())
                .aggregateType("ORDER")
                .aggregateId(order.getId().toString())
                .eventType("TICKET_RELEASE")
                .topic("ticket.release")
                .payload(releasePayload)
                .status(com.hdv.order_service.outbox.domain.OutboxStatus.PENDING)
                .build();
        outboxService.saveOutboxAndRegisterFastPath(outbox);

        log.info("Đã hủy đơn hàng [{}] và yêu cầu nhả vé.", order.getId());
    }

    // ==================== HELPER FUNCTIONS ====================

    /**
     * Payload cho payment-service: OrderPaymentRequestedEvent
     */
    private String buildPayloadForPayment(Order order) {
        try {
            List<OrderItemDto> itemsList = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                itemsList.add(OrderItemDto.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .ticketTypeName(item.getTicketTypeName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build());
            }

            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .orderId(order.getId())
                    .bookingGroupId(order.getBookingGroupId())
                    .userId(order.getUserId())
                    .userEmail(order.getEmail())
                    .eventId(order.getEventId())
                    .totalAmount(order.getTotalPrice())
                    .description("Thanh toán vé sự kiện")
                    .idempotencyKey(order.getIdempotencyKey())
                    .items(itemsList)
                    .build();

            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lỗi parse JSON payload order.created " + e.getMessage());
        }
    }

    /**
     * Payload cho event-ticket-service: release vé theo danh sách items
     */
    private String buildPayloadForRelease(Order order) {
        try {
            List<ReleaseItemDto> itemsList = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                itemsList.add(ReleaseItemDto.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(item.getQuantity())
                        .build());
            }

            TicketReleaseEvent event = TicketReleaseEvent.builder()
                    .orderId(order.getId())
                    .bookingGroupId(order.getBookingGroupId())
                    .idempotencyKey(order.getIdempotencyKey())
                    .reason("PAYMENT_TIMEOUT_OR_CANCELLED")
                    .items(itemsList)
                    .build();

            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lỗi parse JSON payload ticket.release " + e.getMessage());
        }
    }

    /**
     * Payload cho notification-service: order confirmed
     */
    private String buildPayloadForOrderConfirmed(Order order) {
        try {
            List<ConfirmedItemDto> itemsList = new ArrayList<>();
            for (OrderItem item : order.getItems()) {
                itemsList.add(ConfirmedItemDto.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .ticketTypeName(item.getTicketTypeName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build());
            }

            OrderConfirmedEvent event = OrderConfirmedEvent.builder()
                    .orderId(order.getId())
                    .bookingGroupId(order.getBookingGroupId())
                    .userId(order.getUserId())
                    .userEmail(order.getEmail())
                    .eventId(order.getEventId())
                    .totalAmount(order.getTotalPrice())
                    .idempotencyKey(order.getIdempotencyKey())
                    .items(itemsList)
                    .build();

            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Lỗi parse JSON payload order.confirmed " + e.getMessage());
        }
    }
}
