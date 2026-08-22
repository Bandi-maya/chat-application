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
  primary key (owner_user_id, target_user_id),
  constraint contact_privacy_not_self check (owner_user_id <> target_user_id)
);

alter table public.contact_privacy_overrides enable row level security;
drop policy if exists contact_privacy_select_self on public.contact_privacy_overrides;
create policy contact_privacy_select_self on public.contact_privacy_overrides for select to authenticated using (owner_user_id = auth.uid());
drop policy if exists contact_privacy_insert_self on public.contact_privacy_overrides;
create policy contact_privacy_insert_self on public.contact_privacy_overrides for insert to authenticated with check (owner_user_id = auth.uid());
drop policy if exists contact_privacy_update_self on public.contact_privacy_overrides;
create policy contact_privacy_update_self on public.contact_privacy_overrides for update to authenticated using (owner_user_id = auth.uid()) with check (owner_user_id = auth.uid());
drop policy if exists contact_privacy_delete_self on public.contact_privacy_overrides;
create policy contact_privacy_delete_self on public.contact_privacy_overrides for delete to authenticated using (owner_user_id = auth.uid());
revoke all on public.contact_privacy_overrides from anon;
grant select, insert, update, delete on public.contact_privacy_overrides to authenticated;

create table if not exists public.contact_connections (
  user_low_id uuid not null references public.profiles(id) on delete cascade,
  user_high_id uuid not null references public.profiles(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  low_accepted boolean not null default false,
  high_accepted boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_low_id, user_high_id),
  unique (conversation_id),
  constraint contact_connection_ordered check (user_low_id::text < user_high_id::text)
);

alter table public.contact_connections enable row level security;
drop policy if exists contact_connections_select_participant on public.contact_connections;
create policy contact_connections_select_participant on public.contact_connections for select to authenticated using (auth.uid() = user_low_id or auth.uid() = user_high_id);
revoke all on public.contact_connections from anon, authenticated;
grant select on public.contact_connections to authenticated;

create table if not exists public.linked_devices (
  user_id uuid not null references public.profiles(id) on delete cascade,
  device_id text not null,
  device_name text not null default 'Chaty device',
  platform text not null default 'Unknown',
  location text not null default '',
  last_active_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (user_id, device_id)
);

alter table public.linked_devices enable row level security;
drop policy if exists linked_devices_manage_self on public.linked_devices;
create policy linked_devices_manage_self on public.linked_devices for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
revoke all on public.linked_devices from anon;
grant select, insert, update, delete on public.linked_devices to authenticated;

