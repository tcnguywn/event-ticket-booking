-- =========================================================================
-- Script DROP Multi-Database cho cac Microservices
-- Chay tren database mac dinh 'postgres' khi can reset sach toan bo du lieu
-- =========================================================================

-- 1. Ngat ket noi va Xoa Database cho Event Ticket Service
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'event_ticket_db' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS event_ticket_db;


-- 2. Ngat ket noi va Xoa Database cho Order Service
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'order_db' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS order_db;


-- 3. Ngat ket noi va Xoa Database cho Payment Service
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'payment_db' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS payment_db;


-- 4. Ngat ket noi va Xoa Database cho Keycloak (Tuy chon)
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE pg_stat_activity.datname = 'keycloak_db' AND pid <> pg_backend_pid();

DROP DATABASE IF EXISTS keycloak_db;
