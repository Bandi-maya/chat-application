-- Rich chat, per-contact privacy, presence projection, linked devices, and call gating.
-- This migration mirrors the production schema used by the Flutter phase and is
-- intentionally idempotent so a restored/self-hosted Chaty database can apply it.

create schema if not exists private;

alter table public.message_receipts add column if not exists delivered_at timestamptz;
alter table public.typing_states add column if not exists is_recording boolean not null default false;

create table if not exists public.contact_privacy_overrides (
  owner_user_id uuid not null references public.profiles(id) on delete cascade,
  target_user_id uuid not null references public.profiles(id) on delete cascade,
  hide_delivery_receipts boolean not null default false,
  hide_read_receipts boolean not null default false,
  hide_typing boolean not null default false,
  hide_recording boolean not null default false,
  hide_online boolean not null default false,
  hide_last_seen boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key(owner_user_id,target_user_id),
  constraint contact_privacy_not_self check(owner_user_id<>target_user_id)
);

create table if not exists public.contact_connections (
  user_low_id uuid not null references public.profiles(id) on delete cascade,
  user_high_id uuid not null references public.profiles(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  low_accepted boolean not null default false,
  high_accepted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(user_low_id,user_high_id),
  unique(conversation_id),
  constraint contact_connection_order check(user_low_id::text<user_high_id::text)
);

create table if not exists public.linked_devices (
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  device_name text not null default 'Chaty device',
  platform text not null default 'Unknown',
  location text not null default '',
  last_active_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key(user_id,device_id)
);

create table if not exists public.contact_presence_visibility (
  owner_user_id uuid not null references public.profiles(id) on delete cascade,
  viewer_user_id uuid not null references public.profiles(id) on delete cascade,
  presence text not null default 'offline',
  last_seen_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key(owner_user_id,viewer_user_id),
  constraint contact_presence_not_self check(owner_user_id<>viewer_user_id)
);

create index if not exists contact_privacy_target_idx on public.contact_privacy_overrides(target_user_id,owner_user_id);
create index if not exists contact_connections_conversation_idx on public.contact_connections(conversation_id);
create index if not exists linked_devices_user_active_idx on public.linked_devices(user_id,last_active_at desc) where revoked_at is null;
create index if not exists contact_presence_viewer_idx on public.contact_presence_visibility(viewer_user_id,owner_user_id);
create index if not exists conversation_members_user_conversation_idx on public.conversation_members(user_id,conversation_id);

alter table public.contact_privacy_overrides enable row level security;
alter table public.contact_connections enable row level security;
alter table public.linked_devices enable row level security;
alter table public.contact_presence_visibility enable row level security;

drop policy if exists contact_privacy_owner_all on public.contact_privacy_overrides;
create policy contact_privacy_owner_all on public.contact_privacy_overrides for all to authenticated
using(owner_user_id=(select auth.uid())) with check(owner_user_id=(select auth.uid()));

drop policy if exists contact_connections_participants_select on public.contact_connections;
create policy contact_connections_participants_select on public.contact_connections for select to authenticated
using(user_low_id=(select auth.uid()) or user_high_id=(select auth.uid()));

drop policy if exists linked_devices_owner_all on public.linked_devices;
create policy linked_devices_owner_all on public.linked_devices for all to authenticated
using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));

drop policy if exists contact_presence_select_viewer on public.contact_presence_visibility;
create policy contact_presence_select_viewer on public.contact_presence_visibility for select to authenticated
using(viewer_user_id=(select auth.uid()));

revoke all on public.contact_presence_visibility from anon,authenticated;
grant select on public.contact_presence_visibility to authenticated;

create or replace function private.accept_contact_connection(p_other_user_id uuid)
returns uuid language plpgsql security definer set search_path to 'public','private' as $$
declare
  v_me uuid:=auth.uid(); v_conversation_id uuid; v_low uuid; v_high uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id=v_me then raise exception 'invalid contact'; end if;
  v_conversation_id:=public.create_direct_conversation(p_other_user_id);
  if v_me::text<p_other_user_id::text then v_low:=v_me; v_high:=p_other_user_id; else v_low:=p_other_user_id; v_high:=v_me; end if;
  insert into public.contact_connections(user_low_id,user_high_id,conversation_id,low_accepted,high_accepted)
  values(v_low,v_high,v_conversation_id,v_me=v_low,v_me=v_high)
  on conflict(user_low_id,user_high_id) do update set
    conversation_id=excluded.conversation_id,
    low_accepted=public.contact_connections.low_accepted or excluded.low_accepted,
    high_accepted=public.contact_connections.high_accepted or excluded.high_accepted,
    updated_at=now();
  return v_conversation_id;
end; $$;
revoke all on function private.accept_contact_connection(uuid) from public,anon,authenticated;

create or replace function public.accept_contact_connection(p_other_user_id uuid)
returns uuid language sql set search_path to 'public','private' as $$
  select private.accept_contact_connection(p_other_user_id);
$$;
grant execute on function public.accept_contact_connection(uuid) to authenticated;

