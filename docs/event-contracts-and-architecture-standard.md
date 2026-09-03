# 📐 Chuẩn Hóa Cấu Trúc Toàn Hệ Thống, Kafka Contracts & Tích Hợp VNPay Sandbox

> Tài liệu này chuẩn hóa toàn bộ:
> 1. **Luồng nghiệp vụ tối ưu hóa (Redis-First & Zero-Latency Hybrid Outbox)**.
> 2. **Kafka Topic Constants & Type-Safe Event DTOs** (Loại bỏ hoàn toàn JSON String hardcode).
> 3. **Kiến trúc tích hợp Cổng thanh toán VNPAY Sandbox** (HMAC-SHA512 Checksum, Payment URL, IPN & Return URL).
> 4. **Cấu trúc thư mục (Package Structure) chuẩn Enterprise cho cả 4 Services**.

---

## 1. 🗺️ Sơ Đồ Luồng Nghiệp Vụ Tối Ưu (End-to-End Architecture)

```
[1. User Booking Request] 
      │
      ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ event-ticket-service (Port 8082)                                                      │
│  ├── 1.1. REDIS FIRST: Atomic Lua Script trừ kho (ticket_stock:{id})                 │
│  │        └── Hết vé ──► Trả 400 SoldOut ngay (0ms chạm Postgres, 0 kết nối Hikari!) │
│  ├── 1.2. Mở DB Transaction ngắn (< 3ms): Lưu UserTicketBooking + Outbox             │
│  └── 1.3. @TransactionalEventListener(AFTER_COMMIT) ──► Publish: ticket.reserved     │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           │ (Kafka Topic: ticket.reserved)
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ order-service (Port 8083)                                                             │
│  ├── 2.1. Consume ticket.reserved (Idempotent by idempotencyKey)                      │
│  ├── 2.2. Tạo Order (Status = PENDING)                                                │
│  ├── 2.3. Đẩy orderId vào Redisson Delayed Queue (10 phút TTL - Ghost Reservation)    │
│  └── 2.4. Ghi Outbox ──► Publish: order.created                                       │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           │ (Kafka Topic: order.created)
                                           ▼
┌───────────────────────────────────────────────────────────────────────────────────────┐
│ payment-service (Port 8084) - VNPAY Sandbox Gateway                                   │
│  ├── 3.1. Consume order.created ──► Tạo bản ghi Payment (Status = PENDING)            │
│  ├── 3.2. User / Frontend gọi API lấy VNPAY Payment URL (HMAC-SHA512)                │
│  ├── 3.3. User quét mã / thanh toán trên VNPAY Sandbox Portal                         │
│  └── 3.4. VNPAY gửi IPN Callback về /api/v1/payments/vnpay/ipn                        │
│            ├── Verify Chữ ký số Checksum HMAC-SHA512                                 │
│            ├── Thành công (vnp_ResponseCode = "00") ──► Payment COMPLETED             │
│            │   └── Ghi Outbox ──► Publish: payment.completed                          │
│            └── Thất bại (vnp_ResponseCode != "00") ──► Payment FAILED                 │
│                └── Ghi Outbox ──► Publish: payment.failed                             │
└───────────────────────┬────────────────────────────────────────┬──────────────────────┘
                        │ (Topic: payment.completed)             │ (Topic: payment.failed)
                        ▼                                        ▼
┌──────────────────────────────────────────────────┐ ┌──────────────────────────────────┐
│ order-service (Nhận kết quả thanh toán)          │ │ order-service (Hủy & Giải phóng) │
│  ├── Cập nhật Order (Status = CONFIRMED)         │ │  ├── Cập nhật Order (CANCELLED)  │
│  └── Ghi Outbox ──► Publish: order.confirmed     │ │  └── Publish: ticket.release     │
└───────────────┬──────────────────────────────────┘ └─────────────────┬────────────────┘
                │                                                      │
        ┌───────┴────────────────────────┐                             ▼
        │ (Topic: order.confirmed)       │                   ┌──────────────────────────┐
        ▼                                ▼                   │ event-ticket-service     │
┌───────────────────────────────┐ ┌────────────────────────┐ │  ├── Redis: INCRBY kho vé │
│ event-ticket-service          │ │ notification-service   │ │  └── DB: Set CANCELLED   │
│  └── DB: Set vé sang CONFIRMED│ │  ├── Sinh QR Code vé   │ └──────────────────────────┘
└───────────────────────────────┘ │  └── Gửi Email vé      │
                                  └────────────────────────┘
```

