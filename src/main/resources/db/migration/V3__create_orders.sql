-- Creates completed order snapshots containing the membership benefits applied at checkout.

CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    category VARCHAR(100),
    discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    final_amount NUMERIC(12, 2) NOT NULL CHECK (final_amount >= 0),
    free_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    membership_tier_code VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_customer_orders_user_created
    ON customer_orders(user_id, created_at DESC);
