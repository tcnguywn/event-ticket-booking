# Kịch bản Test Hệ thống Đặt vé (Ticketing System)

Tài liệu này hướng dẫn các bước test quy trình từ lúc lấy xác thực (Token) từ Keycloak, khỏi tạo sự kiện, đặt vé và kiểm tra đơn hàng thông qua **API Gateway**. Kịch bản cũng bao gồm hướng dẫn cho trường hợp chưa có `payment-service`.

## 1. Khởi động hệ thống
Mở terminal tại thư mục gốc của dự án và chạy:
```powershell
docker-compose up -d
```
*Lưu ý: Đảm bảo Keycloak, API Gateway, Event Ticket Service, Order Service, Redis và Kafka đều lên thành công.*

## 2. Kiểm tra trạng thái các Service (Health Check)
Đảm bảo các service đã sẵn sàng hoạt động (Gọi trực tiếp do `/health` thường không qua Auth Gateway):

```powershell
# Kiểm tra API Gateway
curl http://localhost:8888/actuator/health

# Kiểm tra Event Ticket Service
curl http://localhost:8081/health

# Kiểm tra Order Service
curl http://localhost:8083/health
```
*(Lưu ý: Tuỳ vào cấu hình expose metrics/health, có thể trả về thông tin UP)*

---

## 3. Quy trình Test (Qua API Gateway)

### Bước 1: Lấy Token từ Keycloak (Đăng nhập)
Để test, chúng ta cần sinh Token từ Keycloak cho 2 vai trò: **ORGANIZER** (Người tạo sự kiện) và **USER** (Người mua vé).
*(Thay đổi `{CLIENT_ID}`, `{USERNAME_ORGANIZER}`, `{PASSWORD_ORGANIZER}`, `{USERNAME_USER}` cho phù hợp với dữ liệu trên Keycloak của bạn).*

**Lấy Token cho ORGANIZER:**
```powershell
curl -X POST "http://localhost:8080/realms/event-ticketing/protocol/openid-connect/token" `
     -H "Content-Type: application/x-www-form-urlencoded" `
     -d "grant_type=password" `
     -d "client_id=event-ticketing-client" `
     -d "username=organizer" `
     -d "password=123"
```
**=> LƯU Ý:** Copy giá trị `"access_token"` trả về. Đặt tên là `$TOKEN_ORGANIZER`.

**Lấy Token cho USER:**
```powershell
curl -X POST "http://localhost:8080/realms/event-ticketing/protocol/openid-connect/token" `
     -H "Content-Type: application/x-www-form-urlencoded" `
     -d "grant_type=password" `
     -d "client_id=event-ticketing-client" `
     -d "username=john_doe" `
     -d "password=123"
```
**=> LƯU Ý:** Copy giá trị `"access_token"` trả về. Đặt tên là `$TOKEN_USER`.

---

### Bước 2: Tạo Sự kiện (Vai trò: ORGANIZER)
Gọi thông qua API Gateway (Cổng 8888). Gateway sẽ tự động trích xuất mã ID và Gắn header `X-User-Id` / `X-User-Role` để đẩy xuống `event-ticket-service`.

```powershell
curl -X POST http://localhost:8888/api/events `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $TOKEN_ORGANIZER" `
  -d '{
    "title": "Đại nhạc hội Rock 2026",
    "description": "Sự kiện âm nhạc lớn nhất năm",
    "location": "Sân vận động Mỹ Đình",
    "startTime": "2026-05-01T19:00:00",
    "endTime": "2026-05-01T23:00:00",
    "ticketTypes": [
      {
        "name": "Vé VIP",
        "price": 2000000,
        "quantity": 100,
        "maxPerUser": 2
      },
      {
        "name": "Vé Thường",
        "price": 500000,
        "quantity": 1000,
        "maxPerUser": 4
      }
    ]
  }'
```
**Quan trọng:** Lưu lại `id` của sự kiện và `id` của loại vé (VIP hoặc Thường) từ Response để dùng cho bước đặt vé.

---

### Bước 3: Đặt vé (Vai trò: USER)
Dùng `$TOKEN_USER` gọi qua API Gateway (8888) lưu ý thay thế `{TICKET_TYPE_ID}`. Service `event-ticket-service` sẽ tự lấy được Email và Role nhờ bộ lọc `AuthenticationFilter`.

```powershell
curl -X POST http://localhost:8888/api/tickets/book `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $TOKEN_USER" `
  -d '{
    "ticketTypeId": "{TICKET_TYPE_ID}",
    "quantity": 2
  }'
```
**Trạng thái trả về:** `PENDING`. Message nhắn rằng đặt vé thành công và chờ thanh toán.

---

### Bước 4: Kiểm tra Đơn hàng (Vai trò: USER)
Sau khi đặt vé, `event-ticket-service` bắn sự kiện qua Kafka cho `order-service` xử lý. Gọi API thông qua Gateway (sẽ tự filter và xác nhận User Id cho `order-service`).

```powershell
curl -H "Authorization: Bearer $TOKEN_USER" `
  http://localhost:8888/api/orders/my
```
**Kết quả mong muốn:** Bạn sẽ thấy danh sách đơn hàng vừa đặt, đi kèm `id` đơn hàng và trạng thái hiện tại là `PENDING`. Lưu lại `{ORDER_ID}`.

---

## 4. Mô phỏng Thanh toán (Vì chưa có payment-service)

Vì hệ thống đang thiếu `payment-service`, đơn hàng sẽ ở trạng thái `PENDING` mãi. Để test tính năng thông báo (Notification/Email), bạn sẽ mô phỏng việc payment-service gửi 1 tin nhắn lên Kafka.

### Cách 1: Sử dụng Kafka UI
1. Mở trình duyệt truy cập: [http://localhost:8086](http://localhost:8086)
2. Chuyển đến mục Topics, tìm topic `payment.completed` (tên topic tùy cấu hình của bạn).
3. Bấm **Produce Message**, điền Key (tuỳ ý) và Value (nội dung JSON thanh toán thành công):
```json
{
  "orderId": "{ORDER_ID}",
  "status": "COMPLETED",
  "amount": 1000000,
  "paymentMethod": "VNPAY"
}
```
Nhấn nút **Produce** để đẩy sự kiện.

### Cách 2: Sử dụng Dòng lệnh (Docker CLI)
```powershell
# Vào container kafka
docker exec -it ticketing_kafka bash

# Chạy lệnh produce
/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic payment.completed
```
Sau đó dán đoạn JSON trên vào và nhấn `Enter`. Nhấn `Ctrl+C` để thoát.

---

### Bước 5: Kiểm tra trạng thái cuối cùng
1. **Order Service:** Check lại lịch sử mua hàng, trạng thái Order phải chuyển thành `CONFIRMED`.
   ```powershell
   curl -H "Authorization: Bearer $TOKEN_USER" http://localhost:8888/api/orders/my
   ```
2. **Notification Service:** Kiểm tra log container để đảm bảo hệ thống đã kích hoạt việc gửi thư thông báo cho Khách hàng.
   ```powershell
   docker logs -f ticketing_notification
   ```
