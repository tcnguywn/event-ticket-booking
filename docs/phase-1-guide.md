# 📖 Hướng Dẫn Triển Khai & Kiểm Thử Phase 1: Infrastructure & Observability

> **Mục tiêu Phase 1:** Thiết lập toàn bộ hạ tầng phân tán và hệ thống giám sát phục vụ cho 5 Microservices:
> 1. **PostgreSQL 16 (Local Host):** Tạo 4 database độc lập cho từng service.
> 2. **Redis 7 (Docker):** In-Memory Cache, Rate Limiting, Redisson 10-min Delayed Queue.
> 3. **Apache Kafka 3.7 (Docker KRaft):** Event Broker kết nối Saga Choreography.
> 4. **Keycloak 24+ (Docker):** Identity Provider tự động import Realm & Tài khoản mẫu.
> 5. **Observability Stack (Docker):** Zipkin (Tracing), Prometheus (Metrics) & Grafana (Dashboard).

---

## 1. 📁 Danh Sách File & Cấu Trúc Hạ Tầng

```
mid-project-596068632/
├── docker-compose.yml              # Điều phối Redis, Kafka, Kafka UI, Keycloak, Zipkin, Prometheus, Grafana & Microservices
├── prometheus.yml                  # Cấu hình Scraper cho 5 Microservices + Prometheus
├── scripts/
│   ├── init-databases.sql          # Script SQL tạo 4 DB trong PostgreSQL local (event_ticket_db, order_db, payment_db, keycloak_db)
│   └── keycloak/
│       └── realm-export.json       # File tự động import Realm "event-ticketing", Client & Users mẫu
└── docs/
    └── phase-1-guide.md            # Tài liệu hướng dẫn này
```

---

## 2. 🧩 Chi Tiết Các Bước Thiết Lập

### Bước 1: Khởi tạo 4 Database trên PostgreSQL Local của bạn

