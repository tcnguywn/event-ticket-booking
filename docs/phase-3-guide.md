# 🎟️ Hướng Dẫn Tự Code Phase 3: Event & Ticket Inventory Hardening

> **Mục tiêu Phase 3:** Hoàn thiện tầng xử lý kho vé (Inventory Concurrency Engine) và đồng bộ trạng thái đơn hàng trong `event-ticket-service`.
> 1. Sửa lỗi đảo thứ tự tham số và hardcode kho `"0"` trong `InventoryService.java` & `inventory.lua`.
> 2. Đảm bảo cơ chế **Lazy Initialization (Cold Cache)**: Nếu Redis bị khởi động lại hoặc chưa nạp dữ liệu, Script Lua sẽ tự động lấy số lượng vé từ PostgreSQL nạp vào Redis một cách nguyên tử.
> 3. Viết mới Kafka Consumer `OrderConfirmedConsumer.java` để lắng nghe topic `order.confirmed` và chuyển trạng thái vé sang `CONFIRMED`.
> 4. Bảo vệ Idempotency (chống trùng lặp message khi Kafka retry) bằng bảng `processed_events`.

---

## 1. 📁 Các File Cần Chỉnh Sửa & Viết Mới Trong `event-ticket-service`

```
services/event-ticket-service/src/main/
├── resources/scripts/
│   └── inventory.lua                      # Script Lua nguyên tử: Trừ kho & Tự động nạp kho khi Cold Cache
└── java/com/hdv/event_ticket_service/
    ├── ticket/
    │   ├── domain/enums/
    │   │   └── BookingStatus.java          # Bổ sung trạng thái CONFIRMED
    │   ├── repository/
    │   │   └── UserTicketBookingRepository.java # Đếm vé tính cả trạng thái CONFIRMED
    │   └── service/
    │       ├── InventoryService.java       # Sửa truyền đúng tham số (qty, initialDbStock)
    │       └── TicketBookingService.java   # Truyền type.getQuantity() vào inventoryService
    └── kafka/
        └── OrderConfirmedConsumer.java     # [TẠO MỚI] Consumer cập nhật trạng thái CONFIRMED khi đơn thanh toán xong
```

---

## 2. 🧩 Hướng Dẫn Chi Tiết Từng Bước Code

---

### 🛠️ Bước 1: Sửa Script Lua Kho Vé (`resources/scripts/inventory.lua`)

#### 🎯 Vấn đề:
Khi Redis vừa bật lên (Cache rỗng / Cold Cache), `redis.call('GET', KEYS[1])` sẽ trả về `false` (nil). 
Script Lua cần tự động `SET` giá trị từ DB (`ARGV[2]`) với tùy chọn `'NX'` (chỉ set nếu chưa tồn tại), sau đó mới thực hiện trừ `ARGV[1]` vé.

#### 📝 Code chuẩn cho `services/event-ticket-service/src/main/resources/scripts/inventory.lua`:

```lua
-- =========================================================================
-- KEYS[1]: 'ticket_stock:{ticketTypeId}'
-- ARGV[1]: Số lượng vé cần mua/giảm (quantity to decrement)
-- ARGV[2]: Số lượng vé ban đầu từ DB (dùng khi Redis Cache Miss / Cold Cache)
-- =========================================================================

local stock = redis.call('GET', KEYS[1])

-- 1. LAZY INITIALIZATION: Nếu Redis chưa có key (Cold Cache), nạp từ DB (ARGV[2])
if stock == false then
    redis.call('SET', KEYS[1], ARGV[2], 'NX')
    stock = ARGV[2]
end

local qty = tonumber(ARGV[1])
local current = tonumber(stock)

-- 2. KIỂM TRA TỒN KHO
if current < qty then
    return -1  -- Trả về -1 biểu thị Hết vé (Sold Out)
end

-- 3. TRỪ KHO NGUYÊN TỬ (ATOMIC DECREMENT)
return redis.call('DECRBY', KEYS[1], qty)  -- Trả về số lượng vé còn lại
```

---

### 🛠️ Bước 2: Sửa `InventoryService.java` & `TicketBookingService.java`

#### 🎯 Vấn đề trong `InventoryService.java`:
Trước đó tham số bị đảo ngược và hardcode `"0"`, khiến mọi request khi Redis cold cache đều bị báo hết vé (`-1`).

