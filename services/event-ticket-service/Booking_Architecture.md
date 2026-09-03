Optimizing tool selection...Tôi sẽ giải thích chi tiết lý do tồn tại của `user_ticket_booking`, kiến trúc business flow, và cơ chế gửi Kafka.

## 1️⃣ Tại Sao Cần Bảng `user_ticket_booking`?

### Vấn Đề Không Có Bảng Này:

Nếu chỉ dùng **Redis** để lock vé và **Outbox** để track messages:

```
Problem 1: Kiểm tra maxPerUser - MỞ RỘNG
- User muốn kiểm tra: "Tôi đã đặt mấy vé loại A cho sự kiện B?"
- Không có lịch sử trong DB
- Phải query từ order-service (tích hợp Saga)
- Chậm = N+1 query across services

Problem 2: Ở Booking Time - KHÔNG ĐON GIẢN
- Khi user requestPick 2 vé, kiểm tra maxPerUser = 2
- Cách duy nhất: call order-service "tôi đã đặt bao nhiêu?"
- Nhưng order-service có thể đang processing orders từ Kafka
- Race condition: 2 request cùng lúc
  - Request 1: "đã đặt 0, cộng thêm 2 -> ok"
  - Request 2: "đã đặt 0, cộng thêm 2 -> ok"
  - Kết quả: User đặt 4 vé (vượt maxPerUser=2)

Problem 3: Outbox Pattern - KHÔNG ĐẬT ĐỦ
- Outbox chỉ track Kafka messages (PENDING | SENT)
- Không track SỐ LƯỢNG vé đặt
```

### Giải Pháp: Sử Dụng `user_ticket_booking` Bảng

```java
// ✅ Có bảng user_ticket_booking
Integer currentTotal = userTicketBookingRepository
    .countUserTicketsForType(userId, ticketTypeId);  // Query COUNT(*) WHERE status='SENT'
// currentTotal = 1 (1 lần đặt 1 vé trước đó)

// Kiểm tra trước khi booking
if (currentTotal + 2 > maxPerUser){
    throw ExceedPurchaseLimitException;
}
```

**Lợi Ích:**
- ✅ **Tracking**: Lịch sử đặt vé + Số lượng của mỗi order
- ✅ **Limit Enforcement**: `SELECT SUM(quantity) FROM user_ticket_booking WHERE status='SENT'`
- ✅ **Race Condition Fix**: Tất cả checks trong **1 transaction DB** → atomic
- ✅ **Outbox Integration**: Track status (PENDING → SENT) tích hợp sẵn
- ✅ **Idempotency**: `idempotencyKey` unique prevent duplicate
- ✅ **Audit Trail**: Lịch sử khi nào user đặt mấy vé

---

## 2️⃣ Toàn Bộ Business Architecture

### Luồng Tạo Sự Kiện (UC1)

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT (Web/Mobile)                      │
└────────────────────┬────────────────────────────────────────┘
                     │ 1. POST /api/events
                     │ {name, description, ticketTypes:[...]}
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY (8888)                        │
│  - Validate JWT token
│  - Extract organizerId from token
│  - Rate limiting
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│            EVENT-TICKET-SERVICE (8081)                      │
│  EventService.createEvent():
│  1. Validate CreateEventReq (@Valid)
│  2. Create Event entity
│  3. Create TicketType list (cascade)
│  4. Save to PostgreSQL (ATOMIC TRANSACTION)
│  5. Return EventRes
│
│  Database:
│  INSERT INTO event (id, name, organizer_id, status, ...)
│  INSERT INTO ticket_type (id, name, price, quantity, 
│                          max_per_user, max_order_quantity, event_id, ...)
└─────────────────────────────────────────────────────────────┘
                 │
                 ▼ (Response)
         ✅ Created event