create or replace function public.get_contact_connection_status(p_other_user_id uuid)
returns table(conversation_id uuid,my_accepted boolean,other_accepted boolean,calls_allowed boolean)
language sql stable set search_path to 'public' as $$
  select cc.conversation_id,
    case when (select auth.uid())=cc.user_low_id then cc.low_accepted else cc.high_accepted end,
    case when (select auth.uid())=cc.user_low_id then cc.high_accepted else cc.low_accepted end,
    cc.low_accepted and cc.high_accepted
  from public.contact_connections cc
  where (cc.user_low_id=(select auth.uid()) and cc.user_high_id=p_other_user_id)
     or (cc.user_high_id=(select auth.uid()) and cc.user_low_id=p_other_user_id)
  limit 1;
$$;
grant execute on function public.get_contact_connection_status(uuid) to authenticated;

create or replace function public.set_typing_state(p_conversation_id uuid,p_is_typing boolean)
returns void language plpgsql set search_path to 'public' as $$
declare v_peer uuid; v_hide boolean:=false;
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
  where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then select coalesce(hide_typing,false) into v_hide from public.contact_privacy_overrides where owner_user_id=auth.uid() and target_user_id=v_peer; end if;
  if p_is_typing and not coalesce(v_hide,false) then
    insert into public.typing_states(conversation_id,user_id,is_typing,is_recording,updated_at) values(p_conversation_id,auth.uid(),true,false,now())
    on conflict(conversation_id,user_id) do update set is_typing=true,updated_at=excluded.updated_at;
  else
    update public.typing_states set is_typing=false,updated_at=now() where conversation_id=p_conversation_id and user_id=auth.uid();
    delete from public.typing_states where conversation_id=p_conversation_id and user_id=auth.uid() and not is_typing and not is_recording;
  end if;
end; $$;

create or replace function public.set_recording_state(p_conversation_id uuid,p_is_recording boolean)
returns void language plpgsql set search_path to 'public' as $$
declare v_peer uuid; v_hide boolean:=false;
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
  where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then select coalesce(hide_recording,false) into v_hide from public.contact_privacy_overrides where owner_user_id=auth.uid() and target_user_id=v_peer; end if;
  if p_is_recording and not coalesce(v_hide,false) then
    insert into public.typing_states(conversation_id,user_id,is_typing,is_recording,updated_at) values(p_conversation_id,auth.uid(),false,true,now())
    on conflict(conversation_id,user_id) do update set is_recording=true,updated_at=excluded.updated_at;
  else
    update public.typing_states set is_recording=false,updated_at=now() where conversation_id=p_conversation_id and user_id=auth.uid();
    delete from public.typing_states where conversation_id=p_conversation_id and user_id=auth.uid() and not is_typing and not is_recording;
  end if;
end; $$;

create or replace function public.mark_conversation_delivered(p_conversation_id uuid)
returns void language plpgsql set search_path to 'public' as $$
declare v_peer uuid; v_hide boolean:=false;
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
  where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then select coalesce(hide_delivery_receipts,false) into v_hide from public.contact_privacy_overrides where owner_user_id=auth.uid() and target_user_id=v_peer; end if;
  if not coalesce(v_hide,false) then
    insert into public.message_receipts(message_id,user_id,delivered_at)
    select m.id,auth.uid(),now() from public.messages m where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
    on conflict(message_id,user_id) do update set delivered_at=coalesce(public.message_receipts.delivered_at,excluded.delivered_at);
  end if;
end; $$;

create or replace function private.refresh_presence_projection_pair(p_owner_user_id uuid,p_viewer_user_id uuid)
returns void language plpgsql security definer set search_path to 'public','private' as $$
begin
  if p_owner_user_id is null or p_viewer_user_id is null or p_owner_user_id=p_viewer_user_id then return; end if;
  if not exists(select 1 from public.conversation_members a join public.conversation_members b on b.conversation_id=a.conversation_id where a.user_id=p_owner_user_id and b.user_id=p_viewer_user_id) then
    delete from public.contact_presence_visibility where owner_user_id=p_owner_user_id and viewer_user_id=p_viewer_user_id; return;
  end if;
  insert into public.contact_presence_visibility(owner_user_id,viewer_user_id,presence,last_seen_at,updated_at)
  select p_owner_user_id,p_viewer_user_id,case when coalesce(cpo.hide_online,false) then 'offline' else p.presence end,
    case when coalesce(cpo.hide_last_seen,false) then null else p.last_seen_at end,now()
  from public.profiles p left join public.contact_privacy_overrides cpo on cpo.owner_user_id=p_owner_user_id and cpo.target_user_id=p_viewer_user_id
  where p.id=p_owner_user_id
  on conflict(owner_user_id,viewer_user_id) do update set presence=excluded.presence,last_seen_at=excluded.last_seen_at,updated_at=excluded.updated_at;
end; $$;

