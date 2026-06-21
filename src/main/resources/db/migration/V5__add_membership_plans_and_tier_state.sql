-- Keeps duration as the existing billing-cycle enum while separating paid, effective,
-- behavioral, and scheduled tiers and adding configurable cycle-tier pricing.

CREATE TABLE plan_tier_prices (
    id UUID PRIMARY KEY,
    billing_cycle VARCHAR(20) NOT NULL
        CHECK (billing_cycle IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    tier_id UUID NOT NULL REFERENCES tiers(id),
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cycle_tier_price UNIQUE (billing_cycle, tier_id)
);

INSERT INTO plan_tier_prices (
    id, billing_cycle, tier_id, price, currency,
    active, version, created_at, updated_at
)
SELECT gen_random_uuid(),
       cycle.code,
       tier.id,
       CASE cycle.code
           WHEN 'MONTHLY' THEN tier.monthly_price
           WHEN 'QUARTERLY' THEN tier.quarterly_price
           WHEN 'YEARLY' THEN tier.yearly_price
       END,
       'INR',
       TRUE,
       0,
       NOW(),
       NOW()
FROM (
    VALUES ('MONTHLY'), ('QUARTERLY'), ('YEARLY')
) AS cycle(code)
CROSS JOIN tiers tier;

ALTER TABLE subscriptions
    ADD COLUMN min_tier_id UUID REFERENCES tiers(id),
    ADD COLUMN current_tier_id UUID REFERENCES tiers(id),
    ADD COLUMN computed_behavioral_tier_id UUID REFERENCES tiers(id),
    ADD COLUMN scheduled_min_tier_id UUID REFERENCES tiers(id);

UPDATE subscriptions
SET min_tier_id = tier_id,
    current_tier_id = tier_id;

ALTER TABLE subscriptions
    ALTER COLUMN min_tier_id SET NOT NULL,
    ALTER COLUMN current_tier_id SET NOT NULL,
    ALTER COLUMN tier_id DROP NOT NULL;

CREATE INDEX idx_subscriptions_current_tier
    ON subscriptions(current_tier_id);

CREATE INDEX idx_subscriptions_scheduled_min_tier
    ON subscriptions(scheduled_min_tier_id)
    WHERE scheduled_min_tier_id IS NOT NULL;
