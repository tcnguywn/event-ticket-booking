CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP   NOT NULL,
    locked_at  TIMESTAMP   NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- 2. TẠO PARTIAL INDEX CHO BẢNG ORDERS
-- Tối ưu hóa cực độ cho OrderExpiryScheduler: Thay vì quét toàn bộ database,
-- index này giúp DB chỉ tìm kiếm trên những đơn hàng đang ở trạng thái 'PENDING'.
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status, created_at) WHERE status = 'PENDING';

-- 3. TẠO PARTIAL INDEX CHO BẢNG OUTBOX
-- Tối ưu hóa cho OutboxPoller: Giúp câu query quét hàng loạt diễn ra mượt mà, không gây thắt cổ chai cho database.
CREATE INDEX IF NOT EXISTS idx_outbox_status_pending ON outbox(status) WHERE status = 'PENDING';

-- 4. TẠO INDEX CHO BẢNG OUTBOX (Phục vụ truy vấn event_id)
CREATE INDEX IF NOT EXISTS idx_outbox_event_id ON outbox(event_id);

-- 5. TẠO INDEX CHO BẢNG ORDER_ITEMS
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);