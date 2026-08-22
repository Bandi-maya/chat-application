-- Keep the canonical profile row private to its owner. Peer presence is exposed only
-- through a per-viewer masked projection below.
drop policy if exists profiles_select_shared_or_self on public.profiles;
drop policy if exists profiles_select_self on public.profiles;
create policy profiles_select_self on public.profiles for select to authenticated
using (id = (select auth.uid()));

create table if not exists public.contact_presence_visibility (
  owner_user_id uuid not null references public.profiles(id) on delete cascade,
  viewer_user_id uuid not null references public.profiles(id) on delete cascade,
  presence text not null default 'offline',
  last_seen_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (owner_user_id, viewer_user_id),
  constraint contact_presence_not_self check (owner_user_id <> viewer_user_id)
);

alter table public.contact_presence_visibility enable row level security;
drop policy if exists contact_presence_select_viewer on public.contact_presence_visibility;
create policy contact_presence_select_viewer on public.contact_presence_visibility
for select to authenticated
using (viewer_user_id = (select auth.uid()));
revoke all on public.contact_presence_visibility from anon, authenticated;
grant select on public.contact_presence_visibility to authenticated;
create index if not exists contact_presence_viewer_idx on public.contact_presence_visibility(viewer_user_id, owner_user_id);

create or replace function private.refresh_presence_projection_for_owner(p_owner_user_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public','private'
as $function$
begin
  insert into public.contact_presence_visibility(owner_user_id, viewer_user_id, presence, last_seen_at, updated_at)
  select distinct p_owner_user_id,
         viewer.user_id,
         case when coalesce(cpo.hide_online,false) then 'offline' else p.presence end,
         case when coalesce(cpo.hide_last_seen,false) then null else p.last_seen_at end,
         now()
  from public.profiles p
  join public.conversation_members owner_cm on owner_cm.user_id=p.id
  join public.conversation_members viewer on viewer.conversation_id=owner_cm.conversation_id and viewer.user_id<>p.id
  left join public.contact_privacy_overrides cpo
    on cpo.owner_user_id=p.id and cpo.target_user_id=viewer.user_id
  where p.id=p_owner_user_id
  on conflict(owner_user_id,viewer_user_id) do update set
    presence=excluded.presence,
    last_seen_at=excluded.last_seen_at,
    updated_at=excluded.updated_at;
end;
$function$;
revoke all on function private.refresh_presence_projection_for_owner(uuid) from public, anon, authenticated;

create or replace function private.refresh_presence_projection_pair(p_owner_user_id uuid, p_viewer_user_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public','private'
as $function$
begin
  if p_owner_user_id is null or p_viewer_user_id is null or p_owner_user_id=p_viewer_user_id then return; end if;
  if not exists (
    select 1 from public.conversation_members a
    join public.conversation_members b on b.conversation_id=a.conversation_id
    where a.user_id=p_owner_user_id and b.user_id=p_viewer_user_id
  ) then
    delete from public.contact_presence_visibility
    where owner_user_id=p_owner_user_id and viewer_user_id=p_viewer_user_id;
    return;
  end if;
  insert into public.contact_presence_visibility(owner_user_id,viewer_user_id,presence,last_seen_at,updated_at)
  select p_owner_user_id,p_viewer_user_id,
         case when coalesce(cpo.hide_online,false) then 'offline' else p.presence end,
         case when coalesce(cpo.hide_last_seen,false) then null else p.last_seen_at end,
         now()
  from public.profiles p
  left join public.contact_privacy_overrides cpo
    on cpo.owner_user_id=p_owner_user_id and cpo.target_user_id=p_viewer_user_id
  where p.id=p_owner_user_id
  on conflict(owner_user_id,viewer_user_id) do update set
    presence=excluded.presence,
    last_seen_at=excluded.last_seen_at,
    updated_at=excluded.updated_at;
end;
$function$;
revoke all on function private.refresh_presence_projection_pair(uuid,uuid) from public, anon, authenticated;

create or replace function private.on_profile_presence_changed()
returns trigger
language plpgsql
security definer
set search_path to 'public','private'
as $function$
begin
  perform private.refresh_presence_projection_for_owner(new.id);
  return new;
end;
$function$;
revoke all on function private.on_profile_presence_changed() from public, anon, authenticated;

drop trigger if exists profiles_refresh_contact_presence on public.profiles;
create trigger profiles_refresh_contact_presence
after insert or update of presence,last_seen_at on public.profiles
for each row execute function private.on_profile_presence_changed();

create or replace function private.on_contact_privacy_presence_changed()
returns trigger
language plpgsql
security definer
set search_path to 'public','private'
as $function$
begin
  perform private.refresh_presence_projection_pair(coalesce(new.owner_user_id,old.owner_user_id),coalesce(new.target_user_id,old.target_user_id));
  return coalesce(new,old);
end;
$function$;
revoke all on function private.on_contact_privacy_presence_changed() from public, anon, authenticated;

drop trigger if exists contact_privacy_refresh_presence on public.contact_privacy_overrides;
create trigger contact_privacy_refresh_presence
after insert or update of hide_online,hide_last_seen or delete on public.contact_privacy_overrides
for each row execute function private.on_contact_privacy_presence_changed();

create or replace function private.on_conversation_membership_presence_changed()
returns trigger
language plpgsql
security definer
set search_path to 'public','private'
as $function$
declare
  v_conversation uuid:=coalesce(new.conversation_id,old.conversation_id);
  v_user uuid:=coalesce(new.user_id,old.user_id);
  peer record;
begin
  for peer in select cm.user_id from public.conversation_members cm where cm.conversation_id=v_conversation and cm.user_id<>v_user loop
    perform private.refresh_presence_projection_pair(v_user,peer.user_id);
    perform private.refresh_presence_projection_pair(peer.user_id,v_user);
  end loop;
  if tg_op='DELETE' then
    delete from public.contact_presence_visibility cpv
    where (cpv.owner_user_id=v_user or cpv.viewer_user_id=v_user)
      and not exists (
        select 1 from public.conversation_members a join public.conversation_members b on b.conversation_id=a.conversation_id
        where a.user_id=cpv.owner_user_id and b.user_id=cpv.viewer_user_id
      );
  end if;
  return coalesce(new,old);
end;
$function$;
revoke all on function private.on_conversation_membership_presence_changed() from public, anon, authenticated;

drop trigger if exists conversation_members_refresh_presence on public.conversation_members;
create trigger conversation_members_refresh_presence
after insert or delete on public.conversation_members
for each row execute function private.on_conversation_membership_presence_changed();

-- Backfill projection for existing conversation peers.
do $do$
declare r record;
begin
  for r in select id from public.profiles loop
    perform private.refresh_presence_projection_for_owner(r.id);
  end loop;
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='contact_presence_visibility'
  ) then
    execute 'alter publication supabase_realtime add table public.contact_presence_visibility';
  end if;
  if exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='profiles'
  ) then
    execute 'alter publication supabase_realtime drop table public.profiles';
  end if;
end;
$do$;
