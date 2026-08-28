ALTER TABLE discord_servers ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX discord_servers_deleted_at_idx ON discord_servers (deleted_at);