#### 📝 1. Cập nhật `InventoryService.java`:
```java
package com.hdv.event_ticket_service.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> inventoryLuaScript;

    /**
     * Trừ kho nguyên tử trên Redis qua Lua script.
     * @param ticketTypeId ID loại vé
     * @param quantity Số lượng mua
     * @param initialStockFromDb Số lượng tồn kho hiện tại trong DB (làm fallback khi cold cache)
     * @return Số vé còn lại (>= 0), hoặc -1 nếu hết vé
     */
    public long decrementStock(String ticketTypeId, int quantity, int initialStockFromDb) {
        String key = "ticket_stock:" + ticketTypeId;

        Long remaining = redisTemplate.execute(
                inventoryLuaScript,
                Collections.singletonList(key),
                String.valueOf(quantity),            // ARGV[1]: Số lượng mua cần trừ
                String.valueOf(initialStockFromDb)   // ARGV[2]: Số vé nạp vào Redis nếu cache miss
        );

        return remaining != null ? remaining : -1;
    }

    public void incrementStock(String ticketTypeId, int quantity) {
        String key = "ticket_stock:" + ticketTypeId;
        redisTemplate.opsForValue().increment(key, quantity);
        log.info("Incremented stock for {} by {}", ticketTypeId, quantity);
    }
}
```

#### 📝 2. Cập nhật `TicketBookingService.java` (Tại dòng gọi `decrementStock`):
Mở file `services/event-ticket-service/src/main/java/com/hdv/event_ticket_service/ticket/service/TicketBookingService.java`:
Tìm đến đoạn trừ kho Redis trong vòng lặp (khoảng dòng 86-90) và truyền thêm `type.getQuantity()`:

```java
// Step 4: Lock inventory cho từng loại vé trong Redis (Atomic per type)
for (BookTicketRequest.BookTicketItemRequest item : request.getItems()) {
    TicketType type = ticketTypeMap.get(item.getTicketTypeId());
    
    // Truyền số lượng tồn kho từ DB (type.getQuantity()) làm fallback cho Cold Cache
    long remaining = inventoryService.decrementStock(
            type.getId().toString(), 
            item.getQuantity(), 
            type.getQuantity()
    );
    
    if (remaining < 0) {
        throw new SoldOutException();
    }
    redisRollbackList.add(new RedisRollbackEntry(type.getId().toString(), item.getQuantity()));
}
```

---

### 🛠️ Bước 3: Cập nhật Enum & Repository

#### 📝 1. Cập nhật `BookingStatus.java`:
Mở file `services/event-ticket-service/src/main/java/com/hdv/event_ticket_service/ticket/domain/enums/BookingStatus.java`:
```java
package com.hdv.event_ticket_service.ticket.domain.enums;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    SENT,
    CANCELLED
}
```

#### 📝 2. Cập nhật `UserTicketBookingRepository.java`:
Mở file `services/event-ticket-service/src/main/java/com/hdv/event_ticket_service/ticket/repository/UserTicketBookingRepository.java`:
Sửa câu truy vấn đếm vé để tính cả trạng thái `CONFIRMED`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT COALESCE(SUM(u.quantity), 0) FROM UserTicketBooking u " +
        "WHERE u.userId = :userId AND u.eventId = :eventId " +
        "AND u.status IN (com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.PENDING, " +
        "com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.CONFIRMED, " +
        "com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus.SENT)")