```

**OUTPUT**: Sự kiện + Ticket Types được lưu trong PostgreSQL

---

### Luồng Đặt Vé (UC2) - **CẶP ĐÔI & KAFKA**

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT (User)                            │
└────────────────────┬────────────────────────────────────────┘
                     │ 1. POST /api/tickets/book
                     │ { ticketTypeId: "xxx", quantity: 2 }
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY (8888)                        │
│  - Validate JWT
│  - Extract userId from token
│  - Rate limiting
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────┐
│            EVENT-TICKET-SERVICE (8081)                      │
│  TicketBookingService.bookTicket():
│
│  STEP 1: Validation
│  ├─ ticketTypeId not null
│  ├─ quantity >= 1
│  └─ Load TicketType from DB
│
│  STEP 2: Check Constraints
│  ├─ quantity <= maxOrderQuantity
│  ├─ countUserTickets + quantity <= maxPerUser
│  └─ All checks PASS ✓
│
│  STEP 3: Lock Inventory (Redis + Lua)
│  ├─ Redis Key: "ticket_stock:ticketTypeId"
│  ├─ Execute Lua Script:
│  │  IF stock >= 2 THEN
│  │    DECRBY stock 2
│  │    RETURN remaining
│  │  ELSE
│  │    RETURN -1
│  └─ Result: remaining = 98 ✓
│
│  STEP 4: Dual Write Pattern (ORM + Outbox)
│  ├─ Create Outbox record
│  │  {
│  │    id: uuid,
│  │    topic: "ticket.reserved",
│  │    payload: {...},
│  │    idempotencyKey: uuid,
│  │    status: "PENDING"
│  │  }
│  ├─ INSERT INTO outbox
│  │
│  ├─ Create UserTicketBooking record
│  │  {
│  │    id: uuid,
│  │    userId: "user123",
│  │    eventId: "event456",
│  │    ticketTypeId: "ticket789",
│  │    quantity: 2,
│  │    status: "PENDING",
│  │    idempotencyKey: uuid
│  │  }
│  ├─ INSERT INTO user_ticket_booking
│  │
│  └─ COMMIT TRANSACTION ✓
│
│  ** KHI TRANSACTION COMMIT:
│     - Outbox record persist to DB
│     - UserTicketBooking persist to DB
│     - Redis inventory đã lock (DECRBY)
│     - Nếu DB error → Redis rollback (INCR)
│
└─────────────────────────────────────────────────────────────┘
                 │
                 ▼ (Response)
         ✅ Booking successful
         
                 │
                 ├──────────────────────────────────────────┐
                 │                                          │
                 ▼ (Background Task)                        ▼ (Background Task)
         ╔════════════════════════╗               ╔════════════════════════╗
         ║   OutboxPoller Task    ║               ║ (Future feature)       ║
         ║  @Scheduled every 1s   ║               ║ Update UserTicketBooking
         ╚════════════════════════╝               ╚════════════════════════╝
                 │
                 ▼
         SELECT * FROM outbox
         WHERE status='PENDING'
         LIMIT 100
                 │
                 ├─ outbox_1: {ticketTypeId, quantity, userId, ...}
                 ├─ outbox_2: {...}
                 └─ outbox_N: {...}
                 │
                 ▼
         FOR EACH outbox record:
         - KafkaTemplate.send("ticket.reserved", outbox)
         - UPDATE outbox SET status='SENT'
```

---

## 3️⃣ Gửi Gì Lên Kafka + Format

### **Topic: `ticket.reserved`**

Payload khi gửi Kafka:

```json
{
  "ticketTypeId": "abc-123-def",
  "eventId": "event-456-xyz",
  "quantity": 2,
  "userId": "user-789",
  "price": 500000,
  "totalPrice": 1000000,
  "timestamp": 1711420200000
}
```

**Giải Thích:**
- `ticketTypeId` + `eventId` → order-service biết đó là vé loại nào, sự kiện nào
- `quantity` → Số vé user đặt
- `userId` → Để tạo order record
- `price` → Giá gốc 1 vé
- `totalPrice` → Tổng tiền = price × quantity
- `timestamp` → Khi nào booking được tạo

---

### **Order Service Nhận & Xử Lý**

```java
@KafkaListener(topics = "ticket.reserved", groupId = "order-service")
public void handleTicketReserved(String payload) {
    // 1. Parse JSON
    Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
    
    // 2. Create Order record
    Order order = Order.builder()
        .userId(msg.get("userId"))
        .eventId(msg.get("eventId"))
        .ticketTypeId(msg.get("ticketTypeId"))
        .quantity(msg.get("quantity"))
        .totalPrice(msg.get("totalPrice"))
        .status("PENDING")  // Chờ payment
        .build();
    
    orderRepository.save(order);
    
    // 3. Start Saga - Publish order.created event
    // (order-service sẽ gửi lên topic khác)
}
```

---

## 4️⃣ Chi Tiết Luồng - Step by Step

