CREATE TABLE behavioral_tier_settings (
    id UUID PRIMARY KEY,
    gold_order_count BIGINT NOT NULL CHECK (gold_order_count >= 0),
    platinum_order_count BIGINT NOT NULL CHECK (platinum_order_count >= gold_order_count),
    gold_monthly_spend NUMERIC(12, 2) NOT NULL CHECK (gold_monthly_spend >= 0),
    platinum_monthly_spend NUMERIC(12, 2) NOT NULL
        CHECK (platinum_monthly_spend >= gold_monthly_spend),
    gold_cohort VARCHAR(100) NOT NULL,
    platinum_cohort VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (LOWER(gold_cohort) <> LOWER(platinum_cohort))
);

INSERT INTO behavioral_tier_settings (
    id,
    gold_order_count,
    platinum_order_count,
    gold_monthly_spend,
    platinum_monthly_spend,
    gold_cohort,
    platinum_cohort,
    version,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    5,
    10,
    5000.00,
    15000.00,
    'EARLY_ADOPTER',
    'VIP',
    0,
    NOW(),
    NOW()
);