create or replace function public.create_direct_conversation(p_other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_me uuid := auth.uid();
  v_conversation_id uuid;
  v_direct_key text;
  v_low uuid;
  v_high uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id = v_me then raise exception 'cannot create a direct conversation with yourself'; end if;
  if not exists(select 1 from public.profiles where id = p_other_user_id) then raise exception 'user not found'; end if;
  v_direct_key := least(v_me::text,p_other_user_id::text)||':'||greatest(v_me::text,p_other_user_id::text);
  insert into public.conversations(kind,direct_key,created_by) values('direct',v_direct_key,v_me)
  on conflict(direct_key) do update set updated_at=public.conversations.updated_at returning id into v_conversation_id;
  insert into public.conversation_members(conversation_id,user_id,role)
  values(v_conversation_id,v_me,'owner'),(v_conversation_id,p_other_user_id,'member') on conflict(conversation_id,user_id) do nothing;
  if v_me::text < p_other_user_id::text then v_low := v_me; v_high := p_other_user_id; else v_low := p_other_user_id; v_high := v_me; end if;
  insert into public.contact_connections(user_low_id,user_high_id,conversation_id,low_accepted,high_accepted)
  values(v_low,v_high,v_conversation_id,v_me=v_low,v_me=v_high)
  on conflict(user_low_id,user_high_id) do update set
    conversation_id=excluded.conversation_id,
    low_accepted=public.contact_connections.low_accepted or excluded.low_accepted,
    high_accepted=public.contact_connections.high_accepted or excluded.high_accepted,
    updated_at=now();
  return v_conversation_id;
end;
$function$;
revoke all on function public.create_direct_conversation(uuid) from public, anon;
grant execute on function public.create_direct_conversation(uuid) to authenticated;

create or replace function public.accept_contact_connection(p_other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_me uuid := auth.uid();
  v_conversation_id uuid;
  v_low uuid;
  v_high uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id = v_me then raise exception 'invalid contact'; end if;
  v_conversation_id := public.create_direct_conversation(p_other_user_id);
  if v_me::text < p_other_user_id::text then v_low := v_me; v_high := p_other_user_id; else v_low := p_other_user_id; v_high := v_me; end if;
  update public.contact_connections set
    low_accepted = case when v_me=v_low then true else low_accepted end,
    high_accepted = case when v_me=v_high then true else high_accepted end,
    updated_at = now()
  where user_low_id=v_low and user_high_id=v_high;
  return v_conversation_id;
end;
$function$;
revoke all on function public.accept_contact_connection(uuid) from public, anon;
grant execute on function public.accept_contact_connection(uuid) to authenticated;

create or replace function public.get_contact_connection_status(p_other_user_id uuid)
returns table(conversation_id uuid, my_accepted boolean, other_accepted boolean, calls_allowed boolean)
language sql
stable
security definer
set search_path to 'public'
as $function$
  select cc.conversation_id,
    case when auth.uid()=cc.user_low_id then cc.low_accepted else cc.high_accepted end,
    case when auth.uid()=cc.user_low_id then cc.high_accepted else cc.low_accepted end,
    cc.low_accepted and cc.high_accepted
  from public.contact_connections cc
  where (cc.user_low_id=auth.uid() and cc.user_high_id=p_other_user_id)
     or (cc.user_high_id=auth.uid() and cc.user_low_id=p_other_user_id)
  limit 1;
$function$;
revoke all on function public.get_contact_connection_status(uuid) from public, anon;
grant execute on function public.get_contact_connection_status(uuid) to authenticated;

create or replace function public.mark_conversation_delivered(p_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_peer uuid;
  v_hide_delivery boolean := false;
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer
    from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then
    select coalesce(cpo.hide_delivery_receipts,false) into v_hide_delivery
    from public.contact_privacy_overrides cpo
    where cpo.owner_user_id=auth.uid() and cpo.target_user_id=v_peer;
  end if;
  if not coalesce(v_hide_delivery,false) then
    insert into public.message_receipts(message_id,user_id,delivered_at)
    select m.id,auth.uid(),now() from public.messages m
    where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
    on conflict(message_id,user_id) do update set delivered_at=coalesce(public.message_receipts.delivered_at,excluded.delivered_at);
  end if;
end;
$function$;
revoke all on function public.mark_conversation_delivered(uuid) from public, anon;
grant execute on function public.mark_conversation_delivered(uuid) to authenticated;

create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_hide_seen boolean := false;
  v_blue_reply boolean := false;
  v_hide_delivery boolean := false;
  v_hide_read boolean := false;
  v_peer uuid;
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select
    coalesce((settings->'gbFeatures'->>'yoHideSeen')::boolean, not coalesce((settings->'privacy'->>'readReceipts')::boolean,true), false),
    coalesce((settings->'gbFeatures'->>'yoBlueOnReply')::boolean, (settings->'privacy'->>'showBlueTicksAfterReply')::boolean, false)
  into v_hide_seen,v_blue_reply
  from public.user_feature_settings where user_id=auth.uid();
  select cm.user_id into v_peer
    from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then
    select coalesce(cpo.hide_delivery_receipts,false),coalesce(cpo.hide_read_receipts,false)
      into v_hide_delivery,v_hide_read
    from public.contact_privacy_overrides cpo
    where cpo.owner_user_id=auth.uid() and cpo.target_user_id=v_peer;
  end if;
  update public.conversation_members set unread_count=0,last_read_at=now()
    where conversation_id=p_conversation_id and user_id=auth.uid();
  if not coalesce(v_hide_delivery,false) then
    insert into public.message_receipts(message_id,user_id,delivered_at)
    select m.id,auth.uid(),now() from public.messages m
    where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
    on conflict(message_id,user_id) do update set delivered_at=coalesce(public.message_receipts.delivered_at,excluded.delivered_at);
  end if;
  if not coalesce(v_hide_seen,false) and not coalesce(v_blue_reply,false) and not coalesce(v_hide_read,false) then
    insert into public.message_receipts(message_id,user_id,delivered_at,read_at)
    select m.id,auth.uid(),now(),now() from public.messages m
    where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
    on conflict(message_id,user_id) do update set delivered_at=coalesce(public.message_receipts.delivered_at,excluded.delivered_at),read_at=excluded.read_at;
  end if;
end;
$function$;
revoke all on function public.mark_conversation_read(uuid) from public, anon;
grant execute on function public.mark_conversation_read(uuid) to authenticated;

create or replace function public.set_typing_state(p_conversation_id uuid, p_is_typing boolean)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_peer uuid;
  v_hide boolean := false;
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then
    select coalesce(hide_typing,false) into v_hide from public.contact_privacy_overrides
      where owner_user_id=auth.uid() and target_user_id=v_peer;
  end if;
  if p_is_typing and not coalesce(v_hide,false) then
    insert into public.typing_states(conversation_id,user_id,is_typing,is_recording,updated_at)
    values(p_conversation_id,auth.uid(),true,false,now())
    on conflict(conversation_id,user_id) do update set is_typing=true,updated_at=excluded.updated_at;
  else
    update public.typing_states set is_typing=false,updated_at=now()
      where conversation_id=p_conversation_id and user_id=auth.uid();
    delete from public.typing_states where conversation_id=p_conversation_id and user_id=auth.uid() and not is_typing and not is_recording;
  end if;
end;
$function$;
revoke all on function public.set_typing_state(uuid,boolean) from public, anon;
grant execute on function public.set_typing_state(uuid,boolean) to authenticated;

create or replace function public.set_recording_state(p_conversation_id uuid, p_is_recording boolean)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_peer uuid;
  v_hide boolean := false;
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select cm.user_id into v_peer from public.conversations c join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='direct' and cm.user_id<>auth.uid() limit 1;
  if v_peer is not null then
    select coalesce(hide_recording,false) into v_hide from public.contact_privacy_overrides
      where owner_user_id=auth.uid() and target_user_id=v_peer;
  end if;
  if p_is_recording and not coalesce(v_hide,false) then
    insert into public.typing_states(conversation_id,user_id,is_typing,is_recording,updated_at)
    values(p_conversation_id,auth.uid(),false,true,now())
    on conflict(conversation_id,user_id) do update set is_recording=true,updated_at=excluded.updated_at;
  else
    update public.typing_states set is_recording=false,updated_at=now()
      where conversation_id=p_conversation_id and user_id=auth.uid();
    delete from public.typing_states where conversation_id=p_conversation_id and user_id=auth.uid() and not is_typing and not is_recording;
  end if;
end;
$function$;
revoke all on function public.set_recording_state(uuid,boolean) from public, anon;
grant execute on function public.set_recording_state(uuid,boolean) to authenticated;

create or replace function public.get_conversation_members(p_conversation_id uuid)
returns table(id uuid, username text, display_name text, avatar_url text, about text, avatar_initials text, avatar_color_hex text, presence text, last_seen_at timestamptz, is_verified boolean, role text)
language plpgsql
stable security definer
set search_path to 'public'
as $function$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,
    case when p.id<>auth.uid() and coalesce(cpo.hide_online,false) then 'offline' else p.presence end,
    case when p.id<>auth.uid() and coalesce(cpo.hide_last_seen,false) then null else p.last_seen_at end,
    p.is_verified,cm.role
  from public.conversation_members cm
  join public.profiles p on p.id=cm.user_id
  left join public.contact_privacy_overrides cpo on cpo.owner_user_id=p.id and cpo.target_user_id=auth.uid()
  where cm.conversation_id=p_conversation_id
  order by case cm.role when 'owner' then 0 when 'admin' then 1 else 2 end,cm.joined_at;
end;
$function$;
revoke all on function public.get_conversation_members(uuid) from public, anon;
grant execute on function public.get_conversation_members(uuid) to authenticated;

create or replace function public.search_profiles(p_query text)
returns table(id uuid, username text, display_name text, avatar_url text, about text, avatar_initials text, avatar_color_hex text, presence text, last_seen_at timestamptz, is_verified boolean)
language sql
stable security definer
set search_path to 'public'
as $function$
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,
    case when coalesce(cpo.hide_online,false) then 'offline' else p.presence end,
    case when coalesce(cpo.hide_last_seen,false) then null else p.last_seen_at end,
    p.is_verified
  from public.profiles p
  left join public.contact_privacy_overrides cpo on cpo.owner_user_id=p.id and cpo.target_user_id=auth.uid()
  where auth.uid() is not null and p.id<>auth.uid() and char_length(trim(p_query))>=2
    and (strpos(lower(p.username),lower(trim(leading '@' from trim(p_query))))>0 or strpos(lower(p.display_name),lower(trim(p_query)))>0)
  order by case when lower(p.username)=lower(trim(leading '@' from trim(p_query))) then 0 else 1 end,p.display_name limit 30;
$function$;
revoke all on function public.search_profiles(text) from public, anon;
grant execute on function public.search_profiles(text) to authenticated;

create or replace function public.get_conversation_messages(p_conversation_id uuid, p_limit integer default 100, p_before timestamptz default null)
returns table(id uuid, conversation_id uuid, sender_id uuid, type text, body text, metadata jsonb, created_at timestamptz, edited_at timestamptz, deleted_at timestamptz, reactions jsonb, is_starred boolean, is_pinned boolean, is_hidden boolean, is_read_by_other boolean)
language plpgsql
stable security definer
set search_path to 'public'
as $function$
declare v_anti_delete boolean:=false;
begin
 if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
 select coalesce((settings->'gbFeatures'->>'yoAntiRevoke')::boolean,(settings->'privacy'->>'antiDeleteMessages')::boolean,false)
 into v_anti_delete from public.user_feature_settings where user_id=auth.uid();
 return query
 select m.id,m.conversation_id,m.sender_id,m.type,
   case when m.type='poll' then '[POLL] '||m.body else m.body end,
   ((case when m.type='poll' then m.metadata||jsonb_build_object('poll',(select jsonb_build_object(
      'id',p.id,'question',p.question,'allow_multiple',p.allow_multiple,
      'total_votes',(select count(*) from public.poll_votes pv where pv.poll_id=p.id),
      'options',coalesce((select jsonb_agg(jsonb_build_object('id',po.id,'label',po.label,'position',po.position,'votes',(select count(*) from public.poll_votes pv where pv.option_id=po.id),'voted_by_me',exists(select 1 from public.poll_votes pv where pv.option_id=po.id and pv.user_id=auth.uid())) order by po.position) from public.poll_options po where po.poll_id=p.id),'[]'::jsonb)
   ) from public.polls p where p.message_id=m.id)) else m.metadata end)
   || case when m.deleted_at is not null and v_anti_delete then jsonb_build_object('anti_deleted',true,'deleted_original_at',m.deleted_at) else '{}'::jsonb end
   || jsonb_build_object('delivery_state', case when m.sender_id=auth.uid() then
        case when exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null) then 'read'
             when exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.delivered_at is not null) then 'delivered'
             else 'sent' end
        else 'delivered' end)),
   m.created_at,m.edited_at,case when v_anti_delete then null else m.deleted_at end,
   coalesce((select jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) from (select mr.emoji,array_agg(mr.user_id) user_ids from public.message_reactions mr where mr.message_id=m.id group by mr.emoji)x),'[]'::jsonb),
   coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
   exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null)
 from public.messages m left join public.message_user_state mus on mus.message_id=m.id and mus.user_id=auth.uid()
 where m.conversation_id=p_conversation_id and (p_before is null or m.created_at<p_before)
 order by m.created_at desc limit greatest(1,least(coalesce(p_limit,100),200));
end;
$function$;
revoke all on function public.get_conversation_messages(uuid,integer,timestamptz) from public, anon;
grant execute on function public.get_conversation_messages(uuid,integer,timestamptz) to authenticated;

do $do$
begin
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='profiles') then execute 'alter publication supabase_realtime add table public.profiles'; end if;
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='contact_connections') then execute 'alter publication supabase_realtime add table public.contact_connections'; end if;
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='linked_devices') then execute 'alter publication supabase_realtime add table public.linked_devices'; end if;
end;
$do$;
