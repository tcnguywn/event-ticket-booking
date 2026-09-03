package com.hdv.order_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.TicketItemDto;
import com.hdv.common.dto.TicketReservedEvent;
import com.hdv.order_service.exception.AppException;
import com.hdv.order_service.exception.ErrorCode;
import com.hdv.order_service.idempotency.ProcessedEventRepository;
import com.hdv.order_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketReservedConsumer {

    private static final String CONSUMER_NAME = "TicketReservedConsumer";

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ticket.reserved", groupId = "order-service-group")
    public void consume(String message) {
        try {
            // Parse typed JSON payload từ event-ticket-service
            TicketReservedEvent event = objectMapper.readValue(message, TicketReservedEvent.class);
            UUID idempotencyKey = event.getIdempotencyKey();

            // Check Idempotency với composite constraint (event_id, consumer_name)
            if (processedEventRepository.insertIfNotExists(idempotencyKey, CONSUMER_NAME) == 0) {
                log.warn("[{}] Bỏ qua message ticket.reserved trùng lặp: {}", CONSUMER_NAME, idempotencyKey);
                return;
            }

            UUID eventId = event.getEventId();
            UUID userId = event.getUserId();
            String email = event.getEmail();
            Long totalPrice = event.getTotalPrice();
            UUID bookingGroupId = event.getBookingGroupId();

            // Map DTO sang local inner record
            List<OrderService.OrderItemData> items = new ArrayList<>();
            if (event.getItems() != null) {
                for (TicketItemDto item : event.getItems()) {
                    items.add(new OrderService.OrderItemData(
                            item.getTicketTypeId(),
                            item.getTicketTypeName(),
                            item.getQuantity(),
                            item.getPrice()
                    ));
                }
            }

            // Gọi service tạo đơn hàng
            orderService.createOrder(eventId, userId, email, totalPrice, items, bookingGroupId, idempotencyKey);

        } catch (Exception e) {
            log.error("Lỗi khi xử lý message ticket.reserved: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.KAFKA_ERROR, "Chưa xử lý thành công, yêu cầu Kafka retry " + e.getMessage());
        }
    }
}