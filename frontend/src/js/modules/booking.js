import { request } from '../api.js';
import { State } from '../state.js';
import { CONFIG } from '../config.js';
import { formatVND, formatDate, generateUUID } from '../utils.js';

export function initBookingView(eventId = null) {
    const container = document.getElementById('booking-view');
    if (!container) return;

    const targetEventId = eventId || (State.selectedEvent ? State.selectedEvent.id : CONFIG.SAMPLE_EVENT_ID);
    
    // Reset selection
    State.selectedSeats = [];
    State.zoneQuantity = 1;

    container.innerHTML = `
        <div class="grid-2">
            <!-- Left: Seat Map & Ticket Selection -->
            <div>
                <div class="card" style="margin-bottom: 16px;">
                    <div class="card-header">
                        <span class="card-title">🎪 Sơ Đồ Chọn Ghế (Model A: Numbered Seats)</span>
                        <div style="display: flex; gap: 8px;">
                            <span class="badge" style="background: white; border: 1px solid var(--border); color: var(--text-main);">Trống</span>
                            <span class="badge" style="background: var(--primary); color: white;">Đang chọn</span>
                            <span class="badge badge-warning">Đang giữ chỗ</span>
                            <span class="badge badge-subtle">Đã bán</span>
                        </div>
                    </div>

                    <div class="seat-map-container">
                        <div class="stage-bar">SÂN KHẤU CHÍNH / MAIN STAGE</div>
                        <div class="seat-grid" id="seat-matrix">
                            <!-- Dynamically generated seats -->
                        </div>
                    </div>
                </div>

                <!-- Model B: General Admission / Zone Tickets -->
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">⚡ Vé Đứng Tự Do (Model B: Standing Zone Flash Sale)</span>
                        <span class="badge badge-success">Atomic Lua Quota</span>
                    </div>
                    <div style="display: flex; align-items: center; justify-content: space-between;">
                        <div>
                            <div style="font-weight: 600; font-size: 14px;">Khu Vực Fanzone / Sân Cỏ (Standard GA)</div>
                            <div style="font-size: 12px; color: var(--text-muted);">Không gắn số ghế, vào cổng tự do theo khu vực</div>
                            <div style="font-size: 14px; font-weight: 700; color: var(--primary); margin-top: 4px;">650.000 đ / vé</div>
                        </div>
                        <div style="display: flex; align-items: center; gap: 10px;">
                            <button class="btn btn-secondary btn-sm" id="btn-dec-zone">-</button>
                            <span style="font-size: 16px; font-weight: 700; min-width: 24px; text-align: center;" id="zone-qty-display">0</span>
                            <button class="btn btn-secondary btn-sm" id="btn-inc-zone">+</button>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right: Order Summary & Execution -->
            <div>
                <div class="card" style="position: sticky; top: 76px;">
                    <div class="card-header">
                        <span class="card-title">🧾 Thông Tin Đơn Đặt Vé</span>
                        <span class="badge badge-warning" id="hold-timer-badge" style="display: none;">Giữ chỗ: 10:00</span>
                    </div>

                    <div style="margin-bottom: 16px; font-size: 13px;">
                        <div style="margin-bottom: 6px;"><b>Sự kiện ID:</b> <span id="summary-event-id" style="font-family: var(--font-mono); font-size: 11px;">${targetEventId}</span></div>
                        <div style="margin-bottom: 6px;"><b>Người đặt:</b> <span>${State.currentUser.name} (${State.currentUser.email})</span></div>
                    </div>

                    <div style="background: var(--bg-subtle); border-radius: var(--radius-sm); padding: 12px; margin-bottom: 16px; font-size: 13px;">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
                            <span class="text-muted">Ghế VIP đã chọn:</span>
                            <span id="summary-seats-text" style="font-weight: 600;">Chưa chọn</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; margin-bottom: 6px;">
                            <span class="text-muted">Vé đứng GA:</span>
                            <span id="summary-zone-text" style="font-weight: 600;">0 vé</span>
                        </div>
                        <div style="border-top: 1px dashed var(--border); padding-top: 8px; margin-top: 8px; display: flex; justify-content: space-between; font-size: 15px;">
                            <b>Tổng tiền tạm tính:</b>
                            <b id="summary-total-price" style="color: var(--primary);">0 đ</b>
                        </div>
                    </div>

                    <!-- Virtual Waiting Room Alert Banner -->
                    <div id="waiting-room-alert" style="display: none; padding: 12px; background: var(--warning-light); border: 1px solid #fde68a; border-radius: var(--radius-sm); margin-bottom: 16px; font-size: 12px; color: #b45309;">
                        <b>⏳ Bạn đang trong Phòng Chờ Ảo:</b> Vị trí xếp hàng: <b id="wr-position">#1</b>. Thời gian ước tính: <b id="wr-wait">5s</b>. Vui lòng không tắt trang.
                    </div>

                    <div style="display: flex; flex-direction: column; gap: 8px;">
                        <button class="btn btn-primary" id="btn-submit-booking" style="width: 100%; padding: 10px; font-size: 14px;">
                            ⚡ Xác Nhận Đặt Vé & Giữ Chỗ (10 Phút)
                        </button>
                        <button class="btn btn-success" id="btn-go-payment" style="width: 100%; padding: 10px; font-size: 14px; display: none;">
                            💳 Thanh Toán Đơn Hàng (VNPay) →
                        </button>
                    </div>
                </div>
            </div>
        </div>
    `;

    renderSampleSeatMatrix(targetEventId);
    setupBookingEvents(targetEventId);
}

