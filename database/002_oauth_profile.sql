-- Additive migration. Existing profiles, identities, sessions and scores remain intact.
alter table player_profiles add column if not exists email text;
alter table player_profiles add column if not exists avatar_url text;
alter table player_sessions add column if not exists provider text check (provider in ('google','github'));
-- Recover metadata for old sessions only when the existing identity is unambiguous.
update player_sessions s set provider = identity.provider
from (select user_id, min(provider) as provider from player_identities group by user_id having count(*) = 1) identity
where s.user_id = identity.user_id and s.provider is null;
