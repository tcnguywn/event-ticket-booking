CREATE INDEX IF NOT EXISTS idx_utb_user_event ON user_ticket_booking(user_id, event_id, status);
CREATE INDEX IF NOT EXISTS idx_utb_booking_group ON user_ticket_booking(booking_group_id);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox(status) WHERE status = 'PENDING';

-- Table mapping ghe voi don booking de chong ro ri ghe trong Saga
CREATE TABLE IF NOT EXISTS booking_seats (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    booking_group_id UUID NOT NULL,
    event_id UUID NOT NULL,
    seat_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_booking_seats_group ON booking_seats(booking_group_id);
CREATE INDEX IF NOT EXISTS idx_booking_seats_booking ON booking_seats(booking_id);
CREATE INDEX IF NOT EXISTS idx_booking_seats_seat ON booking_seats(seat_id);

CREATE TABLE IF NOT EXISTS shedlock (
  name        VARCHAR(64)  PRIMARY KEY,
  lock_until  TIMESTAMP    NOT NULL,
  locked_at   TIMESTAMP    NOT NULL,
  locked_by   VARCHAR(255) NOT NULL
);

-- Seed Sample Event
INSERT INTO events (id, title, description, organizer_id, status, start_time, end_time, location, max_tickets_per_user, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'Concert World Tour 2026', 'Dai nhac hoi quoc te', '00000000-0000-0000-0000-000000000001', 'PUBLISHED', '2026-10-01 19:00:00', '2026-10-01 23:00:00', 'Ha Noi Stadium', 10, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Seed Sample Ticket Types
INSERT INTO ticket_types (id, event_id, name, price, quantity, max_order_quantity, created_at)
VALUES 
('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'VIP Diamond', 1500000, 500, 4, CURRENT_TIMESTAMP),
('33333333-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Standard GA', 650000, 2000, 4, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;