// Render sample seat map with 24 seats (Row A, B, C)
function renderSampleSeatMatrix(eventId) {
    const seatMatrix = document.getElementById('seat-matrix');
    if (!seatMatrix) return;

    const rows = ['A', 'B', 'C'];
    const seats = [];

    rows.forEach(row => {
        for (let i = 1; i <= 8; i++) {
            const num = i < 10 ? `0${i}` : `${i}`;
            // Sample mock: Seat A03 is HOLD, B05 is BOOKED
            let status = 'free';
            if (row === 'A' && i === 3) status = 'hold';
            if (row === 'B' && i === 5) status = 'booked';

            seats.push({
                id: generateUUID(),
                seatRow: row,
                seatNumber: num,
                label: `${row}${num}`,
                price: 1500000,
                status
            });
        }
    });

    seatMatrix.innerHTML = seats.map(seat => `
        <div class="seat-item ${seat.status}" 
             data-id="${seat.id}" 
             data-label="${seat.label}" 
             data-price="${seat.price}" 
             data-status="${seat.status}"
             title="${seat.label} - ${formatVND(seat.price)}">
            ${seat.label}
        </div>
    `).join('');

    // Attach click events
    seatMatrix.querySelectorAll('.seat-item.free').forEach(el => {
        el.addEventListener('click', () => {
            const id = el.dataset.id;
            const label = el.dataset.label;
            const price = parseInt(el.dataset.price);

            const index = State.selectedSeats.findIndex(s => s.id === id);
            if (index >= 0) {
                State.selectedSeats.splice(index, 1);
                el.classList.remove('selected');
            } else {
                State.selectedSeats.push({ id, label, price });
                el.classList.add('selected');
            }
            updateSummary();
        });
    });
}

function setupBookingEvents(eventId) {
    const btnInc = document.getElementById('btn-inc-zone');
    const btnDec = document.getElementById('btn-dec-zone');
    const btnSubmit = document.getElementById('btn-submit-booking');

    if (btnInc) {
        btnInc.addEventListener('click', () => {
            if (State.zoneQuantity < 4) {
                State.zoneQuantity++;
                document.getElementById('zone-qty-display').innerText = State.zoneQuantity;
                updateSummary();
            }
        });
    }

    if (btnDec) {
        btnDec.addEventListener('click', () => {
            if (State.zoneQuantity > 0) {
                State.zoneQuantity--;
                document.getElementById('zone-qty-display').innerText = State.zoneQuantity;
                updateSummary();
            }
        });
    }

    if (btnSubmit) {
        btnSubmit.addEventListener('click', () => executeBooking(eventId));
    }
}

