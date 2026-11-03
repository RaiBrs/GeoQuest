-- Apply after 001_accounts.sql. Answer keys stay in this server-only table.
create table if not exists game_rounds (
  id uuid primary key,
  owner_id uuid references player_profiles(id) on delete cascade,
  state bytea not null,
  expires_at timestamptz not null
);
create index if not exists game_rounds_expiry on game_rounds(expires_at);
create index if not exists game_rounds_owner on game_rounds(owner_id);
