-- Additive migration: keeps existing profiles, identities, sessions and results.
alter table player_profiles add column if not exists email text;
alter table player_profiles add column if not exists avatar_url text;
alter table player_sessions add column if not exists provider text check (provider in ('google','github'));
-- Update old sessions only when there is exactly one possible identity.
update player_sessions s set provider = identity.provider
from (select user_id, min(provider) as provider from player_identities group by user_id having count(*) = 1) identity
where s.user_id = identity.user_id and s.provider is null;
