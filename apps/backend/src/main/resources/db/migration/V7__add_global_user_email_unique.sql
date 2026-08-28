-- Login is email + password with no company selector, so email alone must
-- identify a user. UNIQUE (company_id, email) from V3 stays. Indexed on
-- lower(email) so lookups are case-insensitive; partial so a soft-deleted user
-- doesn't permanently reserve an address.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email)) WHERE deleted_at IS NULL;
