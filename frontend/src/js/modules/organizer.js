import { request } from '../api.js';
import { State } from '../state.js';
import { CONFIG } from '../config.js';
import { formatVND, formatDate } from '../utils.js';

export function initOrganizerView() {
    const container = document.getElementById('organizer-view');
    if (!container) return;

    container.innerHTML = `
        <div class="grid-2">
            <!-- Left: Create New Event Form -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">🎪 Khởi Tạo Sự Kiện Mới (Organizer Studio)</span>
                        <span class="badge badge-success">Outbox + ES Sync</span>
                    </div>

                    <form id="create-event-form">
                        <div class="form-group">
                            <label>Tên sự kiện</label>
                            <input type="text" id="ev-title" required value="Concert Anh Trai Say Hi 2026">
                        </div>
                        <div class="form-group">
                            <label>Mô tả chi tiết</label>
                            <textarea id="ev-desc" rows="2">Đêm nhạc hội quy tụ 30 nghệ sĩ hàng đầu Việt Nam.</textarea>
                        </div>
                        <div class="grid-2">
                            <div class="form-group">
                                <label>Địa điểm</label>
                                <input type="text" id="ev-location" value="Ha Noi Stadium">
                            </div>
                            <div class="form-group">
                                <label>Giới hạn vé / người</label>
                                <input type="number" id="ev-max-tickets" value="4">
                            </div>
                        </div>
                        <div class="grid-2">
                            <div class="form-group">
                                <label>Thời gian bắt đầu</label>
                                <input type="datetime-local" id="ev-start" value="2026-11-20T19:00">
                            </div>
                            <div class="form-group">
                                <label>Thời gian kết thúc</label>
                                <input type="datetime-local" id="ev-end" value="2026-11-20T23:00">
                            </div>
                        </div>

                        <div style="background: var(--bg-subtle); padding: 12px; border-radius: var(--radius-sm); margin-bottom: 16px;">
                            <div style="font-weight: 600; font-size: 12px; margin-bottom: 8px;">Cấu hình vé khởi tạo (Tự động nạp kho Redis)</div>
                            <div class="grid-2">
                                <div>
                                    <label style="font-size: 11px; color: var(--text-muted);">Vé VIP Diamond (1.500.000 đ)</label>
                                    <input type="number" id="ev-vip-qty" value="100">
                                </div>
                                <div>
                                    <label style="font-size: 11px; color: var(--text-muted);">Vé GA Sân Cỏ (650.000 đ)</label>
                                    <input type="number" id="ev-ga-qty" value="1000">
                                </div>
                            </div>
                        </div>

                        <button type="submit" class="btn btn-primary" style="width: 100%;">
                            Tạo Sự Kiện & Xuất Bản Lên Hệ Thống
                        </button>
                    </form>
                </div>
            </div>

            <!-- Right: Sales Report Viewer (Point-in-Time Cached Report) -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">📊 Báo Cáo Doanh Thu Bán Vé (Cached Sales Report)</span>
                        <button class="btn btn-secondary btn-sm" id="btn-refresh-report">Tải Báo Cáo</button>
                    </div>

                    <div class="form-group">
                        <label>Chọn Sự Kiện Cần Xem Báo Cáo</label>
                        <select id="report-event-id">
                            <option value="${CONFIG.SAMPLE_EVENT_ID}">Concert World Tour 2026 (Mặc định)</option>
                        </select>
                    </div>

                    <div id="report-stats-grid" class="grid-2" style="margin-bottom: 16px;">
                        <div class="stat-box">
                            <div class="stat-box-title">Tổng Vé Đã Bán</div>
                            <div class="stat-box-value" id="stat-tickets-sold">0</div>
                        </div>
                        <div class="stat-box">
                            <div class="stat-box-title">Tổng Doanh Thu</div>
                            <div class="stat-box-value" id="stat-revenue" style="color: var(--primary);">0 đ</div>
                        </div>
                    </div>

                    <div id="report-cache-note" style="padding: 10px; background: var(--bg-subtle); border-radius: var(--radius-sm); font-size: 12px; color: var(--text-muted);">
                        ℹ️ <i>Dữ liệu báo cáo được tối ưu bằng Redis Cache (TTL 10 phút) để tránh quá tải DB khi hàng triệu người dùng tra cứu.</i>
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('create-event-form').addEventListener('submit', handleCreateEvent);
    document.getElementById('btn-refresh-report').addEventListener('click', fetchSalesReport);

    // Initial report load
    fetchSalesReport();
}

async function handleCreateEvent(e) {
    e.preventDefault();

    const title = document.getElementById('ev-title').value.trim();
    const description = document.getElementById('ev-desc').value.trim();
    const location = document.getElementById('ev-location').value.trim();
    const maxTickets = parseInt(document.getElementById('ev-max-tickets').value) || 4;
    const startTime = document.getElementById('ev-start').value + ':00';
    const endTime = document.getElementById('ev-end').value + ':00';

    const vipQty = parseInt(document.getElementById('ev-vip-qty').value) || 100;
    const gaQty = parseInt(document.getElementById('ev-ga-qty').value) || 1000;

    const payload = {
        title,
        description,
        location,
        maxTicketsPerUser: maxTickets,
        startTime,
        endTime,
        ticketTypes: [
            {
                name: 'VIP Diamond',
                price: 1500000,
                quantity: vipQty,
                maxOrderQuantity: 4
            },
            {
                name: 'Standard GA',
                price: 650000,
                quantity: gaQty,
                maxOrderQuantity: 4
            }
        ]
    };

    try {
        const result = await request('/api/events', {
            method: 'POST',
            body: JSON.stringify(payload)
        }, 'event');

        alert(`🎉 Sự kiện "${title}" đã được tạo thành công!\nID: ${result.id}\nĐã đồng bộ sang Elasticsearch và nạp kho Redis.`);
        
        // Add to dropdown
        const select = document.getElementById('report-event-id');
        if (select) {
            const opt = document.createElement('option');
            opt.value = result.id;
            opt.innerText = `${title} (${result.id.substring(0, 8)}...)`;
            select.appendChild(opt);
            select.value = result.id;
        }

    } catch (err) {
        alert(`Lỗi tạo sự kiện: ${err.message}`);
    }
}

async function fetchSalesReport() {
    const select = document.getElementById('report-event-id');
    const eventId = select ? select.value : CONFIG.SAMPLE_EVENT_ID;

    const statTickets = document.getElementById('stat-tickets-sold');
    const statRevenue = document.getElementById('stat-revenue');

    try {
        const report = await request(`/api/orders/reports/sales/${eventId}`, {}, 'order');
        if (statTickets) statTickets.innerText = report.totalTicketsSold != null ? report.totalTicketsSold : 0;
        if (statRevenue) statRevenue.innerText = formatVND(report.totalRevenue || 0);

    } catch (err) {
        if (statTickets) statTickets.innerText = '-';
        if (statRevenue) statRevenue.innerText = '-';
    }
}
