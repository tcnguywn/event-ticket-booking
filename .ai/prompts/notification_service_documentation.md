# Notification Service - Tài liệu chi tiết

## Tổng quan & Kiến trúc

- **Vai trò**: `notification-service` là một dịch vụ nghiệp vụ (Business Service) hoạt động nội bộ trên cổng `8085`.

- **Nhiệm vụ chính**: Tạo mã QR và gửi email xác nhận đặt vé cho người dùng.

- **Cơ chế hoạt động**: Thiết kế theo cơ chế **"Fire-and-forget" (Bắn và quên)**. Nghĩa là việc gửi email dù bị chậm cũng tuyệt đối không được làm ảnh hưởng đến luồng xử lý xác nhận đơn hàng chính của hệ thống.

- **Giao tiếp**: Việc tiêu thụ (consume) thông điệp từ Kafka diễn ra hoàn toàn bất đồng bộ (async). Điều này giúp đảm bảo nếu máy chủ mail chậm hoặc consumer bị nghẽn (lag), sự cố sẽ không lây lan sang các topic khác.

- **Khả năng mở rộng**: Việc tách biệt dịch vụ này giúp hệ thống dễ dàng mở rộng (scale) một cách độc lập khi khối lượng thông báo tăng lên.

- **Đặc điểm kiến trúc**: Đây là dịch vụ đơn giản nhất trong toàn bộ hệ thống vì không có cơ sở dữ liệu (DB) riêng và không cung cấp bất kỳ REST endpoint nào ra ngoài.

---

## Công nghệ sử dụng

- **Spring Boot (3.3.x)**: Framework chính, bao gồm bộ khởi tạo web (`spring-boot-starter`).

- **Apache Kafka (`spring-kafka`)**: Dùng để lắng nghe (consume) tin nhắn một cách bất đồng bộ từ các dịch vụ khác.

- **ZXing (`com.google.zxing:core`, `javase`)**: Thư viện dùng để mã hóa `orderId` và tạo hình ảnh mã QR code.

- **JavaMailSender (`spring-boot-starter-mail`)**: Cung cấp công cụ để gửi email xác nhận kèm mã QR qua giao thức SMTP.

- **Thymeleaf (`spring-boot-starter-thymeleaf`)**: Template engine phục vụ việc render giao diện HTML động cho nội dung email.

- **Redis (`spring-boot-starter-data-redis`)**: Đóng vai trò làm bộ nhớ đệm (cache) lưu trữ các khóa tạm thời để kiểm tra tính độc nhất (Idempotency) của tin nhắn.

- **Observability**:
  - `micrometer-registry-prometheus`: Xuất metrics cho Prometheus
  - Grafana
  - Zipkin (`micrometer-tracing-bridge-otel`): theo vết (trace) request trên toàn hệ thống

---

## Cấu trúc thư mục

Package gốc của toàn bộ mã nguồn dịch vụ này là `com.eventticketing.notification`.

```
notification-service/
├── src/main/
│   ├── java/com/eventticketing/notification/
│   │   ├── NotificationApplication.java      # Class main của Spring Boot
│   │   │
│   │   ├── kafka/
│   │   │   └── PaymentCompletedConsumer.java # Kafka listener cho topic payment.completed
│   │   │
│   │   ├── service/
│   │   │   ├── NotificationService.java      # Orchestrator điều phối QR và Email
│   │   │   ├── QRCodeService.java            # Logic tạo QR code bằng ZXing
│   │   │   └── EmailService.java             # Logic gửi email bằng JavaMailSender + Thymeleaf
│   │   │
│   │   ├── idempotency/
│   │   │   └── ProcessedEventCache.java      # Cache Redis check duplicate event
│   │   │
│   │   └── config/
│   │       ├── KafkaConfig.java              # Cấu hình bean cho Kafka
│   │       └── MailConfig.java               # Cấu hình máy chủ gửi thư SMTP
│   │
│   └── resources/
│       ├── application.yml                   # File cấu hình ứng dụng
│       └── templates/
│           └── ticket-confirmation.html      # Template HTML cho email xác nhận
```

---

## Luồng xử lý chi tiết

Quy trình hoạt động được kích hoạt khi dịch vụ nhận được tin nhắn từ Kafka:

### 1. Nhận sự kiện Kafka

Lớp `@KafkaListener` tiêu thụ tin nhắn mang thông tin đặt vé từ topic `payment.completed`. Tin nhắn này được phát ra từ `payment-service` ngay sau khi giao dịch qua cổng thanh toán (VNPay) thành công.

### 2. Kiểm tra tính Idempotency (Tránh gửi lặp email)

Hệ thống khởi tạo một Redis key với định dạng:

```
notif:processed:{idempotencyKey}
```

Việc ghi key sử dụng lệnh `setIfAbsent` kèm theo vòng đời (TTL) giới hạn trong đúng `1 ngày (86400 giây)`.

- Nếu phép ghi trả về `FALSE` (key đã tồn tại), ứng dụng kết luận email đã được gửi đi và dừng xử lý tin nhắn.

**Lưu ý kiến trúc**: Việc lưu trạng thái Idempotency cho Notification không đòi hỏi sự bền vững tuyệt đối (durable) như cơ sở dữ liệu. Nếu máy chủ Redis xảy ra sự cố, trường hợp tồi tệ nhất chỉ là một số người dùng sẽ bị nhận trùng thư xác nhận, đây là mức độ rủi ro có thể chấp nhận được.

### 3. Tạo mã QR (QRCodeService)

- Công cụ `QRCodeWriter` sẽ tiến hành mã hóa giá trị `orderId` thành ma trận điểm ảnh (`BitMatrix`) với chuẩn định dạng QR Code ở kích thước `300x300 pixels`.

- `MatrixToImageWriter` xuất dữ liệu này thành mảng byte (`byte[]`) chuẩn hình ảnh PNG.

### 4. Kết xuất và Gửi Email (EmailService)

- Khởi tạo `MimeMessage` và cấu hình `MimeMessageHelper` với cờ hiển thị định dạng HTML (`true`).

- Truyền dữ liệu (`event name`, `quantity`...) vào biến `Context` của Thymeleaf để biên dịch file template `ticket-confirmation.html` thành chuỗi HTML.

- Mảng byte của hình ảnh mã QR được gắn trực tiếp vào email qua cơ chế ảnh nội tuyến (inline):

```
helper.addInline("qrcode", new ByteArrayResource(qrBytes), "image/png")
```

Trong HTML, ảnh sẽ được gọi thông qua:

```
src="cid:qrcode"
```

- Nếu quy trình gửi mail bị lỗi, hệ thống chỉ ghi nhận log (`log error`) chứ không ném ra exception nhằm tránh việc Kafka consumer chạy lại vòng lặp vô tận.

- Gửi email thành công qua lệnh:

```
mailSender.send(message)
```

---

## Cấu hình (application.yml)

Cấu hình cho SMTP được khai báo cụ thể trong file `application.yml` (ví dụ với Gmail):

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: noreply@example.com
    password: ${MAIL_PASSWORD} # Nên dùng biến môi trường
    properties:
      mail.smtp.starttls.enable: true
```

- **Host**: Địa chỉ máy chủ gửi mail.
- **Port**: Cổng giao tiếp mặc định (`587` cho TLS).
- **Properties**: Cơ chế mã hóa bảo mật `starttls` bắt buộc phải được kích hoạt.

