import { State } from './state.js';
import { CONFIG } from './config.js';
import { request, getBaseUrl } from './api.js';
import { initEventsView } from './modules/events.js';
import { initBookingView } from './modules/booking.js';
import { initOrdersView } from './modules/orders.js';
import { initOrganizerView } from './modules/organizer.js';
import { initCheckInView } from './modules/checkin.js';
import { initSimulatorView } from './modules/simulator.js';

// Global Router
window.AppRouter = {
    navigateTo(tabId, params = {}) {
        document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

        const targetTab = document.querySelector(`.nav-tab[data-tab="${tabId}"]`);
        const targetPane = document.getElementById(`${tabId}-view`);

        if (targetTab) targetTab.classList.add('active');
        if (targetPane) {
            targetPane.classList.add('active');
            State.activeTab = tabId;

            // Trigger module initializers
            if (tabId === 'events') initEventsView();
            if (tabId === 'booking') initBookingView(params.eventId);
            if (tabId === 'orders') initOrdersView(params.bookingGroupId);
            if (tabId === 'organizer') initOrganizerView();
            if (tabId === 'checkin') initCheckInView();
            if (tabId === 'simulator') initSimulatorView();
        }
    },

    selectEventForBooking(eventId) {
        this.navigateTo('booking', { eventId });
    }
};

document.addEventListener('DOMContentLoaded', () => {
    setupHeaderControls();
    setupNavigation();
    setupDebugConsole();
    checkHealthStatuses();

    // Default start at events catalog
    window.AppRouter.navigateTo('events');
});

function setupHeaderControls() {
    const roleSelect = document.getElementById('select-user-role');
    const targetSelect = document.getElementById('select-target-mode');

    if (roleSelect) {
        roleSelect.addEventListener('change', (e) => {
            State.setUser(e.target.value);
            // Refresh current tab
            window.AppRouter.navigateTo(State.activeTab);
        });
    }

    if (targetSelect) {
        targetSelect.addEventListener('change', (e) => {
            State.setTargetMode(e.target.value);
            checkHealthStatuses();
        });
    }
}

function setupNavigation() {
    document.querySelectorAll('.nav-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            const tabId = tab.dataset.tab;
            window.AppRouter.navigateTo(tabId);
        });
    });
}

function setupDebugConsole() {
    const consoleBody = document.getElementById('debug-log-stream');
    const btnClear = document.getElementById('btn-clear-logs');

    if (btnClear) {
        btnClear.addEventListener('click', () => {
            if (consoleBody) consoleBody.innerHTML = '';
        });
    }

    window.addEventListener('app-debug-log', (e) => {
        if (!consoleBody) return;
        const { time, level, message, details } = e.detail;

        const row = document.createElement('div');
        row.className = 'log-row';

        let colorClass = 'log-info';
        if (level === 'success') colorClass = 'log-success';
        if (level === 'warn') colorClass = 'log-warn';
        if (level === 'error') colorClass = 'log-error';

        let detailsStr = '';
        if (details) {
            try {
                detailsStr = typeof details === 'object' ? ` ${JSON.stringify(details)}` : ` ${details}`;
            } catch {}
        }

        row.innerHTML = `
            <span class="log-time">[${time}]</span>
            <span class="${colorClass}">${message}</span>
            <span style="color: #64748b; font-size: 11px;">${detailsStr.substring(0, 150)}</span>
        `;

        consoleBody.appendChild(row);
        consoleBody.scrollTop = consoleBody.scrollHeight;
    });
}

async function checkHealthStatuses() {
    const statusDot = document.getElementById('gateway-status-dot');
    const statusText = document.getElementById('gateway-status-text');

    try {
        const url = `${getBaseUrl('event')}/actuator/health`;
        const res = await fetch(url, { signal: AbortSignal.timeout(3000) });
        if (res.ok) {
            if (statusDot) statusDot.style.background = 'var(--success)';
            if (statusText) statusText.innerText = 'Connected';
        } else {
            throw new Error();
        }
    } catch {
        if (statusDot) statusDot.style.background = 'var(--danger)';
        if (statusText) statusText.innerText = 'Offline';
    }
}
