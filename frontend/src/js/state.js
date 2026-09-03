import { CONFIG } from './config.js';

export const State = {
    targetMode: 'GATEWAY', // 'GATEWAY' | 'DIRECT'
    currentUser: CONFIG.ROLES.USER,
    activeTab: 'events',
    
    // Event & Booking State
    eventsList: [],
    selectedEvent: null,
    selectedSeats: [], // Array of seat objects { id, seatRow, seatNumber }
    zoneQuantity: 1,
    currentBookingGroup: null,
    queuePassToken: null,
    
    // Listeners for UI re-render
    listeners: new Set(),
    
    subscribe(fn) {
        this.listeners.add(fn);
        return () => this.listeners.delete(fn);
    },
    
    notify() {
        this.listeners.forEach(fn => fn(this));
    },
    
    setUser(roleKey) {
        if (CONFIG.ROLES[roleKey]) {
            this.currentUser = CONFIG.ROLES[roleKey];
            this.notify();
        }
    },
    
    setTargetMode(mode) {
        this.targetMode = mode;
        this.notify();
    },
    
    setQueuePassToken(token) {
        this.queuePassToken = token;
        this.notify();
    }
};