### **Scenario: User Đặt 2 Vé Hạng A của Event Concert**

```
T0: User Clicks "Book 2 tickets"
      ↓
T1: API Gateway nhận request
    - Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    - Extract userId="user-alice-001"
      ↓
T2: EventTicketService.bookTicket() TRỊ GỌI
    - Input: BookTicketReq { ticketTypeId: "vip-concert-001", quantity: 2 }
    - userId: "user-alice-001"
      ↓
T3: VALIDATION PHASE
    ├─ ticketTypeId != null ✓
    ├─ quantity = 2 > 0 ✓
    └─ Load từ DB:
           SELECT * FROM ticket_type WHERE id='vip-concert-001'
           RESULT: {
             id: "vip-concert-001",
             name: "VIP",
             price: 500000,
             quantity: 100,
             maxPerUser: 2,        ← Giới hạn 2 vé/người
             maxOrderQuantity: 10,  ← Giới hạn 10 vé/giao dịch
             eventId: "concert-2026"
           }
      ↓
T4: CHECK CONSTRAINT 1: maxOrderQuantity
    ├─ Request quantity (2) <= maxOrderQuantity (10) ?
    ├─ 2 <= 10 ? YES ✓
      ↓
T5: CHECK CONSTRAINT 2: maxPerUser
    ├─ Query UserTicketBooking:
    │  SELECT SUM(quantity) FROM user_ticket_booking
    │  WHERE userId='user-alice-001' 
    │    AND ticketTypeId='vip-concert-001'
    │    AND status='SENT'
    │
    ├─ Result: currentTotal = 0 (lần đầu user đặt)
    ├─ totalAfterBooking = 0 + 2 = 2
    ├─ totalAfterBooking (2) <= maxPerUser (2) ? YES ✓
      ↓
T6: REDIS LOCK - Atomic via Lua Script
    ├─ Redis Key: "ticket_stock:vip-concert-001"
    ├─ Current value in Redis: 100 (giá trị ban đầu từ DB)
    ├─ Execute Lua:
    │  KEYS[1] = "ticket_stock:vip-concert-001"
    │  ARGV[1] = "100"  (initial DB stock)
    │  ARGV[2] = "2"    (quantity to decrement)
    │  
    │  stock = GET KEYS[1]
    │  IF stock == nil THEN
    │    SET KEYS[1] = ARGV[1]  (cache miss, set to 100)
    │    stock = 100
    │  END
    │  
    │  quantity = tonumber(ARGV[2]) = 2
    │  IF stock < quantity THEN
    │    RETURN -1  (insufficient)
    │  END
    │  
    │  RETURN DECRBY(KEYS[1], quantity)
    │
    ├─ Lua execution: ATOMIC (single-threaded)
    ├─ Result: 98 (remaining tickets) ✓
      ↓
T7: DATABASE TRANSACTION BEGIN
    ├─ @Transactional annotation ensures ACID
      ↓
T8: INSERT Outbox Record (for Kafka)
    ├─ INSERT INTO outbox (id, topic, payload, idempotencyKey, active)
    ├─ Record:
    │  {
    │    id: "outbox-uuid-001",
    │    topic: "ticket.reserved",
    │    payload: "{
    │      \"ticketTypeId\": \"vip-concert-001\",
    │      \"eventId\": \"concert-2026\",
    │      \"quantity\": 2,
    │      \"userId\": \"user-alice-001\",
    │      \"price\": 500000,
    │      \"totalPrice\": 1000000,
    │      \"timestamp\": 1711420200000
    │    }",
    │    idempotencyKey: "idempotent-key-xyz",
    │    active: "PENDING"
    │  }
    │
    ├─ Status: INSERT SUCCESS ✓
      ↓
T9: INSERT UserTicketBooking Record (Tracking)
    ├─ INSERT INTO user_ticket_booking 
    │  (id, userId, eventId, ticketTypeId, quantity, status, idempotencyKey)
    ├─ Record:
    │  {
    │    id: "booking-uuid-001",
    │    userId: "user-alice-001",
    │    eventId: "concert-2026",
    │    ticketTypeId: "vip-concert-001",
    │    quantity: 2,
    │    status: "PENDING",
    │    idempotencyKey: "idempotent-key-xyz"
    │  }
    │
    ├─ Status: INSERT SUCCESS ✓
      ↓
T10: DATABASE TRANSACTION COMMIT
     ├─ Both Outbox + UserTicketBooking persisted
     ├─ Transaction locked → All or nothing
     ├─ Status: COMMITTED ✓
     
     ** IF error at T8 or T9:
        - ROLLBACK transaction
        - Outbox + UserTicketBooking NOT inserted
        - Redis Redis rollback: INCR "ticket_stock:vip-concert-001" by 2
        - Exception thrown to client
      ↓
T11: RETURN Response to User
     ├─ HTTP 200 OK
     ├─ { success: true, message: "Booking successful" }
      ↓
T12: [Background Task] OutboxPoller (@Scheduled every 1s)
     ├─ SELECT * FROM outbox WHERE active='PENDING' LIMIT 100
     ├─ For each record:
     │  1. KafkaTemplate.send("ticket.reserved", payload)
     │     → Kafka broker receives message
     │     → Broker replicates across brokers
     │
     │  2. UPDATE outbox SET active='SENT'
     │     → Mark as sent in DB
     │
     │  3. Log: "Published outbox-uuid-001 to Kafka"
     │
     └─ Next scheduled run in 1s
      ↓
T13: Order Service Consumes from "ticket.reserved"
     ├─ @KafkaListener(topics="ticket.reserved", groupId="order-service")
     ├─ Receive payload:
     │  {
     │    ticketTypeId: "vip-concert-001",
     │    eventId: "concert-2026",
     │    quantity: 2,
     │    userId: "user-alice-001",
     │    totalPrice: 1000000,
     │    timestamp: 1711420200000
     │  }
     │
     ├─ Create Order record in order_service DB
     │  INSERT INTO orders (userId, eventId, quantity, totalPrice, status)
     │  VALUES ("user-alice-001", "concert-2026", 2, 1000000, "PENDING")
     │
     │  Status: PENDING (waiting for payment)
     │
     └─ Publish "order.created" event to Kafka
        → payment-service consumes
        → notification-service consumes
      ↓
T14: Payment Service Processes
     ├─ Consume "order.created"
     ├─ Call VNPay API
     ├─ If payment SUCCESS:
     │  - UPDATE order SET status='CONFIRMED'
     │  - Publish "order.confirmed" event
     │
     └─ If payment FAILED:
        - UPDATE order SET status='CANCELLED'
        - Publish "order.cancelled" event
      ↓
T15: Notification Service Sends Email
     ├─ Consume "order.confirmed" or "order.cancelled"
     ├─ If confirmed:
     │  - Generate QR code
     │  - Send confirmation email
     │
     └─ If cancelled:
        - Send cancellation email
      ↓
T16: Event-Ticket-Service Recieves Cancellation (if needed)
     ├─ Listen to "order.cancelled"
     ├─ Extract ticketTypeId, quantity
     ├─ Call releaseTickets(ticketTypeId, quantity)
     │
     └─ Redis INCR "ticket_stock:vip-concert-001" by 2
        → Inventory back to 100
```

