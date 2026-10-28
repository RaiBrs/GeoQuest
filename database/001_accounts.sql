-- PostgreSQL 14+. Run this migration once with the database owner before enabling accounts.
create table if not exists player_profiles (
  id uuid primary key,
  display_name text not null check (char_length(display_name) between 1 and 80),
  created_at timestamptz not null default now()
);
create table if not exists player_identities (
  provider text not null check (provider in ('google','github')),
  subject text not null,
  user_id uuid not null references player_profiles(id) on delete cascade,
  primary key(provider, subject)
);
create table if not exists player_sessions (
  token_hash text primary key,
  user_id uuid not null references player_profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days'
);
create index if not exists player_sessions_expiry on player_sessions(expires_at);
create table if not exists player_results (
  game_id uuid primary key,
  user_id uuid not null references player_profiles(id) on delete cascade,
  mode text not null check (mode in ('CAPITALS','SURVIVAL','TIMED')),
  question_type text not null check (question_type in ('CAPITAL','COUNTRY')),
  score integer not null check (score >= 0),
  answered integer not null check (answered >= score),
  created_at timestamptz not null default now()
);
create index if not exists player_results_ranking on player_results(mode, question_type, score desc);
-- Application queries are parameterized. Give the application role access only to these tables.
-- Profiles are identified by provider + subject, never by display name or unverified email.