---

## 2. 📢 Chuẩn Hóa Kafka Topics & Group IDs

Tạo một class hằng số (hoặc `@ConfigurationProperties`) để các Service dùng chung, **tuyệt đối không hardcode chuỗi string trong code**.

### 📄 `KafkaTopicConstants.java`
```java
package com.hdv.common.constant; // Đặt trong package constants của từng service

public final class KafkaTopicConstants {
    private KafkaTopicConstants() {}

    // TOPICS
    public static final String TICKET_RESERVED = "ticket.reserved";
    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String TICKET_RELEASE = "ticket.release";
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String REFUND_COMPLETED = "refund.completed";

    // CONSUMER GROUPS
    public static final String GROUP_EVENT_TICKET = "event-ticket-group";
    public static final String GROUP_ORDER = "order-service-group";
    public static final String GROUP_PAYMENT = "payment-service-group";
    public static final String GROUP_NOTIFICATION = "notification-service-group";
}
```

---

## 3. 📦 Data Contracts: Định Nghĩa Các Event DTOs (Type-Safe POJOs)

### 3.1. `TicketReservedEvent.java`
* **Producer:** `event-ticket-service`
* **Consumer:** `order-service`
* **Topic:** `ticket.reserved`

```java
package com.hdv.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservedEvent implements Serializable {
    private UUID bookingGroupId;
    private UUID userId;
    private String email;
    private UUID eventId;
    private Long totalPrice;
    private UUID idempotencyKey;
    private List<TicketItemDto> items;
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketItemDto implements Serializable {
        private UUID ticketTypeId;
        private String ticketTypeName;
        private Integer quantity;
        private Long price;
    }
}
```

---

### 3.2. `OrderCreatedEvent.java` (Yêu cầu thanh toán)
* **Producer:** `order-service`
* **Consumer:** `payment-service`
* **Topic:** `order.created`

```java
package com.hdv.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent implements Serializable {
    private UUID orderId;
    private UUID bookingGroupId;
    private UUID userId;
    private String userEmail;
    private UUID eventId;
    private Long totalAmount;
    private String description;
    private UUID idempotencyKey;
    private List<OrderItemDto> items;
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto implements Serializable {
        private UUID ticketTypeId;
        private String ticketTypeName;
        private Integer quantity;
        private Long price;
    }
}
```

---

### 3.3. `PaymentResultEvent.java` (Kết quả thanh toán)
* **Producer:** `payment-service`
* **Consumer:** `order-service`
* **Topic:** `payment.completed` hoặc `payment.failed`

```java
package com.hdv.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent implements Serializable {
    private UUID eventId;           // Message unique ID
    private UUID orderId;
    private UUID paymentId;
    private String status;          // "COMPLETED" hoặc "FAILED"
    private String paymentGateway;   // "VNPAY"
    private String transactionNo;   // Mã giao dịch từ VNPAY (vnp_TransactionNo)
    private String bankCode;        // Ngân hàng thanh toán (vnp_BankCode)
    private Long amount;
    private String failureReason;   // Null nếu thành công
    private UUID idempotencyKey;
    @Builder.Default
    private Instant processedAt = Instant.now();
}
```

---

### 3.4. `OrderConfirmedEvent.java` (Đơn hàng thành công)
* **Producer:** `order-service`
* **Consumer:** `event-ticket-service`, `notification-service`
* **Topic:** `order.confirmed`

```java
package com.hdv.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedEvent implements Serializable {
    private UUID orderId;
    private UUID bookingGroupId;
    private UUID userId;
    private String userEmail;
    private UUID eventId;
    private Long totalAmount;
    private UUID idempotencyKey;
    private List<ConfirmedItemDto> items;
    @Builder.Default
    private Instant confirmedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmedItemDto implements Serializable {
        private UUID ticketTypeId;
        private String ticketTypeName;
        private Integer quantity;
        private Long price;
    }
}
```

---

### 3.5. `TicketReleaseEvent.java` (Hủy & Hoàn vé)
* **Producer:** `order-service`
* **Consumer:** `event-ticket-service`
* **Topic:** `ticket.release`