---

## 5️⃣ Sơ Đồ Tương Tác Giữa Services

```
                         ┌─────────────────┐
                         │     CLIENT      │
                         └────────┬────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │  API Gateway (8888)       │
                    │  - JWT validation         │
                    │  - Rate limiting          │
                    └────────────┬──────────────┘
                                 │
                ┌────────────────┼────────────────┐
                │                │                │
                ▼                ▼                ▼
        ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
        │ event-ticket │  │    order     │  │   payment    │
        │  -service    │  │  -service    │  │  -service    │
        │   (8081)     │  │   (8083)     │  │   (8084)     │
        └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
               │                  │                  │
               │ ticket.reserved   │                  │
               │ (Outbox Poller)   │                  │
               │                   ▼                  │
               └──────────► ┌──────────────────┐ ◄───┘
                            │  KAFKA BROKER    │
                            │  ┌────────────┐  │
                            │  │topic:      │  │
                            │  │- ticket.   │  │
                            │  │  reserved  │  │
                            │  │- order.    │  │
                            │  │  created   │  │
                            │  │- order.    │  │
                            │  │  confirmed │  │
                            │  │- order.    │  │
                            │  │  cancelled │  │
                            │  └────────────┘  │
                            └──────────────────┘
                                   │
                 ┌─────────────────┼──────────────────┐
                 │                 │                  │
                 ▼                 ▼                  ▼
        ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
        │ notification │  │notification  │  │ tick-release │
        │  -service    │  │ -service     │  │  (if needed) │
        │  (8085)      │  │ (8085)       │  │              │
        └──────────────┘  └──────────────┘  └──────────────┘
```

