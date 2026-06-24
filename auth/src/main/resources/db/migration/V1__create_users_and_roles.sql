-- =============================================================================
-- V1__create_users_and_roles.sql
-- Sprint 1, US-002: Foundation schema for auth domain
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ROLES: lookup table, static data, minimal audit
-- -----------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO roles (name, description) VALUES
    ('USER',   'Default registered user'),
    ('ADMIN',  'Platform administrator'),
    ('SELLER', 'Vendor with selling privileges');

-- -----------------------------------------------------------------------------
-- USERS: full audit, soft delete, case-insensitive unique email
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    deleted_at      TIMESTAMP NULL,

    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_users_email_format
        CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_users_full_name_length
        CHECK (length(trim(full_name)) >= 1)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
CREATE INDEX idx_users_active ON users (id) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- USER_ROLES: many-to-many junction
-- -----------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id     UUID NOT NULL,
    role_id     UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
