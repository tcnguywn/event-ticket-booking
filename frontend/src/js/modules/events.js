import { request } from '../api.js';
import { State } from '../state.js';
import { formatVND, formatDate } from '../utils.js';

export async function initEventsView() {
    const container = document.getElementById('events-view');
    if (!container) return;

    container.innerHTML = `
        <div class="card" style="margin-bottom: 20px;">
            <div class="card-header">
                <span class="card-title">🔍 Tìm Kiếm & Khám Phá Sự Kiện (Elasticsearch Engine)</span>
                <span class="badge badge-success">Elasticsearch 8.11 Sync</span>
            </div>
            <div style="display: grid; grid-template-columns: 2fr 1fr 1fr 1fr auto; gap: 12px; align-items: flex-end;">
                <div class="form-group" style="margin-bottom: 0;">
                    <label>Từ khóa tìm kiếm (Fuzzy Match)</label>
                    <input type="text" id="search-keyword" placeholder="Nhập tên ca sĩ, concert, sự kiện...">
                </div>
                <div class="form-group" style="margin-bottom: 0;">
                    <label>Địa điểm</label>
                    <select id="search-location">
                        <option value="">Tất cả địa điểm</option>
                        <option value="Ha Noi Stadium">Hà Nội (Mỹ Đình)</option>
                        <option value="Ho Chi Minh City">TP. Hồ Chí Minh</option>
                        <option value="Da Nang">Đà Nẵng</option>
                    </select>
                </div>
                <div class="form-group" style="margin-bottom: 0;">
                    <label>Danh mục</label>
                    <select id="search-category">
                        <option value="">Tất cả danh mục</option>
                        <option value="MUSIC">Âm Nhạc (MUSIC)</option>
                        <option value="SPORTS">Thể Thao (SPORTS)</option>
                        <option value="WORKSHOP">Hội thảo</option>
                    </select>
                </div>
                <div class="form-group" style="margin-bottom: 0;">
                    <label>Giá tối đa</label>
                    <select id="search-max-price">
                        <option value="">Mọi mức giá</option>
                        <option value="500000">Dưới 500.000 đ</option>
                        <option value="1000000">Dưới 1.000.000 đ</option>
                        <option value="2000000">Dưới 2.000.000 đ</option>
                    </select>
                </div>
                <div>
                    <button class="btn btn-primary" id="btn-search-events">
                        Tìm Kiếm
                    </button>
                </div>
            </div>
        </div>

        <div id="events-list-container" class="grid-3">
            <div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--text-muted);">
                Đang tải danh sách sự kiện từ API...
            </div>
        </div>
    `;

    document.getElementById('btn-search-events').addEventListener('click', () => fetchEvents(true));
    document.getElementById('search-keyword').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') fetchEvents(true);
    });

    // Initial load
    await fetchEvents(false);
}

export async function fetchEvents(isSearchMode = false) {
    const listContainer = document.getElementById('events-list-container');
    if (!listContainer) return;

    try {
        let events = [];
        if (isSearchMode) {
            const keyword = document.getElementById('search-keyword').value.trim();
            const location = document.getElementById('search-location').value;
            const category = document.getElementById('search-category').value;
            const maxPrice = document.getElementById('search-max-price').value;

            const params = new URLSearchParams();
            if (keyword) params.append('keyword', keyword);
            if (location) params.append('location', location);
            if (category) params.append('category', category);
            if (maxPrice) params.append('maxPrice', maxPrice);

            events = await request(`/api/events/search?${params.toString()}`, {}, 'event');
        } else {
            events = await request('/api/events', {}, 'event');
        }

        State.eventsList = events;
        renderEventsList(events);

    } catch (err) {
        listContainer.innerHTML = `
            <div style="grid-column: 1/-1; padding: 24px; background: var(--danger-light); border: 1px solid #fecaca; border-radius: var(--radius); color: var(--danger);">
                <b>Không thể tải sự kiện:</b> ${err.message}. Đảm bảo API Gateway (:8888) hoặc Event Service (:8082) đang chạy.
            </div>
        `;
    }
}

function renderEventsList(events) {
    const container = document.getElementById('events-list-container');
    if (!container) return;

    if (!events || events.length === 0) {
        container.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 48px; background: var(--bg-surface); border: 1px dashed var(--border); border-radius: var(--radius); color: var(--text-muted);">
                Không tìm thấy sự kiện nào phù hợp với bộ lọc.
            </div>
        `;
        return;
    }

    container.innerHTML = events.map(event => {
        const ticketTypes = event.ticketTypes || [];
        const minPrice = ticketTypes.length > 0 
            ? Math.min(...ticketTypes.map(t => t.price)) 
            : (event.minPrice || 0);

        return `
            <div class="card" style="display: flex; flex-direction: column; justify-content: space-between;">
                <div>
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px;">
                        <span class="badge badge-success">${event.status || 'ON_SALE'}</span>
                        <span style="font-size: 11px; color: var(--text-muted);">${event.category || 'MUSIC'}</span>
                    </div>
                    <h3 style="font-size: 15px; font-weight: 600; margin-bottom: 6px;">${event.title}</h3>
                    <p style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px; line-height: 1.4;">
                        ${event.description || 'Chưa có mô tả chi tiết'}
                    </p>
                    <div style="font-size: 12px; color: var(--text-muted); display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px;">
                        <div>📍 <b>Địa điểm:</b> ${event.location}</div>
                        <div>📅 <b>Thời gian:</b> ${formatDate(event.startTime)}</div>
                    </div>
                </div>

                <div style="border-top: 1px solid var(--border); padding-top: 12px; display: flex; align-items: center; justify-content: space-between;">
                    <div>
                        <div style="font-size: 11px; color: var(--text-muted);">Giá chỉ từ</div>
                        <div style="font-size: 15px; font-weight: 700; color: var(--primary);">${formatVND(minPrice)}</div>
                    </div>
                    <button class="btn btn-primary btn-sm" onclick="window.AppRouter.selectEventForBooking('${event.id}')">
                        Xem Vé & Đặt Chỗ →
                    </button>
                </div>
            </div>
        `;
    }).join('');
}