create or replace function private.refresh_presence_projection_for_owner(p_owner_user_id uuid)
returns void language plpgsql security definer set search_path to 'public','private' as $$
declare r record;
begin
  for r in select distinct cm2.user_id viewer_user_id from public.conversation_members cm1 join public.conversation_members cm2 on cm2.conversation_id=cm1.conversation_id and cm2.user_id<>cm1.user_id where cm1.user_id=p_owner_user_id loop
    perform private.refresh_presence_projection_pair(p_owner_user_id,r.viewer_user_id);
  end loop;
end; $$;

create or replace function private.on_profile_presence_changed() returns trigger language plpgsql security definer set search_path to 'public','private' as $$ begin perform private.refresh_presence_projection_for_owner(new.id); return new; end; $$;
create or replace function private.on_contact_privacy_presence_changed() returns trigger language plpgsql security definer set search_path to 'public','private' as $$ begin perform private.refresh_presence_projection_pair(coalesce(new.owner_user_id,old.owner_user_id),coalesce(new.target_user_id,old.target_user_id)); return coalesce(new,old); end; $$;
create or replace function private.on_conversation_membership_presence_changed() returns trigger language plpgsql security definer set search_path to 'public','private' as $$
declare v_conversation uuid:=coalesce(new.conversation_id,old.conversation_id); v_user uuid:=coalesce(new.user_id,old.user_id); peer record;
begin
  for peer in select cm.user_id from public.conversation_members cm where cm.conversation_id=v_conversation and cm.user_id<>v_user loop
    perform private.refresh_presence_projection_pair(v_user,peer.user_id); perform private.refresh_presence_projection_pair(peer.user_id,v_user);
  end loop;
  return coalesce(new,old);
end; $$;

drop trigger if exists profiles_refresh_contact_presence on public.profiles;
create trigger profiles_refresh_contact_presence after insert or update of presence,last_seen_at on public.profiles for each row execute function private.on_profile_presence_changed();
drop trigger if exists contact_privacy_refresh_presence on public.contact_privacy_overrides;
create trigger contact_privacy_refresh_presence after insert or update of hide_online,hide_last_seen or delete on public.contact_privacy_overrides for each row execute function private.on_contact_privacy_presence_changed();
drop trigger if exists conversation_members_refresh_presence on public.conversation_members;
create trigger conversation_members_refresh_presence after insert or delete on public.conversation_members for each row execute function private.on_conversation_membership_presence_changed();

-- Calls are server-gated until the direct contact connection is mutually accepted.
drop policy if exists call_sessions_caller_insert on public.call_sessions;
create policy call_sessions_caller_insert on public.call_sessions for insert to authenticated with check(
  (select auth.uid())=caller_id and caller_id<>callee_id
  and exists(select 1 from public.conversation_members cm where cm.conversation_id=call_sessions.conversation_id and cm.user_id=(select auth.uid()))
  and exists(select 1 from public.conversation_members cm where cm.conversation_id=call_sessions.conversation_id and cm.user_id=call_sessions.callee_id)
  and exists(select 1 from public.contact_connections cc where cc.conversation_id=call_sessions.conversation_id and cc.low_accepted and cc.high_accepted
    and ((cc.user_low_id=caller_id and cc.user_high_id=callee_id) or (cc.user_high_id=caller_id and cc.user_low_id=callee_id)))
);

-- Never leak canonical presence from profile lookup RPCs.
create or replace function public.search_profiles(p_query text)
returns table(id uuid,username text,display_name text,avatar_url text,about text,avatar_initials text,avatar_color_hex text,presence text,last_seen_at timestamptz,is_verified boolean)
language sql stable security definer set search_path to 'public' as $$
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,'offline'::text,null::timestamptz,p.is_verified
  from public.profiles p where auth.uid() is not null and p.id<>auth.uid() and char_length(trim(p_query))>=2
    and (strpos(lower(p.username),lower(trim(leading '@' from trim(p_query))))>0 or strpos(lower(p.display_name),lower(trim(p_query)))>0)
  order by case when lower(p.username)=lower(trim(leading '@' from trim(p_query))) then 0 else 1 end,p.display_name limit 30;
$$;

create or replace function public.get_conversation_members(p_conversation_id uuid)
returns table(id uuid,username text,display_name text,avatar_url text,about text,avatar_initials text,avatar_color_hex text,presence text,last_seen_at timestamptz,is_verified boolean,role text)
language plpgsql stable security definer set search_path to 'public' as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,
    case when p.id=auth.uid() then p.presence else coalesce(cpv.presence,'offline') end,
    case when p.id=auth.uid() then p.last_seen_at else cpv.last_seen_at end,p.is_verified,cm.role
  from public.conversation_members cm join public.profiles p on p.id=cm.user_id
  left join public.contact_presence_visibility cpv on cpv.owner_user_id=p.id and cpv.viewer_user_id=auth.uid()
  where cm.conversation_id=p_conversation_id
  order by case cm.role when 'owner' then 0 when 'admin' then 1 else 2 end,cm.joined_at;
end; $$;

do $$ begin
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='contact_presence_visibility') then
    alter publication supabase_realtime add table public.contact_presence_visibility;
  end if;
end $$;
