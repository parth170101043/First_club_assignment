-- Strengthens user-facing uniqueness rules against case variants and concurrent requests.
CREATE UNIQUE INDEX uq_app_users_email_ci ON app_users (LOWER(email));
CREATE UNIQUE INDEX uq_perks_code_ci ON perks (LOWER(code));
CREATE UNIQUE INDEX uq_perks_name_ci ON perks (LOWER(name));
CREATE UNIQUE INDEX uq_tiers_code_ci ON tiers (LOWER(code));
CREATE UNIQUE INDEX uq_tiers_name_ci ON tiers (LOWER(name));
CREATE UNIQUE INDEX uq_products_sku_ci ON products (LOWER(sku));
CREATE UNIQUE INDEX uq_products_name_ci ON products (LOWER(name));
