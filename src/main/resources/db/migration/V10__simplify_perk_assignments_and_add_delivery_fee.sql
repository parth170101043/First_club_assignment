-- Tier assignments now always use the perk's base configuration.
ALTER TABLE tier_perks
    DROP COLUMN configuration_override;

-- Existing historical orders retain zero because their original delivery charge was not tracked.
ALTER TABLE customer_orders
    ADD COLUMN delivery_fee NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (delivery_fee >= 0);
