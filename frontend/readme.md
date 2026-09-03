# TicketFlow — Minimalist Frontend & Simulator Service

Giao diện Web tối giản (Minimalist UI) và Trung tâm Giả lập Kịch bản Test phân tán cho Hệ thống Đặt vé High-Concurrency.

---

## 1. Triết Lý Thiết Kế (Design Philosophy)
* **Minimalist & Lightweight:** Không dùng framework cồng kềnh, không bundle phình to, giao diện nhẹ, tải tức thì (<100ms).
* **Clean & High Contrast:** Bố cục rõ ràng, typography chuẩn, phân biệt trạng thái bằng các badge tinh gọn (`Emerald` cho Thành Công, `Amber` cho Giữ Chỗ/Cảnh Báo, `Rose` cho Lỗi/Bán Hết).
* **Developer-Friendly & Live Debug:** Tích hợp sẵn thanh **Live Telemetry & API Debug Stream** ở cạnh dưới màn hình, hiển thị trực tiếp Endpoint, Method, Payload, HTTP Status và Latency (ms) của mọi request.

---

## 2. Các Tính Năng & Module Chính

### 🔍 1. Khám Phá Sự Kiện (Elasticsearch Engine)
* Tích hợp tìm kiếm đa tiêu chí (Faceted Search) qua endpoint `/api/events/search`.
* Tìm kiếm mờ (Fuzzy Text Search) theo tên ca sĩ, sự kiện, địa điểm và lọc theo khoảng giá, danh mục.

### 🎪 2. Sơ Đồ Ghế & Đặt Chỗ (Dual-Model Ticketing)
* **Model A (Numbered Seats):** Sơ đồ ma trận ghế trực quan (Row A, B, C). Nhấp chọn ghế để giữ chỗ tạm thời trong 10 phút trên Redis.
* **Model B (Standing Zone / Flash Sale):** Vé đứng Fanzone, kiểm soát trừ kho nguyên tử bằng Redis Lua script (Zero Oversell).
* **Phòng Chờ Ảo (Virtual Waiting Room):** Tự động hiển thị vị trí xếp hàng và thời gian đếm ngược nếu hệ thống vượt quá 100 req/s.

### 📦 3. Quản Lý Đơn Hàng & Giả Lập Thanh Toán (VNPay Sandbox)
* Xem danh sách đơn hàng của người dùng (`PENDING`, `CONFIRMED`, `CANCELLED`).
* Giả lập phản hồi IPN từ cổng thanh toán VNPay:
  * Mã `00` (Thành công) $\rightarrow$ Hoàn tất đơn hàng, Kafka phát sinh vé QR gửi về MailHog.
  * Mã `99` (Thất bại) $\rightarrow$ Kích hoạt Saga Compensation bồi hoàn và hoàn kho vé.
* Liên kết nhanh tới hòm thư MailHog cục bộ (`http://localhost:8025`).

### 📊 4. Ban Tổ Chức (Organizer Studio & Cached Sales Report)
* Khởi tạo sự kiện mới, tự động nạp kho vé vào Redis và đồng bộ tài liệu sang Elasticsearch.
* Báo cáo doanh thu và tổng số vé bán ra tại 1 thời điểm (Point-in-Time Cached Report qua Redis TTL 10 phút).

### 🎟️ 5. Soát Vé Sân Vận Động (Staff Check-In Engine)
* Quét mã vé và chữ ký điện tử HMAC-SHA256 ngoại tuyến.
* Chống gian lận quét trùng (Anti-Fraud Double Scan) qua Redis `SET NX`.

### 🧪 6. Trung Tâm Giả Lập Kịch Bản Test (Distributed Benchmark)
Mô phỏng trực quan 4 kịch bản kỹ thuật cao:
1. **Flash Sale Zero-Oversell:** Bắn đồng thời 20/50/100 requests song song, đo lường RPS và chứng minh không bán lố vé.
2. **Race Condition Ghế:** 2 user cùng tranh mua 1 ghế trong cùng 1 mili-giây $\rightarrow$ 1 thành công (HOLD), 1 nhận 409 Conflict.
3. **Timeout 10 Phút & Saga Compensation:** Đặt vé $\rightarrow$ mô phỏng quá hạn thanh toán $\rightarrow$ vé và ghế tự động nhả về kho.
4. **Chống Quét Vé 2 Lần:** Quét vé lần 1 hợp lệ $\rightarrow$ quét lần 2 cảnh báo đỏ tức thì.

---

## 3. Cấu Trúc Thư Mục

```
frontend/
├── Dockerfile              # Docker container Nginx Alpine siêu nhẹ
├── nginx.conf              # Reverse proxy cho API Gateway và SPA routing
├── package.json            # Script chạy dev cục bộ
├── readme.md               # Tài liệu hướng dẫn
└── src/
    ├── index.html          # SPA Container & Navigation
    ├── css/
    │   └── style.css       # Minimalist Design Tokens & Styling
    └── js/
        ├── config.js       # Địa chỉ Gateway, Services và Sample IDs
        ├── state.js        # Reactive State & Role management
        ├── api.js          # Fetch client chuẩn hóa kèm Logger & Token Relay
        ├── utils.js        # Format tiền tệ, ngày tháng, Web Crypto HMAC-SHA256
        ├── modules/
        │   ├── events.js   # Danh sách và tìm kiếm Elasticsearch
        │   ├── booking.js  # Sơ đồ ghế & Đặt vé Dual-Model
        │   ├── orders.js   # Đơn hàng & Giả lập VNPay
        │   ├── organizer.js# Tạo sự kiện & Báo cáo doanh thu
        │   ├── checkin.js  # Soát vé cửa vào & chống quét 2 lần
        │   └── simulator.js# Giả lập 4 kịch bản benchmark
        └── app.js          # Entrypoint & Header controls
```

---

## 4. Hướng Dẫn Chạy

### Cách 1: Chạy bằng Docker Compose (Khuyến nghị)
Từ thư mục gốc dự án:
```powershell
docker compose up frontend -d --build
```
Mở trình duyệt truy cập: **`http://localhost:3000`**

### Cách 2: Chạy trực tiếp qua Local Static Server
Từ thư mục `frontend/`:
```powershell
npm run dev
# hoặc: npx serve src -p 3000
```
Hoặc mở trực tiếp file `frontend/src/index.html` trong bất kỳ trình duyệt hiện đại nào (Chrome, Edge, Firefox).