---

## 6️⃣ Database State After Booking

**PostgreSQL - event_ticket_service DB:**

```sql
-- ticket_type table
┌───────┬──────────┬────────┬──────────┬──────────────┬────────────────┐
│  id   │   name   │ price  │ quantity │ max_per_user │ max_order_qty  │
├───────┼──────────┼────────┼──────────┼──────────────┼────────────────┤
│ vip-1 │ VIP      │500000  │    98*   │      2       │      10        │
│ std-1 │ Standard │250000  │   200    │      3       │      10        │
└───────┴──────────┴────────┴──────────┴──────────────┴────────────────┘
                                ^
                        *Updated by Redis
                         (Lua script DECRBY)

-- user_ticket_booking table
┌─────────┬──────────────────┬──────────────┬──────────┬──────────┬────────┐
│user_id  │ ticket_type_id   │ quantity     │ status   │event_id  │booking │
├─────────┼──────────────────┼──────────────┼──────────┼──────────┼────────┤
│user-001 │ vip-concert-001  │      2       │ PENDING  │concert-2 │uuid-01 │
│user-002 │ vip-concert-001  │      1       │ SENT     │concert-2 │uuid-02 │
└─────────┴──────────────────┴──────────────┴──────────┴──────────┴────────┘

-- outbox table
┌──────────┬──────────────────┬────────┬────────────┬──────────────┐
│    id    │      topic       │ status │  payload   │ idempotency  │
├──────────┼──────────────────┼────────┼────────────┼──────────────┤
│outbox-01 │ ticket.reserved  │PENDING │{JSON...}   │idempotent-1  │
│outbox-02 │ ticket.reserved  │  SENT  │{JSON...}   │idempotent-2  │
└──────────┴──────────────────┴────────┴────────────┴──────────────┘
```

**Redis:**

```
KEY: "ticket_stock:vip-concert-001"
VALUE: 98     (Original: 100, after DECRBY 2)

KEY: "ticket_stock:std-concert-001"
VALUE: 200
```

**Kafka Topics:**

```
Topic: ticket.reserved
┌─────┬───────────────────────────────────────────┐
│Part │ Message (Offset)                          │
├─────┼───────────────────────────────────────────┤
│  0  │ { ticketTypeId: vip-1, quantity: 2, ... } │
│  0  │ { ticketTypeId: std-1, quantity: 1, ... } │
│  1  │ { ticketTypeId: vip-1, quantity: 1, ... } │
└─────┴───────────────────────────────────────────┘

Topic: order.created
┌─────┬───────────────────────────────────────────┐
│Part │ Message (Offset)                          │
├─────┼───────────────────────────────────────────┤
│  0  │ { orderId: order-123, userId: ..., ... }  │
│  1  │ { orderId: order-124, userId: ..., ... }  │
└─────┴───────────────────────────────────────────┘
```

---

## 7️⃣ Tại Sao Kiến Trúc Này?

| Thành Phần | Lý Do |
|-----------|-------|
| **Outbox Pattern** | ✅ Guarantee message published (No message lost), ✅ Decoupling (service không cần call Kafka sync) |
| **UserTicketBooking** | ✅ Track lịch sử, ✅ Enforce maxPerUser, ✅ Atomic limit check |
| **Redis Lua Script** | ✅ Atomic inventory lock (ACID), ✅ No race condition, ✅ Fast (in-memory) |
| **Dual Write** | ✅ Consistency (Outbox + UserTicketBooking in 1 transaction), ✅ Audit trail |
| **Choreography** | ✅ Loosely coupled (order-service không biết event-ticket), ✅ Scalable |
| **Idempotency Key** | ✅ Prevent duplicates (if Kafka message processed twice) |

---

**Tóm lại:**

1. **user_ticket_booking** = Tracking table để enforce maxPerUser limit + audit trail
2. **Kafka** = Async communication giữa services (ticket → order → payment → notification)
3. **Outbox Pattern** = Guarantee message delivery (No message loss)
4. **Redis Lua** = Atomic inventory lock (prevent oversell)
5. **Dual Write** = Both DB + Kafka in 1 transaction (consistency)

Hiểu rõ kiến trúc này chưa? 😊