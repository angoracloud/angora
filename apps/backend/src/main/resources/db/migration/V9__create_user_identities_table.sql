-- How a user authenticates, separated from the user record. Only 'local' today;
-- the table exists so adding SSO later is a new provider row rather than a new
-- auth architecture.
CREATE TABLE user_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL DEFAULT 'local',
    -- The provider's own stable id for this user: the user id for 'local', the
    -- OIDC `sub` claim for an external provider.
    subject TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX user_identities_user_id_idx ON user_identities (user_id);

CREATE TRIGGER user_identities_set_updated_at
    BEFORE UPDATE ON user_identities
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- Existing users all authenticate locally; the user id as subject survives an
-- email change.
INSERT INTO user_identities (user_id, provider, subject)
SELECT id, 'local', id::text FROM users;
