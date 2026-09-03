// Configuration for Frontend Client
export const CONFIG = {
    DEFAULT_GATEWAY_URL: 'http://localhost:8888',
    DIRECT_SERVICES: {
        event: 'http://localhost:8082',
        order: 'http://localhost:8083',
        payment: 'http://localhost:8084',
        notification: 'http://localhost:8085'
    },
    MAILHOG_WEB_URL: 'http://localhost:8025',
    KAFKA_UI_URL: 'http://localhost:8086',
    KEYCLOAK_URL: 'http://localhost:8080',
    
    // Sample Defaults (Loaded from backend data.sql)
    SAMPLE_EVENT_ID: '11111111-1111-1111-1111-111111111111',
    SAMPLE_VIP_TICKET_TYPE_ID: '22222222-2222-2222-2222-222222222222',
    SAMPLE_GA_TICKET_TYPE_ID: '33333333-2222-2222-2222-222222222222',
    
    ROLES: {
        USER: {
            id: '33333333-3333-3333-3333-333333333333',
            name: 'John Doe (User)',
            email: 'john_doe@loadtest.com',
            role: 'USER'
        },
        ORGANIZER: {
            id: '00000000-0000-0000-0000-000000000001',
            name: 'Concert Corp (Organizer)',
            email: 'organizer@concert.com',
            role: 'ORGANIZER'
        },
        ADMIN: {
            id: '99999999-9999-9999-9999-999999999999',
            name: 'Super Admin',
            email: 'admin@eventticketing.com',
            role: 'ADMIN'
        },
        STAFF: {
            id: '55555555-5555-5555-5555-555555555555',
            name: 'Gate Scanner Staff',
            email: 'staff@stadiumgate.com',
            role: 'STAFF'
        }
    }
};
