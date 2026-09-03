import { CONFIG } from './config.js';
import { State } from './state.js';

export function getBaseUrl(serviceType = 'event') {
    if (State.targetMode === 'GATEWAY') {
        return CONFIG.DEFAULT_GATEWAY_URL;
    }
    return CONFIG.DIRECT_SERVICES[serviceType] || CONFIG.DEFAULT_GATEWAY_URL;
}

export async function request(endpoint, options = {}, serviceType = 'event') {
    const baseUrl = getBaseUrl(serviceType);
    const url = `${baseUrl}${endpoint}`;
    const startTime = performance.now();
    
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': State.currentUser.id,
        'X-User-Role': State.currentUser.role,
        'X-User-Email': State.currentUser.email,
        ...(options.headers || {})
    };

    if (State.queuePassToken) {
        headers['X-Queue-Pass-Token'] = State.queuePassToken;
    }

    const config = {
        ...options,
        headers
    };

    const method = (config.method || 'GET').toUpperCase();
    logDebug('info', `[REQ] ${method} ${endpoint}`, { headers, body: config.body });

    try {
        const response = await fetch(url, config);
        const duration = (performance.now() - startTime).toFixed(1);
        
        // Check for granted Queue Token in headers
        const grantedToken = response.headers.get('X-Queue-Pass-Token');
        if (grantedToken) {
            State.setQueuePassToken(grantedToken);
            logDebug('success', `[TOKEN] Received Queue-Pass-Token: ${grantedToken}`);
        }

        const text = await response.text();
        let data = null;
        try {
            data = text ? JSON.parse(text) : {};
        } catch {
            data = { raw: text };
        }

        if (!response.ok) {
            logDebug('error', `[ERR ${response.status}] ${method} ${endpoint} (${duration}ms)`, data);
            const error = new Error(data.message || `HTTP ${response.status}`);
            error.status = response.status;
            error.data = data;
            throw error;
        }

        logDebug('success', `[RES 200] ${method} ${endpoint} (${duration}ms)`, data);
        return data;

    } catch (err) {
        if (!err.status) {
            const duration = (performance.now() - startTime).toFixed(1);
            logDebug('error', `[NETWORK] ${method} ${endpoint} (${duration}ms): ${err.message}`);
        }
        throw err;
    }
}

// Global debug logger event dispatcher
export function logDebug(level, message, details = null) {
    const event = new CustomEvent('app-debug-log', {
        detail: {
            time: new Date().toLocaleTimeString(),
            level, // 'info' | 'success' | 'warn' | 'error'
            message,
            details
        }
    });
    window.dispatchEvent(event);
}