function updateSummary() {
    const seatsText = document.getElementById('summary-seats-text');
    const zoneText = document.getElementById('summary-zone-text');
    const totalPriceEl = document.getElementById('summary-total-price');

    const seatsCount = State.selectedSeats.length;
    const seatsPrice = State.selectedSeats.reduce((sum, s) => sum + s.price, 0);
    const zonePrice = State.zoneQuantity * 650000;
    const total = seatsPrice + zonePrice;

    if (seatsText) {
        seatsText.innerText = seatsCount > 0 
            ? `${State.selectedSeats.map(s => s.label).join(', ')} (${formatVND(seatsPrice)})`
            : 'Chưa chọn';
    }

    if (zoneText) {
        zoneText.innerText = State.zoneQuantity > 0 
            ? `${State.zoneQuantity} vé (${formatVND(zonePrice)})`
            : '0 vé';
    }

    if (totalPriceEl) {
        totalPriceEl.innerText = formatVND(total);
    }
}

async function executeBooking(eventId) {
    const btnSubmit = document.getElementById('btn-submit-booking');
    const btnPayment = document.getElementById('btn-go-payment');
    const timerBadge = document.getElementById('hold-timer-badge');
    const waitingRoomAlert = document.getElementById('waiting-room-alert');

    if (State.selectedSeats.length === 0 && State.zoneQuantity === 0) {
        alert('Vui lòng chọn ít nhất 1 ghế VIP hoặc 1 vé đứng GA!');
        return;
    }

    btnSubmit.disabled = true;
    btnSubmit.innerText = 'Đang xử lý đặt vé...';

    // Construct request payload
    const items = [];
    if (State.selectedSeats.length > 0) {
        items.push({
            ticketTypeId: CONFIG.SAMPLE_VIP_TICKET_TYPE_ID,
            quantity: State.selectedSeats.length,
            seatIds: State.selectedSeats.map(s => s.id)
        });
    }
    if (State.zoneQuantity > 0) {
        items.push({
            ticketTypeId: CONFIG.SAMPLE_GA_TICKET_TYPE_ID,
            quantity: State.zoneQuantity
        });
    }

    const payload = {
        eventId: eventId,
        items
    };

    try {
        const response = await request('/api/tickets/book', {
            method: 'POST',
            body: JSON.stringify(payload)
        }, 'event');

        State.currentBookingGroup = response.bookingGroupId;

        btnSubmit.style.display = 'none';
        btnPayment.style.display = 'block';
        btnPayment.onclick = () => window.AppRouter.navigateTo('orders', { bookingGroupId: response.bookingGroupId });

        timerBadge.style.display = 'inline-flex';
        waitingRoomAlert.style.display = 'none';

        alert(`🎉 Đặt vé thành công! Mã đơn: ${response.bookingGroupId}. Bạn có 10 phút để thanh toán.`);

    } catch (err) {
        if (err.status === 429 && err.data && err.data.status === 'WAITING_ROOM') {
            // Virtual Waiting Room active
            waitingRoomAlert.style.display = 'block';
            document.getElementById('wr-position').innerText = `#${err.data.queuePosition}`;
            document.getElementById('wr-wait').innerText = `${err.data.estimatedWaitSeconds}s`;
            btnSubmit.innerText = 'Đang xếp hàng... Tự động thử lại';
            
            // Auto retry in estimated seconds
            setTimeout(() => executeBooking(eventId), 3000);
            return;
        }

        alert(`❌ Đặt vé thất bại: ${err.message}`);
        btnSubmit.disabled = false;
        btnSubmit.innerText = '⚡ Xác Nhận Đặt Vé & Giữ Chỗ (10 Phút)';
    }
}
