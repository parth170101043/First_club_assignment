-- Creates tokenized user payment methods and immutable mock payment transaction records.

CREATE TABLE user_payment_methods (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    method_type VARCHAR(20) NOT NULL CHECK (method_type IN ('CARD', 'UPI')),
    provider_token VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    brand VARCHAR(50),
    last_four VARCHAR(4),
    default_method BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_payment_methods_user
    ON user_payment_methods(user_id, active);

CREATE UNIQUE INDEX uq_user_payment_methods_default
    ON user_payment_methods(user_id)
    WHERE default_method = TRUE AND active = TRUE;

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    payment_method_id UUID NOT NULL REFERENCES user_payment_methods(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    purpose VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED', 'FAILED')),
    provider_reference VARCHAR(100) NOT NULL UNIQUE,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payment_transactions_user_created
    ON payment_transactions(user_id, created_at DESC);
