import { request } from '../api.js';
import { State } from '../state.js';
import { CONFIG } from '../config.js';
import { calculateHmacSha256, generateUUID } from '../utils.js';

const CHECKIN_SECRET = 'SecretConcertKeyForCheckIn2026!';

export function initCheckInView() {
    const container = document.getElementById('checkin-view');
    if (!container) return;

    const sampleTicketId = generateUUID();
    const sampleEventId = State.selectedEvent ? State.selectedEvent.id : CONFIG.SAMPLE_EVENT_ID;

    container.innerHTML = `
        <div class="grid-2">
            <!-- Left: Check-in Scanner -->
            <div>
                <div class="card">
                    <div class="card-header">
                        <span class="card-title">🎟️ Thiết Bị Quét Vé Cửa Vào (Staff Check-In Engine)</span>
                        <span class="badge badge-success">HMAC-SHA256 + Redis NX</span>
                    </div>

                    <div class="form-group">
                        <label>Mã Sự Kiện (Event ID)</label>
                        <input type="text" id="chk-event-id" value="${sampleEventId}">
                    </div>

                    <div class="form-group">
                        <label>Mã Vé (Ticket ID / UUID)</label>
                        <div style="display: flex; gap: 8px;">
                            <input type="text" id="chk-ticket-id" value="${sampleTicketId}" style="flex: 1;">
                            <button class="btn btn-secondary btn-sm" id="btn-new-ticket">🎲 Sinh Vé Mới</button>
                        </div>
                    </div>

                    <div class="form-group">
                        <label>Chữ Ký Điện Tử (QR HMAC Signature)</label>
                        <input type="text" id="chk-signature" placeholder="Tự động sinh chữ ký QR">
                    </div>

                    <div class="form-group">
                        <label>Cổng Soát Vé (Gate Location)</label>
                        <select id="chk-gate">
                            <option value="GATE_A_VIP">Cổng A - Lối VIP Khán Đài</option>
                            <option value="GATE_B_GA">Cổng B - Vé Đứng Fanzone</option>
                            <option value="GATE_C_STANDARD">Cổng C - Khán Đài Phổ Thông</option>
                        </select>
                    </div>

                    <div style="display: flex; gap: 8px; margin-bottom: 16px;">
                        <button class="btn btn-secondary" id="btn-gen-sig" style="flex: 1;">
                            🔑 Sinh Chữ Ký Hợp Lệ
                        </button>
                        <button class="btn btn-primary" id="btn-do-checkin" style="flex: 1;">
                            📷 Quét & Soát Vé Ngay
                        </button>
                    </div>

                    <div style="padding: 10px; background: var(--bg-subtle); border-radius: var(--radius-sm); font-size: 12px; color: var(--text-muted);">
                        <b>Kiểm thử chống gian lận (Anti-Fraud Double Scan):</b>
                        <br>• Lần 1: Hệ thống xác thực chữ ký HMAC và ghi nhận Redis <code>SET NX</code> $\rightarrow$ <span style="color: var(--success); font-weight: 600;">HỢP LỆ</span>.
                        <br>• Lần 2 (quét lại cùng vé): Redis phát hiện trùng lặp $\rightarrow$ <span style="color: var(--danger); font-weight: 600;">CẢNH BÁO ĐỎ TỨC THÌ</span>.
                    </div>
                </div>
            </div>

            <!-- Right: Check-in Result Card -->
            <div>
                <div class="card" style="position: sticky; top: 76px;">
                    <div class="card-header">
                        <span class="card-title">📋 Kết Quả Soát Vé Trực Tiếp</span>
                    </div>

                    <div id="checkin-result-box" style="text-align: center; padding: 36px 16px; border: 2px dashed var(--border); border-radius: var(--radius-sm); color: var(--text-muted);">
                        Chưa có lượt quét vé nào. Hãy nhấn <b>"Quét & Soát Vé Ngay"</b>.
                    </div>
                </div>
            </div>
        </div>
    `;

    document.getElementById('btn-new-ticket').addEventListener('click', () => {
        document.getElementById('chk-ticket-id').value = generateUUID();
        generateSignature();
    });
    document.getElementById('btn-gen-sig').addEventListener('click', generateSignature);
    document.getElementById('btn-do-checkin').addEventListener('click', executeCheckIn);

    // Generate initial signature
    generateSignature();
}

async function generateSignature() {
    const ticketId = document.getElementById('chk-ticket-id').value.trim();
    const eventId = document.getElementById('chk-event-id').value.trim();
    if (!ticketId || !eventId) return;

    try {
        const rawData = `${ticketId}:${eventId}`;
        const sig = await calculateHmacSha256(rawData, CHECKIN_SECRET);
        document.getElementById('chk-signature').value = sig;
    } catch (e) {
        console.error('Failed to generate HMAC', e);
    }
}

async function executeCheckIn() {
    const ticketId = document.getElementById('chk-ticket-id').value.trim();
    const eventId = document.getElementById('chk-event-id').value.trim();
    const signature = document.getElementById('chk-signature').value.trim();
    const gate = document.getElementById('chk-gate').value;
    const resultBox = document.getElementById('checkin-result-box');

    if (!ticketId || !eventId || !signature) {
        alert('Vui lòng nhập Ticket ID, Event ID và Signature!');
        return;
    }

    resultBox.innerHTML = '<div style="color: var(--text-muted);">Đang đối soát chữ ký và kiểm tra Redis SET NX...</div>';

    try {
        const payload = {
            ticketId,
            eventId,
            signature
        };

        const res = await request('/api/tickets/check-in', {
            method: 'POST',
            body: JSON.stringify(payload)
        }, 'event');

        if (res.valid) {
            resultBox.innerHTML = `
                <div style="color: var(--success); font-size: 48px; margin-bottom: 8px;">✅</div>
                <div style="font-size: 18px; font-weight: 700; color: var(--success); margin-bottom: 6px;">VÉ HỢP LỆ - MỜI VÀO CỔNG</div>
                <div style="font-size: 12px; color: var(--text-muted); margin-bottom: 12px;">Cổng: <b>${gate}</b> | Thời gian: <b>${new Date().toLocaleTimeString()}</b></div>
                <div style="font-size: 11px; background: var(--success-light); color: var(--success); padding: 6px 12px; border-radius: 4px; display: inline-block;">
                    Redis Lock: Đã ghi nhận check-in nguyên tử (Anti Double-Scan)
                </div>
            `;
        } else {
            renderCheckinError(resultBox, res.message || 'Vé không hợp lệ hoặc không tìm thấy');
        }

    } catch (err) {
        renderCheckinError(resultBox, err.message);
    }
}

function renderCheckinError(box, message) {
    box.innerHTML = `
        <div style="color: var(--danger); font-size: 48px; margin-bottom: 8px;">⛔</div>
        <div style="font-size: 18px; font-weight: 700; color: var(--danger); margin-bottom: 6px;">CẢNH BÁO: KHÔNG ĐƯỢC PHÉP VÀO!</div>
        <div style="font-size: 13px; color: var(--danger); margin-bottom: 12px;"><b>Lý do:</b> ${message}</div>
        <div style="font-size: 11px; background: var(--danger-light); color: var(--danger); padding: 6px 12px; border-radius: 4px; display: inline-block;">
            Phát hiện vé giả mạo hoặc vé đã qua cửa trước đó!
        </div>
    `;
}
