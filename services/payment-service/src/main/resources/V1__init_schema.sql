-- =========================================================================
-- 1. Bảng: outbox
-- =========================================================================
CREATE TABLE outbox (
                      id uuid PRIMARY KEY,
                      aggregate_id character varying(255) NOT NULL,
                      event_type character varying(255) NOT NULL,
                      topic character varying(255) NOT NULL,
                      payload jsonb,
                      status character varying(50),
                      retry_count integer NOT NULL DEFAULT 0,
                      max_retry integer NOT NULL DEFAULT 0,
                      created_at timestamp without time zone,
                      processed_at timestamp without time zone,
                      next_retry_at timestamp without time zone
);

-- =========================================================================
-- 2. Bảng: payments
-- =========================================================================
CREATE TABLE payments (
                        id uuid PRIMARY KEY,
                        order_id character varying(255) NOT NULL UNIQUE,
                        user_id character varying(255) NOT NULL,
                        event_id character varying(255) NOT NULL,
                        amount bigint NOT NULL,
                        status character varying(50),
                        transaction_ref bigint UNIQUE,
                        qr_code_url character varying(1000),  -- URL thường dài, nên để độ dài an toàn
                        checkout_url character varying(1000), -- URL thường dài, nên để độ dài an toàn
                        idempotency_key character varying(255) NOT NULL UNIQUE,
                        payload text,
                        created_at timestamp without time zone,
                        updated_at timestamp without time zone,
                        expired_at timestamp without time zone
);


