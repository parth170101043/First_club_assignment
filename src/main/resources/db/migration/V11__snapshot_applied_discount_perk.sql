ALTER TABLE customer_orders
    ADD COLUMN applied_discount_perk_code VARCHAR(80),
    ADD COLUMN applied_discount_perk_name VARCHAR(120);
