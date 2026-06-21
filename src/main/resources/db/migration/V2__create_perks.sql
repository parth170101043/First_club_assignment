-- Creates the independent perk catalogue and tier-to-perk assignments. A perk may remain
-- unassigned, while an assignment can override the perk configuration for one tier.

CREATE TABLE perks (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    perk_type VARCHAR(40) NOT NULL CHECK (
        perk_type IN (
            'FREE_DELIVERY',
            'PERCENTAGE_DISCOUNT',
            'EARLY_ACCESS',
            'PRIORITY_SUPPORT',
            'EXCLUSIVE_COUPON',
            'CUSTOM'
        )
    ),
    configuration JSONB NOT NULL DEFAULT '{}'::JSONB,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE tier_perks (
    id UUID PRIMARY KEY,
    tier_id UUID NOT NULL REFERENCES tiers(id),
    perk_id UUID NOT NULL REFERENCES perks(id),
    configuration_override JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tier_perks_tier_perk UNIQUE (tier_id, perk_id)
);

CREATE INDEX idx_tier_perks_tier_id ON tier_perks(tier_id);
CREATE INDEX idx_tier_perks_perk_id ON tier_perks(perk_id);