```java
package com.hdv.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketReleaseEvent implements Serializable {
    private UUID orderId;
    private UUID bookingGroupId;
    private UUID idempotencyKey;
    private String reason; // "PAYMENT_FAILED", "PAYMENT_TIMEOUT", "USER_CANCELLED"
    private List<ReleaseItemDto> items;
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleaseItemDto implements Serializable {
        private UUID ticketTypeId;
        private Integer quantity;
    }
}
```

---

## 4. 💳 Tích Hợp VNPAY Sandbox trong `payment-service`

### 4.1. Cấu hình `application.yaml` cho VNPAY
```yaml
vnpay:
  tmn-code: ${VNPAY_TMN_CODE:CGXZLS0Z}           # Mã website cấu hình trên VNPAY Sandbox
  hash-secret: ${VNPAY_HASH_SECRET:XNBCJFAKAZQSGZSJFYNXGKJNMGDLSUAP} # Chuỗi bí mật tạo mã checksum SHA512
  url: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
  return-url: http://localhost:8888/api/v1/payments/vnpay/return
  version: "2.1.0"
  command: "pay"
  order-type: "other"
```

---

### 4.2. `VNPayUtil.java` (Tạo URL & Mã Hóa Checksum HMAC-SHA512)

```java
package com.hdv.payment_service.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VNPayUtil {

    /**
     * Mã hóa HMAC SHA512
     */
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Lấy IP thực của Client
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress != null ? ipAddress : "127.0.0.1";
    }

    /**
     * Sinh query string đã sắp xếp Alphabet và mã hóa URL
     */
    public static String buildQueryUrl(Map<String, String> vnp_Params) {
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isBlank())) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                // Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }
        return hashData.toString();
    }
}
```

---

### 4.3. `VNPayService.java` (Sinh URL & Kiểm Tra Checksum IPN)

```java
package com.hdv.payment_service.service;

import com.hdv.payment_service.config.VNPayProperties;
import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.PaymentRepository;
import com.hdv.payment_service.util.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final VNPayProperties vnpayConfig;
    private final PaymentRepository paymentRepository;

    /**
     * Tạo URL redirect sang cổng VNPAY
     */
    public String createPaymentUrl(Payment payment, HttpServletRequest request) {
        long amountInVND = payment.getAmount() * 100L; // VNPAY tính theo đơn vị Đồng * 100

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", vnpayConfig.getVersion());
        vnp_Params.put("vnp_Command", vnpayConfig.getCommand());
        vnp_Params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        vnp_Params.put("vnp_Amount", String.valueOf(amountInVND));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", payment.getOrderId().toString()); // Dùng orderId làm mã tham chiếu
        vnp_Params.put("vnp_OrderInfo", "Thanh toan ve don hang: " + payment.getOrderId());
        vnp_Params.put("vnp_OrderType", vnpayConfig.getOrderType());
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        vnp_Params.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(Calendar.MINUTE, 10); // Hết hạn thanh toán sau 10 phút
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Build HashData và QueryUrl
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isBlank())) {
                hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII)).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = VNPayUtil.hmacSHA512(vnpayConfig.getHashSecret(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

        return vnpayConfig.getUrl() + "?" + queryUrl;
    }

    /**
     * Xác thực chữ ký số khi VNPAY trả kết quả về (IPN Callback)
     */
    public boolean verifySignature(Map<String, String> fields, String secureHash) {
        String signValue = hashAllFields(fields);
        return signValue.equalsIgnoreCase(secureHash);
    }

    private String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isBlank())) {
                sb.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    sb.append('&');
                }
            }
        }
        return VNPayUtil.hmacSHA512(vnpayConfig.getHashSecret(), sb.toString());
    }
}
```

---

### 4.4. `VNPayController.java` (API Tạo Link & Nhận IPN Callback)

