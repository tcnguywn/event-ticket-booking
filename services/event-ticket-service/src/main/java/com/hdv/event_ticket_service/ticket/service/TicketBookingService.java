package com.hdv.event_ticket_service.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.common.dto.TicketItemDto;
import com.hdv.common.dto.TicketReservedEvent;
import com.hdv.event_ticket_service.event.domain.entity.Event;
import com.hdv.event_ticket_service.event.repository.EventRepository;
import com.hdv.event_ticket_service.exception.AppException;
import com.hdv.event_ticket_service.exception.ExceedOrderQuantityException;
import com.hdv.event_ticket_service.exception.ExceedPurchaseLimitException;
import com.hdv.event_ticket_service.exception.SoldOutException;
import com.hdv.event_ticket_service.exception.TicketTypeNotFoundException;
import com.hdv.event_ticket_service.outbox.domain.Outbox;
import com.hdv.event_ticket_service.outbox.domain.OutboxStatus;
import com.hdv.event_ticket_service.outbox.repository.OutboxRepository;
import com.hdv.event_ticket_service.outbox.service.OutboxService;
import com.hdv.event_ticket_service.saga.domain.SagaInstance;
import com.hdv.event_ticket_service.saga.domain.SagaStatus;
import com.hdv.event_ticket_service.saga.repository.SagaInstanceRepository;
import com.hdv.event_ticket_service.ticket.domain.dtos.BookTicketRequest;
import com.hdv.event_ticket_service.ticket.domain.dtos.BookTicketResponse;
import com.hdv.event_ticket_service.ticket.domain.entity.TicketType;
import com.hdv.event_ticket_service.ticket.domain.entity.UserTicketBooking;
import com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus;
import com.hdv.event_ticket_service.ticket.repository.TicketTypeRepository;
import com.hdv.event_ticket_service.ticket.repository.UserTicketBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketBookingService {

    private final TicketTypeRepository ticketTypeRepository;
    private final UserTicketBookingRepository userTicketBookingRepository;
    private final com.hdv.event_ticket_service.ticket.repository.BookingSeatRepository bookingSeatRepository;
    private final EventRepository eventRepository;
    private final InventoryService inventoryService;
    private final SeatReservationService seatReservationService;
    private final OutboxService outboxService;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    // L1 In-Memory Cache cho Event Metadata nhằm giảm tải tối đa cho DB khi Flash Sale
    private final Map<UUID, Event> eventMetadataCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, TicketType> ticketTypeMetadataCache = new java.util.concurrent.ConcurrentHashMap<>();

    public BookTicketResponse bookTicket(UUID userId, String email, BookTicketRequest request) {
        UUID eventId = request.getEventId();
        Event event = eventMetadataCache.computeIfAbsent(eventId, id ->
                eventRepository.findById(id)
                        .orElseThrow(() -> new AppException("Sự kiện không tồn tại", HttpStatus.NOT_FOUND))
        );

        // Step 1: Validate tất cả ticketTypeId tồn tại và thuộc event
        Map<UUID, TicketType> ticketTypeMap = new HashMap<>();
        int totalRequestedQuantity = 0;

        for (BookTicketRequest.BookTicketItemRequest item : request.getItems()) {
            TicketType type = ticketTypeMetadataCache.computeIfAbsent(item.getTicketTypeId(), id ->
                    ticketTypeRepository.findById(id)
                            .orElseThrow(() -> new TicketTypeNotFoundException(id.toString()))
            );

            if (!type.getEventId().equals(eventId)) {
                throw new AppException("Loại vé " + type.getName() + " không thuộc sự kiện này", HttpStatus.BAD_REQUEST);
            }

            // Nếu là vé chọn ghế thì số lượng bằng số ghế đã chọn
            int requestedQty = (item.getSeatIds() != null && !item.getSeatIds().isEmpty())
                    ? item.getSeatIds().size()
                    : item.getQuantity();

            // Validate maxOrderQuantity cho từng loại vé
            if (requestedQty > type.getMaxOrderQuantity()) {
                throw new ExceedOrderQuantityException();
            }

            ticketTypeMap.put(item.getTicketTypeId(), type);
            totalRequestedQuantity += requestedQty;
        }

        final int finalTotalRequested = totalRequestedQuantity;

        // Step 4: Khóa kho theo DUAL-MODEL (Ghế có số vs Vé đứng/Flash Sale)
        List<RedisRollbackEntry> redisRollbackList = new ArrayList<>();
        List<UUID> allLockedSeatIds = new ArrayList<>();

        try {
            for (BookTicketRequest.BookTicketItemRequest item : request.getItems()) {
                TicketType type = ticketTypeMap.get(item.getTicketTypeId());

                if (item.getSeatIds() != null && !item.getSeatIds().isEmpty()) {
                    // MODEL A: Khóa ghế cụ thể theo sơ đồ
                    seatReservationService.lockSeats(eventId, item.getSeatIds(), userId);
                    allLockedSeatIds.addAll(item.getSeatIds());
                } else {
                    // MODEL B: Trừ kho nguyên tử Flash Sale (Vé đứng / Khu vực tự do)
                    long remaining = inventoryService.decrementStock(type.getId().toString(), item.getQuantity(), type.getQuantity());
                    if (remaining < 0) {
                        throw new SoldOutException();
                    }
                    redisRollbackList.add(new RedisRollbackEntry(type.getId().toString(), item.getQuantity()));
                }
            }
        } catch (Exception ex) {
            // Rollback Redis stock ngay lập tức
            for (RedisRollbackEntry entry : redisRollbackList) {
                try {
                    inventoryService.incrementStock(entry.ticketTypeId, entry.quantity);
                } catch (Exception redisEx) {
                    log.error("CRITICAL: Failed to rollback Redis stock for ticketTypeId: {}", entry.ticketTypeId, redisEx);
                }
            }
            // Rollback danh sách ghế đã khóa
            if (!allLockedSeatIds.isEmpty()) {
                try {
                    seatReservationService.releaseSeats(eventId, allLockedSeatIds, userId);
                } catch (Exception seatEx) {
                    log.error("CRITICAL: Failed to release seats during rollback: {}", allLockedSeatIds, seatEx);
                }
            }
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException("Redis lock inventory failed", ex);
        }

        try {
            // Mở Transaction bao trọn các thao tác DB ghi nhận booking, saga state và outbox
            return transactionTemplate.execute(status -> {
                try {
                    // Step 3: Check maxTicketsPerUser cho toàn bộ sự kiện
                    int booked = userTicketBookingRepository.countBookedByUserAndEvent(userId, eventId);
                    if (booked + finalTotalRequested > event.getMaxTicketsPerUser()) {
                        throw new ExceedPurchaseLimitException();
                    }

                    // Step 5: DB Writes - Tạo booking cho mỗi item (Batch Save để tránh N+1)
                    UUID bookingGroupId = UUID.randomUUID();
                    UUID idempotencyKey = UUID.randomUUID();
                    List<UserTicketBooking> bookings = new ArrayList<>();

                    for (BookTicketRequest.BookTicketItemRequest item : request.getItems()) {
                        TicketType type = ticketTypeMap.get(item.getTicketTypeId());
                        int requestedQty = (item.getSeatIds() != null && !item.getSeatIds().isEmpty())
                                ? item.getSeatIds().size()
                                : item.getQuantity();

                        UserTicketBooking booking = UserTicketBooking.builder()
                                .userId(userId)
                                .eventId(eventId)
                                .ticketTypeId(type.getId())
                                .quantity(requestedQty)
                                .status(BookingStatus.PENDING)
                                .bookingGroupId(bookingGroupId)
                                .idempotencyKey(UUID.randomUUID())
                                .build();
                        bookings.add(booking);
                    }
                    List<UserTicketBooking> savedBookings = userTicketBookingRepository.saveAll(bookings);

                    // Lưu mapping ghế với booking (nếu có chọn ghế)
                    List<com.hdv.event_ticket_service.ticket.domain.entity.BookingSeat> bookingSeats = new ArrayList<>();
                    for (int i = 0; i < request.getItems().size(); i++) {
                        BookTicketRequest.BookTicketItemRequest item = request.getItems().get(i);
                        UserTicketBooking savedBooking = savedBookings.get(i);

                        if (item.getSeatIds() != null && !item.getSeatIds().isEmpty()) {
                            for (UUID seatId : item.getSeatIds()) {
                                bookingSeats.add(com.hdv.event_ticket_service.ticket.domain.entity.BookingSeat.builder()
                                        .bookingId(savedBooking.getId())
                                        .bookingGroupId(bookingGroupId)
                                        .eventId(eventId)
                                        .seatId(seatId)
                                        .status(com.hdv.event_ticket_service.ticket.domain.enums.BookingSeatStatus.HOLD)
                                        .build());
                            }
                        }
                    }
                    if (!bookingSeats.isEmpty()) {
                        bookingSeatRepository.saveAll(bookingSeats);
                        log.info("Saved {} booking_seats mapping records for bookingGroup {}", bookingSeats.size(), bookingGroupId);
                    }

                    // Step 6: Ghi nhận Saga State
                    com.hdv.event_ticket_service.saga.domain.SagaInstance saga = com.hdv.event_ticket_service.saga.domain.SagaInstance.builder()
                            .correlationId(idempotencyKey)
                            .businessId(bookingGroupId.toString())
                            .sagaType("TICKET_BOOKING_SAGA")
                            .currentStep("RESERVE_TICKET")
                            .status(com.hdv.event_ticket_service.saga.domain.SagaStatus.STARTED)
                            .build();
                    sagaInstanceRepository.save(saga);

                    // Step 7: Ghi Outbox event và kích hoạt AFTER_COMMIT Fast-Path
                    Outbox outbox = Outbox.builder()
                            .eventId(idempotencyKey)
                            .aggregateType("BOOKING_GROUP")
                            .aggregateId(bookingGroupId.toString())
                            .eventType("TICKET_RESERVED")
                            .topic("ticket.reserved")
                            .payload(buildPayload(bookings, ticketTypeMap, email, eventId, idempotencyKey))
                            .status(com.hdv.event_ticket_service.outbox.domain.OutboxStatus.PENDING)
                            .build();
                    outboxService.saveOutboxAndRegisterFastPath(outbox);

                    return BookTicketResponse.builder()
                            .bookingGroupId(bookingGroupId)
                            .message("Ticket booking is pending confirmation.")
                            .status(BookingStatus.PENDING)
                            .build();

                } catch (Exception ex) {
                    status.setRollbackOnly();
                    throw ex;
                }
            });
        } catch (Exception dbEx) {
            // DB Transaction bị rollback -> Hoàn trả tồn kho Redis
            for (RedisRollbackEntry entry : redisRollbackList) {
                try {
                    inventoryService.incrementStock(entry.ticketTypeId, entry.quantity);
                } catch (Exception redisEx) {
                    log.error("CRITICAL: Failed to rollback Redis stock for ticketTypeId: {}", entry.ticketTypeId, redisEx);
                }
            }
            throw dbEx;
        }
    }

    private String buildPayload(List<UserTicketBooking> bookings, Map<UUID, TicketType> typeMap,
                                String email, UUID eventId, UUID idempotencyKey) {
        try {
            long totalPrice = 0;
            List<TicketItemDto> items = new ArrayList<>();

            for (UserTicketBooking booking : bookings) {
                TicketType type = typeMap.get(booking.getTicketTypeId());
                long itemTotal = type.getPrice() * booking.getQuantity();
                totalPrice += itemTotal;

                items.add(TicketItemDto.builder()
                        .ticketTypeId(type.getId())
                        .ticketTypeName(type.getName())
                        .quantity(booking.getQuantity())
                        .price(type.getPrice())
                        .build());
            }

            TicketReservedEvent event = TicketReservedEvent.builder()
                    .bookingGroupId(bookings.get(0).getBookingGroupId())
                    .userId(bookings.get(0).getUserId())
                    .email(email)
                    .eventId(eventId)
                    .totalPrice(totalPrice)
                    .idempotencyKey(idempotencyKey)
                    .items(items)
                    .build();

            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create outbox payload", e);
        }
    }

    /**
     * Inner record dùng để track những gì cần rollback trên Redis nếu transaction thất bại
     */
    private record RedisRollbackEntry(String ticketTypeId, int quantity) {}
}
