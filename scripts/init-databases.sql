-- =========================================================================
-- Script khởi tạo Multi-Database cho các Microservices
-- Tự động thực thi khi container PostgreSQL khởi động lần đầu
-- =========================================================================

-- 1. Database cho Event Ticket Service
SELECT 'CREATE DATABASE event_ticket_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'event_ticket_db')\gexec

-- 2. Database cho Order Service
SELECT 'CREATE DATABASE order_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order_db')\gexec

-- 3. Database cho Payment Service
SELECT 'CREATE DATABASE payment_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_db')\gexec

-- 4. Database cho Keycloak Identity Provider
SELECT 'CREATE DATABASE keycloak_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak_db')\gexec