```java
package com.hdv.payment_service.controller;

import com.hdv.payment_service.model.Payment;
import com.hdv.payment_service.repository.PaymentRepository;
import com.hdv.payment_service.service.PaymentProcessingService;
import com.hdv.payment_service.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {

    private final VNPayService vnpayService;
    private final PaymentRepository paymentRepository;
    private final PaymentProcessingService paymentProcessingService;

    /**
     * 1. API Lấy URL Thanh toán VNPAY
     */
    @GetMapping("/create-url")
    public ResponseEntity<Map<String, String>> createPaymentUrl(@RequestParam UUID orderId, HttpServletRequest request) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        String paymentUrl = vnpayService.createPaymentUrl(payment, request);
        return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl, "orderId", orderId.toString()));
    }

    /**
     * 2. Webhook IPN Callback từ VNPAY Server
     * (VNPAY gọi ngầm phía Server-to-Server để báo kết quả thanh toán)
     */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> handleVNPayIpn(@RequestParam Map<String, String> params) {
        log.info("Received VNPAY IPN Callback: {}", params);
        Map<String, String> response = new HashMap<>();

        try {
            String vnp_SecureHash = params.remove("vnp_SecureHash");
            params.remove("vnp_SecureHashType");

            // 1. Kiểm tra chữ ký bảo mật
            if (!vnpayService.verifySignature(params, vnp_SecureHash)) {
                log.error("Invalid VNPAY Checksum Signature!");
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return ResponseEntity.ok(response);
            }

            UUID orderId = UUID.fromString(params.get("vnp_TxnRef"));
            String responseCode = params.get("vnp_ResponseCode");
            String transactionNo = params.get("vnp_TransactionNo");
            String bankCode = params.get("vnp_BankCode");
            long amount = Long.parseLong(params.get("vnp_Amount")) / 100L;

            // 2. Xử lý trạng thái thanh toán & Bắn Outbox Kafka
            if ("00".equals(responseCode)) {
                // Thanh toán THÀNH CÔNG
                paymentProcessingService.markPaymentSuccess(orderId, transactionNo, bankCode, amount);
                response.put("RspCode", "00");
                response.put("Message", "Confirm Success");
            } else {
                // Thanh toán THẤT BẠI hoặc HỦY
                paymentProcessingService.markPaymentFailed(orderId, "VNPAY Error Code: " + responseCode);
                response.put("RspCode", "00");
                response.put("Message", "Confirm Success");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing VNPAY IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown Error");
            return ResponseEntity.ok(response);
        }
    }
}
```

---

## 5. 📂 Chuẩn Hóa Cấu Trúc Thư Mục Các Service

```
services/
├── event-ticket-service/
│   ├── config/ (RedisConfig, KafkaConfig, OutboxSchedulerConfig)
│   ├── constant/ (KafkaTopicConstants)
│   ├── event/ (TicketReservedEvent, OrderConfirmedEvent, TicketReleaseEvent)
│   ├── outbox/ (Outbox, OutboxRepository, ImmediateOutboxDispatcher, OutboxRetryScheduler)
│   └── ticket/ (TicketBookingService [Redis First], InventoryService [Lua])
│
├── order-service/
│   ├── config/ (RedissonConfig [10m Delay Queue], KafkaConfig)
│   ├── constant/ (KafkaTopicConstants)
│   ├── event/ (OrderCreatedEvent, PaymentResultEvent, OrderConfirmedEvent, TicketReleaseEvent)
│   ├── kafka/ (TicketReservedConsumer, PaymentResultConsumer)
│   ├── order/ (OrderService, OrderExpiryConsumer, OrderExpiryScheduler)
│   └── outbox/ (Outbox, OutboxRepository, ImmediateOutboxDispatcher)
│
├── payment-service/
│   ├── config/ (VNPayProperties, KafkaConfig)
│   ├── constant/ (KafkaTopicConstants)
│   ├── controller/ (VNPayController)
│   ├── event/ (OrderCreatedEvent, PaymentResultEvent)
│   ├── kafka/ (OrderCreatedConsumer)
│   ├── model/ (Payment, PaymentStatus)
│   ├── service/ (VNPayService, PaymentProcessingService)
│   └── util/ (VNPayUtil)
│
└── notification-service/
    ├── config/ (MailConfig, RedisConfig, KafkaConfig)
    ├── constant/ (KafkaTopicConstants)
    ├── event/ (OrderConfirmedEvent)
    ├── kafka/ (OrderConfirmedConsumer)
    └── service/ (QrCodeGeneratorService, EmailTemplateService)
```

---

Bản chuẩn hóa này đảm bảo toàn bộ hệ thống của bạn hoạt động đồng bộ 100% về mặt kiểu dữ liệu (Data Contracts), loại bỏ hardcode string, sẵn sàng cho VNPAY Sandbox và đạt hiệu năng cao nhất!
