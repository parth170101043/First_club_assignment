-- Adds password-backed accounts and roles for Spring Security session authentication.
ALTER TABLE app_users
    ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT 'LOGIN_NOT_CONFIGURED',
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
        CHECK (role IN ('MEMBER', 'ADMIN'));

ALTER TABLE app_users
    ALTER COLUMN password_hash DROP DEFAULT;

CREATE INDEX idx_app_users_role ON app_users(role);
