# 🛡️ Hướng Dẫn Tự Code Phase 2: API Gateway & Security Hardening

> **Mục tiêu Phase 2:** Hoàn thiện tầng bảo mật API Gateway (Zero-Trust Security Gateway). Đảm bảo Gateway đóng vai trò là "chốt chặn duy nhất" (Single Entry Point) kiểm tra JWT, ngăn chặn tấn công giả mạo Header (Header Spoofing), định tuyến chính xác tới 4 backend services, mở quyền cho Webhook PayOS và áp dụng Rate Limiting thông minh.

---

## 1. 📁 Các File Cần Chỉnh Sửa Trong Module `gateway`

```
gateway/src/main/
├── java/com/hdv/api_gateway/config/
│   ├── GatewayConfig.java          # Cấu hình SecurityWebFilterChain & Quyền Public / Protected
│   ├── AuthenticationFilter.java   # GlobalFilter trích xuất JWT, chống Header Spoofing & inject Header
│   └── RateLimiterConfig.java      # KeyResolver: Rate limit theo User ID (khi login) hoặc IP (khi anonymous)
└── resources/
    └── application.yaml            # Định tuyến Routes, Redis Rate Limiting & JWK Set URI
```

---

## 2. 🧩 Hướng Dẫn Chi Tiết Từng Bước Code

### 🛠️ Bước 1: Cập nhật `GatewayConfig.java` (Mở quyền Webhook & Actuator)

#### 🎯 Vấn đề cần giải quyết:
1. Cổng thanh toán PayOS gửi Webhook callback từ server ngoài về endpoint `POST /api/v1/payments/webhook`. Request này **không có token Keycloak**. Nếu không mở `permitAll()`, Gateway sẽ trả về lỗi `401 Unauthorized` khiến PayOS không thể cập nhật trạng thái đơn hàng.
2. Cần mở quyền cho các endpoint Actuator (`/actuator/health`, `/actuator/info`, `/actuator/prometheus`) để hệ thống giám sát hoạt động.

#### 📝 Code mẫu chuẩn cho `GatewayConfig.java`:

```java
package com.hdv.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewayConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(ServerHttpSecurity.CorsSpec::disable) // Hoặc cấu hình CorsConfigurationSource nếu gọi từ Web
            .authorizeExchange(exchange -> exchange
                // 1. Public API xem sự kiện (không cần đăng nhập)
                .pathMatchers("/api/events/public/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/events").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/events/*").permitAll()

                // 2. Public API Webhook PayOS (Bắt buộc mở để PayOS gọi callback)
                .pathMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()

                // 3. Public Healthcheck & Monitoring Actuator
                .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()

                // 4. Mọi API còn lại (Đặt vé, Tạo đơn, Thanh toán...) bắt buộc phải có JWT hợp lệ
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
```

---

### 🛠️ Bước 2: Nâng cấp `AuthenticationFilter.java` (Chống Header Spoofing)

#### 🎯 Vấn đề an ninh (Security Vulnerability):
Kẻ xấu có thể gửi request kèm các header tự chế:
`X-User-Id: <uuid-cua-admin>` hoặc `X-User-Role: ORGANIZER`

Nếu Gateway chỉ đơn thuần đính kèm thêm header mà **không xóa header do client gửi lên từ trước**, hoặc nếu là request public mà không xóa header giả mạo, backend service sẽ đọc phải thông tin giả mạo này $\rightarrow$ **Bị chiếm quyền (Privilege Escalation)**.

#### 💡 Thuật toán xử lý:
1. **Bước 1:** Luôn luôn xóa sạch các header `X-User-Id`, `X-User-Email`, `X-User-Role` từ request gốc của client.
2. **Bước 2:** Lấy `Principal` đã được Spring Security xác thực chữ ký (JWT).
3. **Bước 3:** Nếu có JWT $\rightarrow$ Đọc `sub` (User ID), `email`, và `realm_access.roles` $\rightarrow$ Inject lại các header an toàn.
4. **Bước 4:** Nếu không có JWT (request public) $\rightarrow$ Chuyển tiếp request đã được làm sạch (strip headers).

#### 📝 Code mẫu chuẩn cho `AuthenticationFilter.java`:

```java
package com.hdv.api_gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // BƯỚC 1: XÓA SẠCH CÁC HEADER NHẠY CẢM DO CLIENT GỬI LÊN (Chống Header Spoofing)
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Role");
                })
                .build();

        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        // BƯỚC 2: KIỂM TRA JWT TỪ SPRING SECURITY CONTEXT
        return sanitizedExchange.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {
                    Jwt jwt = jwtAuth.getToken();

                    // 1. Trích xuất User ID (sub claim)
                    String userId = jwt.getSubject();

                    // 2. Trích xuất Email
                    String email = jwt.getClaimAsString("email");
                    if (email == null || email.isBlank()) {
                        email = "unknown@example.com";
                    }

                    // 3. Trích xuất Role từ realm_access
                    String roleString = extractRole(jwt.getClaimAsMap("realm_access"));

                    // 4. Inject các header đã được xác thực an toàn xuống backend
                    ServerHttpRequest authenticatedRequest = sanitizedExchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email)
                            .header("X-User-Role", roleString)
                            .build();

                    return sanitizedExchange.mutate().request(authenticatedRequest).build();
                })
                // Nếu là Public API (không có Token), tiếp tục dùng request đã được làm sạch
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    private String extractRole(Map<String, Object> realmAccess) {
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles != null && !roles.isEmpty()) {
                if (roles.contains("ORGANIZER") || roles.contains("organizer")) return "ORGANIZER";
                if (roles.contains("ADMIN") || roles.contains("admin")) return "ADMIN";
                if (roles.contains("USER") || roles.contains("user")) return "USER";
                return roles.get(0).toUpperCase();
            }
        }
        return "USER";
    }

    @Override
    public int getOrder() {
        // Chạy ngay sau khi Spring Security xác thực JWT (Order cao hơn -100)
        return -1;
    }
}
```

