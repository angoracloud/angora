-- Credentials for machine callers (the bots): no user, no role, no expiry. As
-- with sessions, only the SHA-256 of the token is stored. No scopes column yet —
-- add one when a second consumer needs different access.
CREATE TABLE service_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    token_hash TEXT NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER service_tokens_set_updated_at
    BEFORE UPDATE ON service_tokens
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