int countBookedByUserAndEvent(@Param("userId") UUID userId, @Param("eventId") UUID eventId);
```

---

### 🛠️ Bước 4: Tạo mới Kafka Consumer `OrderConfirmedConsumer.java`

#### 🎯 Vai trò:
Khi `order-service` xác nhận đơn hàng đã thanh toán thành công, nó sẽ publish event lên topic **`order.confirmed`**.
`event-ticket-service` cần lắng nghe topic này để:
1. Kiểm tra Idempotency qua bảng `processed_events`.
2. Chuyển trạng thái các bản ghi `UserTicketBooking` từ `PENDING` sang `CONFIRMED`.

#### 📝 Tạo file mới: `services/event-ticket-service/src/main/java/com/hdv/event_ticket_service/kafka/OrderConfirmedConsumer.java`

```java
package com.hdv.event_ticket_service.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdv.event_ticket_service.idempotency.ProcessedEvent;
import com.hdv.event_ticket_service.idempotency.ProcessedEventRepository;
import com.hdv.event_ticket_service.ticket.domain.entity.UserTicketBooking;
import com.hdv.event_ticket_service.ticket.domain.enums.BookingStatus;
import com.hdv.event_ticket_service.ticket.repository.UserTicketBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final UserTicketBookingRepository userTicketBookingRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.confirmed", groupId = "event-ticket-group")
    @Transactional
    public void consumeOrderConfirmed(String payload, MessageHeaders headers) {
        log.info("Received order.confirmed event: {}", payload);
        try {
            // 1. Trích xuất Idempotency Key từ Header hoặc Payload
            String keyHeader = (String) headers.get("kafka_receivedMessageKey");
            JsonNode root = objectMapper.readTree(payload);

            UUID idempotencyKey;
            if (keyHeader != null && !keyHeader.isBlank()) {
                idempotencyKey = UUID.fromString(keyHeader);
            } else if (root.has("idempotencyKey")) {
                idempotencyKey = UUID.fromString(root.get("idempotencyKey").asText());
            } else {
                idempotencyKey = UUID.randomUUID();
            }

            // 2. Chống lặp message (Idempotency Check)
            if (processedEventRepository.existsById(idempotencyKey)) {
                log.info("Order confirmed event with key {} already processed. Skipping.", idempotencyKey);
                return;
            }

            // 3. Đánh dấu đã xử lý vào DB
            processedEventRepository.save(new ProcessedEvent(idempotencyKey, null));

            // 4. Tìm và cập nhật các bản ghi booking sang CONFIRMED
            if (root.has("bookingGroupId")) {
                UUID bookingGroupId = UUID.fromString(root.get("bookingGroupId").asText());
                List<UserTicketBooking> bookings = userTicketBookingRepository.findByBookingGroupId(bookingGroupId);

                for (UserTicketBooking booking : bookings) {
                    if (booking.getStatus() == BookingStatus.PENDING) {
                        booking.setStatus(BookingStatus.CONFIRMED);
                        userTicketBookingRepository.save(booking);
                        log.info("Updated booking {} to CONFIRMED", booking.getId());
                    }
                }
            } else if (root.has("orderId")) {
                log.info("Order confirmed for orderId: {}", root.get("orderId").asText());
            }

        } catch (Exception e) {
            log.error("Failed to process order.confirmed event: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing order.confirmed event", e);
        }
    }
}
```

---

## 3. 🧪 Kịch Bản Kiểm Thử Cho Phase 3

Sau khi bạn hoàn thành code các bước trên:

### Test 1: Kiểm tra Cold Cache nạp tự động từ DB
1. Xóa sạch key tồn kho trong Redis:
   ```powershell
   docker exec -it ticketing_redis redis-cli FLUSHALL
   ```
2. Gọi API đặt vé thông qua Gateway.
3. Kiểm tra lại Redis:
   ```powershell
   docker exec -it ticketing_redis redis-cli KEYS "ticket_stock:*"
   ```
   ✅ **Kỳ vọng:** Key `ticket_stock:<id>` tự động xuất hiện trong Redis với số lượng đã được trừ chính xác.

---

### Test 2: Mô phỏng phát event `order.confirmed` qua Kafka
1. Đặt vé để tạo 1 bản ghi `UserTicketBooking` ở trạng thái `PENDING` (lấy mã `bookingGroupId` từ DB).
2. Vào **Kafka UI** ([http://localhost:8086](http://localhost:8086)) $\rightarrow$ Chọn Topic `order.confirmed` $\rightarrow$ Bấm **Produce Message** với nội dung:
   ```json
   {
     "orderId": "11111111-1111-1111-1111-111111111111",
     "bookingGroupId": "<bookingGroupId_vua_tao>",
     "idempotencyKey": "22222222-2222-2222-2222-222222222222",
     "status": "CONFIRMED"
   }
   ```
3. Kiểm tra DB `event_ticket_db`:
   ```sql
   SELECT id, status, booking_group_id FROM user_ticket_booking;
   ```
   ✅ **Kỳ vọng:** Trạng thái chuyển từ `PENDING` sang `CONFIRMED`.
4. Gửi lại cùng message trên lần 2 $\rightarrow$ Log hiển thị `"already processed. Skipping."` (Idempotency thành công).

---

## 4. 🎯 Checklist Hoàn Thành Phase 3

- [ ] Script `inventory.lua` nhận đúng `ARGV[1] = qty`, `ARGV[2] = initialStock`.
- [ ] `InventoryService.java` và `TicketBookingService.java` truyền đúng tham số tồn kho DB.
- [ ] `BookingStatus.java` đã có trạng thái `CONFIRMED`.
- [ ] `OrderConfirmedConsumer.java` đã tạo và lắng nghe topic `order.confirmed`.

👉 Hãy tiến hành code theo hướng dẫn trên. Khi bạn hoàn thành, hãy báo lại để chúng ta kiểm tra mã nguồn nhé!