Vì PostgreSQL đã được cài đặt và đang chạy sẵn trên máy Host (Port 5432), bạn cần chạy script [scripts/init-databases.sql](file:///d:/Java/SPRING/mid-project-596068632/scripts/init-databases.sql) bằng **DBeaver**, **pgAdmin**, hoặc **psql CLI** để tạo 4 cơ sở dữ liệu độc lập:

```sql
-- Chạy trên Postgres Local (user: postgres)
CREATE DATABASE event_ticket_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE keycloak_db;
```

> **Cách chạy bằng dòng lệnh psql (PowerShell):**
> ```powershell
> psql -U postgres -h localhost -p 5432 -f scripts/init-databases.sql
> ```

---

### Bước 2: Hiểu Cấu Hình Keycloak Auto-Import (`scripts/keycloak/realm-export.json`)
File [scripts/keycloak/realm-export.json](file:///d:/Java/SPRING/mid-project-596068632/scripts/keycloak/realm-export.json) đã được cấu hình sẵn để Keycloak container tự động import khi khởi động (`start-dev --import-realm`):

- **Realm:** `event-ticketing`
- **Client ID:** `event-ticketing-client` (Public client, bật Direct Access Grants cho Password Flow).
- **Roles:** `ORGANIZER`, `USER`, `ADMIN`.
- **Tài khoản mẫu đã tạo sẵn:**
  - **Tài khoản Organizer:**
    - Username: `organizer`
    - Password: `123`
    - Email: `organizer@ticketing.com`
    - Role: `ORGANIZER`
  - **Tài khoản User (Người mua vé):**
    - Username: `john_doe`
    - Password: `123`
    - Email: `john_doe@example.com`
    - Role: `USER`
- **Admin Console:** `admin` / `admin` (Port 8080).
- **Kết nối DB:** Tự động kết nối tới `keycloak_db` trên máy host qua `host.docker.internal:5432`.

---

### Bước 3: Cấu Hình Prometheus Scraper (`prometheus.yml`)
File [prometheus.yml](file:///d:/Java/SPRING/mid-project-596068632/prometheus.yml) định nghĩa chu kỳ lấy metrics (10s) từ actuator của cả 5 dịch vụ:
- `api-gateway:8888`
- `event-ticket-service:8081`
- `order-service:8083`
- `payment-service:8084`
- `notification-service:8085`

---

## 3. 🚀 Các Bước Khởi Động & Kiểm Thử Hạ Tầng

### Bước 1: Khởi động các dịch vụ Hạ Tầng (Docker)
Chạy lệnh sau tại thư mục gốc:

```powershell
docker compose up -d redis kafka kafka-ui keycloak zipkin prometheus grafana
```

Kiểm tra trạng thái các container:
```powershell
docker compose ps
```

**Bảng trạng thái kỳ vọng:**
| Container | Port Host | Ghi chú |
|---|---|---|
| `ticketing_redis` | `6379` | Cache, Lua stock, Redisson Delay Queue |
| `ticketing_kafka` | `9092` | Apache Kafka KRaft broker |
| `ticketing_kafka_ui` | `8086` | Dashboard giám sát Topics/Messages |
| `ticketing_keycloak` | `8080` | Identity Provider (kết nối Postgres local) |
| `ticketing_zipkin` | `9411` | Distributed Tracing Dashboard |
| `ticketing_prometheus` | `9090` | Thu thập metrics từ Actuator |
| `ticketing_grafana` | `3001` | Dashboard trực quan hóa hiệu năng |

---

### Bước 2: Test Lấy JWT Token từ Keycloak

Sau khi Keycloak khởi động xong (~15-30 giây), test xin Access Token để kiểm tra Realm và User:

#### 1. Lấy Token cho ORGANIZER:
```powershell
curl -X POST "http://localhost:8080/realms/event-ticketing/protocol/openid-connect/token" `
     -H "Content-Type: application/x-www-form-urlencoded" `
     -d "grant_type=password" `
     -d "client_id=event-ticketing-client" `
     -d "username=organizer" `
     -d "password=123"
```

#### 2. Lấy Token cho USER (`john_doe`):
```powershell
curl -X POST "http://localhost:8080/realms/event-ticketing/protocol/openid-connect/token" `
     -H "Content-Type: application/x-www-form-urlencoded" `
     -d "grant_type=password" `
     -d "client_id=event-ticketing-client" `
     -d "username=john_doe" `
     -d "password=123"
```
> **Kiểm tra JWT:** Copy chuỗi `access_token` dán vào trang [jwt.io](https://jwt.io) để kiểm tra các claim `sub`, `email`, và `realm_access.roles`.

---

### Bước 3: Kiểm tra Các Web Dashboard

1. **Kafka UI:** [http://localhost:8086](http://localhost:8086) $\rightarrow$ Xem Topics & Cluster Info.
2. **Keycloak Admin Console:** [http://localhost:8080](http://localhost:8080) $\rightarrow$ Đăng nhập với `admin` / `admin`.
3. **Zipkin Tracing:** [http://localhost:9411](http://localhost:9411) $\rightarrow$ Tìm kiếm trace requests.
4. **Prometheus Targets:** [http://localhost:9090/targets](http://localhost:9090/targets) $\rightarrow$ Xem danh sách endpoint metrics.
5. **Grafana Dashboard:** [http://localhost:3001](http://localhost:3001) $\rightarrow$ Đăng nhập với `admin` / `admin`.

---

## 4. 🛠️ Xử Lý Lỗi Thường Gặp (Troubleshooting)

| Lỗi gặp phải | Nguyên nhân | Cách khắc phục |
|---|---|---|
| **Keycloak không kết nối được Postgres Local** | `host.docker.internal` không phân giải được trên Docker Desktop Windows hoặc Postgres Local không cho phép kết nối ngoại vi. | 1. Kiểm tra file `pg_hba.conf` trên máy local đã mở `host all all 0.0.0.0/0 md5/scram-sha-256` chưa.<br>2. Kiểm tra mật khẩu `DB_PASSWORD` trong file `.env` hoặc `docker-compose.yml` (mặc định là `root`). |
| **Port 8080 / 6379 already in use** | Một ứng dụng khác đang chiếm port trên máy host. | Kiểm tra `netstat -ano | findstr 8080` để xem ứng dụng nào đang chiếm port. |
| **Keycloak import báo lỗi** | File `realm-export.json` mount bị sai quyền hoặc định dạng. | Kiểm tra log chi tiết: `docker logs ticketing_keycloak`. |
