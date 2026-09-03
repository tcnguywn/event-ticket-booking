import { request } from '../api.js';
import { State } from '../state.js';
import { CONFIG } from '../config.js';
import { formatVND, formatDate } from '../utils.js';

export async function initOrdersView(initialBookingGroupId = null) {
    const container = document.getElementById('orders-view');
    if (!container) return;

    container.innerHTML = `
        <div class="grid-2">
            <!-- Left: Order List -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">📦 Đơn Hàng Của Bạn</span>
                        <button class="btn btn-secondary btn-sm" id="btn-refresh-orders">Làm Mới</button>
                    </div>
                    <div id="orders-list-table">
                        <div style="text-align: center; padding: 24px; color: var(--text-muted);">
                            Đang tải lịch sử đơn hàng...
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right: Order Details & Payment Sandbox -->
            <div>
                <div class="card" style="position: sticky; top: 76px;">
                    <div class="card-header">
                        <span class="card-title">💳 Giả Lập Thanh Toán VNPay Sandbox</span>
                        <span class="badge badge-success">Saga Verification</span>
                    </div>

                    <div id="order-detail-content">
                        <div class="form-group">
                            <label>Mã Đơn Hàng (Order ID / Booking Group ID)</label>
                            <input type="text" id="pay-order-id" value="${initialBookingGroupId || ''}" placeholder="Nhập hoặc chọn đơn hàng từ danh sách">
                        </div>

                        <div style="background: var(--bg-subtle); border-radius: var(--radius-sm); padding: 12px; margin-bottom: 16px; font-size: 13px;">
                            <div style="margin-bottom: 6px;"><b>Cổng thanh toán:</b> VNPay Sandbox (HMAC-SHA512)</div>
                            <div style="margin-bottom: 6px;"><b>Cơ chế Saga:</b> Sau khi thanh toán, Kafka Event bắn sang Notification Service sinh vé QR.</div>
                            <div><b>Hòm thư MailHog:</b> <a href="${CONFIG.MAILHOG_WEB_URL}" target="_blank" style="color: var(--primary);">Mở http://localhost:8025 ↗</a></div>
                        </div>

                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-bottom: 12px;">
                            <button class="btn btn-success" id="btn-pay-success">
                                ✅ Thanh Toán Thành Công (00)
                            </button>
                            <button class="btn btn-danger" id="btn-pay-fail">
                                ❌ Thanh Toán Thất Bại (99)
                            </button>
                        </div>

                        <div style="border-top: 1px solid var(--border); padding-top: 12px; font-size: 12px; color: var(--text-muted);">
                            <i>Mẹo phỏng vấn: Khi thanh toán thất bại, hệ thống tự kích hoạt Compensating Transaction qua topic <code>ticket.release</code> để trả lại vé về kho.</i>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('btn-refresh-orders').addEventListener('click', fetchUserOrders);
    document.getElementById('btn-pay-success').addEventListener('click', () => executeMockPayment(true));
    document.getElementById('btn-pay-fail').addEventListener('click', () => executeMockPayment(false));

    await fetchUserOrders();
}

export async function fetchUserOrders() {
    const listContainer = document.getElementById('orders-list-table');
    if (!listContainer) return;

    try {
        const orders = await request('/api/orders', {}, 'order');
        if (!orders || orders.length === 0) {
            listContainer.innerHTML = `
                <div style="text-align: center; padding: 32px; color: var(--text-muted);">
                    Bạn chưa có đơn hàng nào. Hãy sang tab <b>Sơ Đồ & Đặt Vé</b> để trải nghiệm.
                </div>
            `;
            return;
        }

        listContainer.innerHTML = `
            <div class="table-container">
                <table>
                    <thead>
                        <tr>
                            <th>Mã Đơn</th>
                            <th>Ngày Tạo</th>
                            <th>Tổng Tiền</th>
                            <th>Trạng Thái</th>
                            <th>Thao Tác</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${orders.map(o => {
                            let badgeClass = 'badge-warning';
                            if (o.status === 'CONFIRMED') badgeClass = 'badge-success';
                            if (o.status === 'CANCELLED') badgeClass = 'badge-danger';

                            return `
                                <tr>
                                    <td style="font-family: var(--font-mono); font-size: 11px;">${o.id.substring(0, 8)}...</td>
                                    <td>${formatDate(o.createdAt)}</td>
                                    <td><b>${formatVND(o.totalPrice)}</b></td>
                                    <td><span class="badge ${badgeClass}">${o.status}</span></td>
                                    <td>
                                        <button class="btn btn-secondary btn-sm" onclick="document.getElementById('pay-order-id').value = '${o.id}'">
                                            Chọn
                                        </button>
                                    </td>
                                </tr>
                            `;
                        }).join('')}
                    </tbody>
                </table>
            </div>
        `;

    } catch (err) {
        listContainer.innerHTML = `
            <div style="padding: 16px; background: var(--danger-light); color: var(--danger); border-radius: var(--radius-sm); font-size: 12px;">
                Lỗi tải đơn hàng: ${err.message}
            </div>
        `;
    }
}

async function executeMockPayment(isSuccess) {
    const orderId = document.getElementById('pay-order-id').value.trim();
    if (!orderId) {
        alert('Vui lòng nhập hoặc chọn mã đơn hàng!');
        return;
    }

    const responseCode = isSuccess ? '00' : '99';
    try {
        const url = `/api/v1/payments/vnpay/return?vnp_TxnRef=${orderId}&vnp_ResponseCode=${responseCode}`;
        const res = await request(url, { method: 'GET' }, 'payment');

        if (isSuccess) {
            alert(`🎉 Thanh toán thành công cho đơn ${orderId}! Vé đã gửi về MailHog (port 8025).`);
        } else {
            alert(`⚠️ Thanh toán thất bại! Saga đã kích hoạt bồi hoàn và hoàn kho vé.`);
        }
        await fetchUserOrders();

    } catch (err) {
        alert(`Lỗi thực hiện thanh toán VNPay: ${err.message}`);
    }
}
