-- Only timed rounds belong to the ranking. Keep profiles, sessions and timed results.
-- Older deployments may still submit practice results during rollout; ignore those writes.
create or replace function public.ignore_practice_results()
returns trigger language plpgsql as $$
begin
  return null;
end;
$$;

drop trigger if exists player_results_timed_only on public.player_results;
create trigger player_results_timed_only
before insert or update on public.player_results
for each row when (new.mode in ('CAPITALS', 'SURVIVAL'))
execute function public.ignore_practice_results();

delete from public.player_results where mode in ('CAPITALS', 'SURVIVAL');