---

### 🛠️ Bước 3: Cải tiến `RateLimiterConfig.java` (IP Fallback)

#### 🎯 Vấn đề:
Nếu cấu hình `.defaultIfEmpty("anonymous")`, tất cả khách vãng lai (chưa đăng nhập) vào xem sự kiện sẽ bị **dùng chung một hạn ngạch (1 key Redis duy nhất là "anonymous")**. Khi 1 người spam F5, toàn bộ người dùng khác sẽ bị văng lỗi `429 Too Many Requests`.

#### 💡 Thuật toán:
* Nếu đã đăng nhập $\rightarrow$ Dùng `user_<sub_uuid>` làm Redis key.
* Nếu chưa đăng nhập $\rightarrow$ Lấy địa chỉ IP của Client (`ip_<client_ip>`) làm Redis key.

#### 📝 Code mẫu chuẩn cho `RateLimiterConfig.java`:

```java
package com.hdv.api_gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(principal -> principal instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> "user_" + jwtAuth.getToken().getSubject())
                .switchIfEmpty(Mono.defer(() -> Mono.just(resolveClientIpKey(exchange))));
    }

    private String resolveClientIpKey(ServerWebExchange exchange) {
        // Ưu tiên đọc từ X-Forwarded-For nếu đi qua Load Balancer / Proxy
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip_" + forwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip_" + remoteAddress.getAddress().getHostAddress();
        }

        return "ip_anonymous";
    }
}
```

---

## 3. 🧪 Kịch Bản Kiểm Thử An Ninh (Verification Test Cases)

Sau khi bạn code xong 3 file trên trong `gateway`, hãy khởi động Gateway và chạy các kịch bản test sau:

### Test 1: Chống giả mạo Header (Header Spoofing Test)
Gửi request có token của `john_doe` (role `USER`) nhưng cố tình đính kèm header `X-User-Role: ORGANIZER`:

```powershell
# 1. Lấy token của john_doe (Role: USER)
$TOKEN_USER = (curl -X POST "http://localhost:8080/realms/event-ticketing/protocol/openid-connect/token" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=password" `
  -d "client_id=event-ticketing-client" `
  -d "username=john_doe" `
  -d "password=123" | ConvertFrom-Json).access_token

# 2. Gửi request qua Gateway kèm header lậu
curl -i -X GET "http://localhost:8888/api/tickets/my-bookings" `
  -H "Authorization: Bearer $TOKEN_USER" `
  -H "X-User-Role: ORGANIZER" `
  -H "X-User-Id: 00000000-0000-0000-0000-000000000000"
```
✅ **Kết quả kỳ vọng:** Khi log trong service `event-ticket-service`, header nhận được phải là User ID thật của `john_doe` và `X-User-Role` là `USER`, không phải giá trị lậu `ORGANIZER`.

---

### Test 2: Kiểm tra Endpoint Webhook PayOS (Không cần Token)
```powershell
curl -i -X POST "http://localhost:8888/api/v1/payments/webhook" `
  -H "Content-Type: application/json" `
  -d '{"code":"00","desc":"test","data":{},"signature":"invalid"}'
```
✅ **Kết quả kỳ vọng:** Trả về kết quả từ `payment-service` (hoặc 401 do sai chữ ký số PayOS, **tuyệt đối KHÔNG phải lỗi 401 từ Spring Security của Gateway**).

---

### Test 3: Kiểm tra Rate Limiting (Redis Token Bucket)
Dùng lệnh spam liên tục 5 request tạo đơn hàng qua Gateway (`replenishRate: 3`, `burstCapacity: 3`):

```powershell
1..5 | ForEach-Object {
    curl -o NUL -s -w "%{http_code}`n" -X POST "http://localhost:8888/api/orders" `
      -H "Authorization: Bearer $TOKEN_USER" `
      -H "Content-Type: application/json" `
      -d '{"eventId":"test"}'
}
```
✅ **Kết quả kỳ vọng:** Các request thứ 4, thứ 5 sẽ trả về HTTP status code **`429 Too Many Requests`**.

---

## 4. 🎯 Checklist Hoàn Thành Phase 2

- [ ] `GatewayConfig.java` đã mở `permitAll()` cho Webhook `/api/v1/payments/webhook` và Actuator.
- [ ] `AuthenticationFilter.java` đã xóa header trước khi trích xuất JWT.
- [ ] `RateLimiterConfig.java` đã cấu hình `user_<id>` hoặc `ip_<ip>`.
- [ ] Chạy thành công 3 bài test an ninh trên.

👉 Bạn hãy tiến hành code theo hướng dẫn trên. Khi hoàn thành hoặc có thắc mắc, hãy nhắn cho tôi để cùng kiểm tra nhé!
