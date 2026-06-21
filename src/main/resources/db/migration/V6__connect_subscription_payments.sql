-- Stores the active payment method used for subscription purchase and recurring renewal.

ALTER TABLE subscriptions
    ADD COLUMN renewal_payment_method_id UUID
        REFERENCES user_payment_methods(id);

CREATE INDEX idx_subscriptions_renewal_payment_method
    ON subscriptions(renewal_payment_method_id)
    WHERE renewal_payment_method_id IS NOT NULL;
