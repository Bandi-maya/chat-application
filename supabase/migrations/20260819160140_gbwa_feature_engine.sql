create extension if not exists pg_cron with schema pg_catalog;

create table if not exists public.user_feature_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  settings jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.user_feature_settings enable row level security;
drop policy if exists user_feature_settings_select_self on public.user_feature_settings;
create policy user_feature_settings_select_self on public.user_feature_settings for select to authenticated using (user_id = auth.uid());
drop policy if exists user_feature_settings_insert_self on public.user_feature_settings;
create policy user_feature_settings_insert_self on public.user_feature_settings for insert to authenticated with check (user_id = auth.uid());
drop policy if exists user_feature_settings_update_self on public.user_feature_settings;
create policy user_feature_settings_update_self on public.user_feature_settings for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists user_feature_settings_delete_self on public.user_feature_settings;
create policy user_feature_settings_delete_self on public.user_feature_settings for delete to authenticated using (user_id = auth.uid());
grant select, insert, update, delete on public.user_feature_settings to authenticated;

create table if not exists public.blocked_users (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
alter table public.blocked_users enable row level security;
drop policy if exists blocked_users_manage_self on public.blocked_users;
create policy blocked_users_manage_self on public.blocked_users for all to authenticated using (blocker_id = auth.uid()) with check (blocker_id = auth.uid());
grant select, insert, delete on public.blocked_users to authenticated;

create or replace function public.is_blocked_pair(p_a uuid, p_b uuid)
returns boolean language sql stable security definer set search_path=public as $$
  select exists(select 1 from public.blocked_users b where (b.blocker_id=p_a and b.blocked_id=p_b) or (b.blocker_id=p_b and b.blocked_id=p_a));
$$;
revoke all on function public.is_blocked_pair(uuid,uuid) from public, anon;
grant execute on function public.is_blocked_pair(uuid,uuid) to authenticated;

create or replace function public.block_user(p_user_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  if p_user_id=auth.uid() then raise exception 'cannot block yourself'; end if;
  if not exists(select 1 from public.profiles where id=p_user_id) then raise exception 'user not found'; end if;
  insert into public.blocked_users(blocker_id,blocked_id) values(auth.uid(),p_user_id) on conflict do nothing;
end; $$;
revoke all on function public.block_user(uuid) from public, anon;
grant execute on function public.block_user(uuid) to authenticated;

create or replace function public.unblock_user(p_user_id uuid)
returns void language sql security definer set search_path=public as $$
  delete from public.blocked_users where blocker_id=auth.uid() and blocked_id=p_user_id;
$$;
revoke all on function public.unblock_user(uuid) from public, anon;
grant execute on function public.unblock_user(uuid) to authenticated;

create or replace function public.get_my_blocked_users()
returns table(id uuid, username text, display_name text, avatar_url text, blocked_at timestamptz)
language sql stable security definer set search_path=public as $$
  select p.id,p.username,p.display_name,p.avatar_url,b.created_at
  from public.blocked_users b join public.profiles p on p.id=b.blocked_id
  where b.blocker_id=auth.uid() order by b.created_at desc;
$$;
revoke all on function public.get_my_blocked_users() from public, anon;
grant execute on function public.get_my_blocked_users() to authenticated;

create table if not exists public.typing_states (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  is_typing boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key(conversation_id,user_id)
);
alter table public.typing_states enable row level security;
drop policy if exists typing_states_select_member on public.typing_states;
create policy typing_states_select_member on public.typing_states for select to authenticated using (public.is_conversation_member(conversation_id));
drop policy if exists typing_states_insert_self on public.typing_states;
create policy typing_states_insert_self on public.typing_states for insert to authenticated with check (user_id=auth.uid() and public.is_conversation_member(conversation_id));
drop policy if exists typing_states_update_self on public.typing_states;
create policy typing_states_update_self on public.typing_states for update to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid() and public.is_conversation_member(conversation_id));
drop policy if exists typing_states_delete_self on public.typing_states;
create policy typing_states_delete_self on public.typing_states for delete to authenticated using (user_id=auth.uid());
grant select,insert,update,delete on public.typing_states to authenticated;

create or replace function public.set_typing_state(p_conversation_id uuid,p_is_typing boolean)
returns void language plpgsql security definer set search_path=public as $$
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if p_is_typing then
    insert into public.typing_states(conversation_id,user_id,is_typing,updated_at)
    values(p_conversation_id,auth.uid(),true,now())
    on conflict(conversation_id,user_id) do update set is_typing=true,updated_at=excluded.updated_at;
  else
    delete from public.typing_states where conversation_id=p_conversation_id and user_id=auth.uid();
  end if;
end; $$;
revoke all on function public.set_typing_state(uuid,boolean) from public, anon;
grant execute on function public.set_typing_state(uuid,boolean) to authenticated;

create table if not exists public.scheduled_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  body text not null,
  metadata jsonb not null default '{}'::jsonb,
  scheduled_at timestamptz not null,
  state text not null default 'pending' check(state in('pending','sent','cancelled','failed')),
  created_at timestamptz not null default now(),
  executed_at timestamptz,
  last_error text,
  check(char_length(body) between 1 and 10000)
);
create index if not exists scheduled_messages_due_idx on public.scheduled_messages(state,scheduled_at);
alter table public.scheduled_messages enable row level security;
drop policy if exists scheduled_messages_self on public.scheduled_messages;
create policy scheduled_messages_self on public.scheduled_messages for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
grant select,insert,update,delete on public.scheduled_messages to authenticated;

create or replace function public.schedule_message(p_conversation_id uuid,p_body text,p_scheduled_at timestamptz,p_metadata jsonb default '{}'::jsonb)
returns uuid language plpgsql security definer set search_path=public as $$
declare v_id uuid;
begin
  if auth.uid() is null or not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if trim(coalesce(p_body,''))='' then raise exception 'message body is required'; end if;
  if p_scheduled_at < now()-interval '1 minute' then raise exception 'scheduled time is in the past'; end if;
  insert into public.scheduled_messages(user_id,conversation_id,body,metadata,scheduled_at)
  values(auth.uid(),p_conversation_id,trim(p_body),coalesce(p_metadata,'{}'::jsonb),p_scheduled_at) returning id into v_id;
  return v_id;
end; $$;
revoke all on function public.schedule_message(uuid,text,timestamptz,jsonb) from public, anon;
grant execute on function public.schedule_message(uuid,text,timestamptz,jsonb) to authenticated;

create or replace function public.cancel_scheduled_message(p_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
 update public.scheduled_messages set state='cancelled' where id=p_id and user_id=auth.uid() and state='pending';
end; $$;
revoke all on function public.cancel_scheduled_message(uuid) from public, anon;
grant execute on function public.cancel_scheduled_message(uuid) to authenticated;

create table if not exists public.auto_reply_rules (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  keyword text not null,
  response_body text not null,
  scope text not null default 'all' check(scope in('all','direct','group')),
  enabled boolean not null default true,
  cooldown_seconds integer not null default 60 check(cooldown_seconds between 0 and 86400),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check(char_length(keyword) between 1 and 100),
  check(char_length(response_body) between 1 and 10000)
);
alter table public.auto_reply_rules enable row level security;
drop policy if exists auto_reply_rules_self on public.auto_reply_rules;
create policy auto_reply_rules_self on public.auto_reply_rules for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
grant select,insert,update,delete on public.auto_reply_rules to authenticated;

create table if not exists public.auto_reply_log (
  rule_id uuid not null references public.auto_reply_rules(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  last_sent_at timestamptz not null default now(),
  primary key(rule_id,conversation_id)
);
alter table public.auto_reply_log enable row level security;

create table if not exists public.quick_reply_templates (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  shortcut text not null,
  title text not null default '',
  content text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id,shortcut),
  check(char_length(shortcut) between 1 and 40),
  check(char_length(content) between 1 and 10000)
);
alter table public.quick_reply_templates enable row level security;
drop policy if exists quick_reply_templates_self on public.quick_reply_templates;
create policy quick_reply_templates_self on public.quick_reply_templates for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
grant select,insert,update,delete on public.quick_reply_templates to authenticated;

create table if not exists public.mass_message_collections (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  conversation_ids uuid[] not null default '{}'::uuid[],
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check(char_length(name) between 1 and 80)
);
alter table public.mass_message_collections enable row level security;
drop policy if exists mass_message_collections_self on public.mass_message_collections;
create policy mass_message_collections_self on public.mass_message_collections for all to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
grant select,insert,update,delete on public.mass_message_collections to authenticated;

alter table public.status_updates add column if not exists media_name text;
alter table public.status_updates add column if not exists media_size bigint not null default 0;
alter table public.status_updates add column if not exists duration_seconds integer not null default 0;
alter table public.status_updates add column if not exists deleted_at timestamptz;

create or replace function public.mark_status_viewed(p_status_id uuid,p_hide_view boolean default false)
returns void language plpgsql security definer set search_path=public as $$
declare v_owner uuid;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  select user_id into v_owner from public.status_updates where id=p_status_id and expires_at>now();
  if v_owner is null or v_owner=auth.uid() or p_hide_view then return; end if;
  insert into public.status_views(status_id,viewer_id,viewed_at) values(p_status_id,auth.uid(),now())
  on conflict(status_id,viewer_id) do update set viewed_at=excluded.viewed_at;
end; $$;
revoke all on function public.mark_status_viewed(uuid,boolean) from public, anon;
grant execute on function public.mark_status_viewed(uuid,boolean) to authenticated;

create or replace function public.delete_status_update(p_status_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.status_updates set deleted_at=now() where id=p_status_id and user_id=auth.uid();
end; $$;
revoke all on function public.delete_status_update(uuid) from public, anon;
grant execute on function public.delete_status_update(uuid) to authenticated;

create table if not exists public.message_edit_history (
  id bigint generated always as identity primary key,
  message_id uuid not null references public.messages(id) on delete cascade,
  editor_id uuid not null references auth.users(id) on delete cascade,
  previous_body text not null,
  new_body text not null,
  edited_at timestamptz not null default now()
);
alter table public.message_edit_history enable row level security;
drop policy if exists message_edit_history_member_select on public.message_edit_history;
create policy message_edit_history_member_select on public.message_edit_history for select to authenticated using(
  exists(select 1 from public.messages m where m.id=message_id and public.is_conversation_member(m.conversation_id))
);
grant select on public.message_edit_history to authenticated;

create or replace function public.edit_chat_message(p_message_id uuid,p_body text)
returns void language plpgsql security definer set search_path=public as $$
declare v_old text; v_sender uuid; v_conversation uuid;
begin
  select body,sender_id,conversation_id into v_old,v_sender,v_conversation from public.messages where id=p_message_id and deleted_at is null;
  if v_sender is null or v_sender<>auth.uid() or not public.is_conversation_member(v_conversation) then raise exception 'not authorized'; end if;
  if trim(coalesce(p_body,''))='' or char_length(p_body)>10000 then raise exception 'invalid message body'; end if;
  insert into public.message_edit_history(message_id,editor_id,previous_body,new_body) values(p_message_id,auth.uid(),v_old,trim(p_body));
  update public.messages set body=trim(p_body),edited_at=now() where id=p_message_id;
end; $$;
revoke all on function public.edit_chat_message(uuid,text) from public, anon;
grant execute on function public.edit_chat_message(uuid,text) to authenticated;

create or replace function public.delete_chat_message(p_message_id uuid,p_for_everyone boolean)
returns void language plpgsql security definer set search_path=public as $$
declare v_sender uuid; v_conversation uuid;
begin
  select sender_id,conversation_id into v_sender,v_conversation from public.messages where id=p_message_id;
  if v_conversation is null or not public.is_conversation_member(v_conversation) then raise exception 'not authorized'; end if;
  if p_for_everyone then
    if v_sender<>auth.uid() then raise exception 'only sender can delete for everyone'; end if;
    update public.messages set deleted_at=coalesce(deleted_at,now()) where id=p_message_id;
  else
    perform public.set_message_user_state(p_message_id,'hidden',true);
  end if;
end; $$;
revoke all on function public.delete_chat_message(uuid,boolean) from public, anon;
grant execute on function public.delete_chat_message(uuid,boolean) to authenticated;

create or replace function public.process_scheduled_messages()
returns integer language plpgsql security definer set search_path=public as $$
declare r record; v_count integer:=0;
begin
  for r in select * from public.scheduled_messages where state='pending' and scheduled_at<=now() order by scheduled_at for update skip locked loop
    begin
      if not exists(select 1 from public.conversation_members cm where cm.conversation_id=r.conversation_id and cm.user_id=r.user_id) then
        update public.scheduled_messages set state='failed',last_error='user is no longer a conversation member' where id=r.id;
        continue;
      end if;
      insert into public.messages(conversation_id,sender_id,client_message_id,type,body,metadata)
      values(r.conversation_id,r.user_id,r.id,'text',r.body,coalesce(r.metadata,'{}'::jsonb)||jsonb_build_object('scheduled',true,'scheduled_message_id',r.id))
      on conflict(sender_id,client_message_id) do nothing;
      update public.conversations set updated_at=now() where id=r.conversation_id;
      update public.conversation_members set unread_count=unread_count+1 where conversation_id=r.conversation_id and user_id<>r.user_id;
      update public.scheduled_messages set state='sent',executed_at=now(),last_error=null where id=r.id;
      v_count:=v_count+1;
    exception when others then
      update public.scheduled_messages set state='failed',last_error=left(sqlerrm,500) where id=r.id;
    end;
  end loop;
  return v_count;
end; $$;
revoke all on function public.process_scheduled_messages() from public, anon, authenticated;

create or replace function public.handle_message_auto_reply()
returns trigger language plpgsql security definer set search_path=public as $$
declare r record; v_kind text;
begin
  if coalesce((new.metadata->>'automation_generated')::boolean,false) then return new; end if;
  select kind into v_kind from public.conversations where id=new.conversation_id;
  for r in
    select ar.* from public.auto_reply_rules ar
    join public.conversation_members cm on cm.user_id=ar.user_id and cm.conversation_id=new.conversation_id
    where ar.enabled and ar.user_id<>new.sender_id
      and (ar.scope='all' or ar.scope=v_kind)
      and position(lower(ar.keyword) in lower(new.body))>0
      and not public.is_blocked_pair(ar.user_id,new.sender_id)
      and not exists(
        select 1 from public.auto_reply_log l where l.rule_id=ar.id and l.conversation_id=new.conversation_id
        and l.last_sent_at > now() - make_interval(secs=>ar.cooldown_seconds)
      )
  loop
    insert into public.messages(conversation_id,sender_id,client_message_id,type,body,metadata)
    values(new.conversation_id,r.user_id,gen_random_uuid(),'text',r.response_body,jsonb_build_object('automation_generated',true,'auto_reply_rule_id',r.id));
    update public.auto_reply_log set last_sent_at=now() where rule_id=r.id and conversation_id=new.conversation_id;
    if not found then insert into public.auto_reply_log(rule_id,conversation_id,last_sent_at) values(r.id,new.conversation_id,now()); end if;
    update public.conversation_members set unread_count=unread_count+1 where conversation_id=new.conversation_id and user_id<>r.user_id;
  end loop;
  return new;
end; $$;
drop trigger if exists messages_auto_reply on public.messages;
create trigger messages_auto_reply after insert on public.messages for each row execute function public.handle_message_auto_reply();

create or replace function public.mass_send_message(p_conversation_ids uuid[],p_body text)
returns integer language plpgsql security definer set search_path=public as $$
declare v_id uuid; v_count integer:=0; v_limit integer:=5; v_increased boolean:=false;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  if trim(coalesce(p_body,''))='' then raise exception 'message body is required'; end if;
  select coalesce((settings->'gbFeatures'->>'fwd_lim_incr')::boolean,false) into v_increased from public.user_feature_settings where user_id=auth.uid();
  if coalesce(v_increased,false) then v_limit:=50; end if;
  if cardinality(p_conversation_ids)>v_limit then raise exception 'conversation limit exceeded'; end if;
  foreach v_id in array p_conversation_ids loop
    if public.is_conversation_member(v_id) then
      perform public.send_message(v_id,gen_random_uuid(),trim(p_body),'text',jsonb_build_object('mass_send',true));
      v_count:=v_count+1;
    end if;
  end loop;
  return v_count;
end; $$;
revoke all on function public.mass_send_message(uuid[],text) from public, anon;
grant execute on function public.mass_send_message(uuid[],text) to authenticated;

select cron.unschedule(jobid) from cron.job where jobname='chaty-process-scheduled-messages';
select cron.schedule('chaty-process-scheduled-messages','* * * * *','select public.process_scheduled_messages();');

do $$ begin
  begin alter publication supabase_realtime add table public.typing_states; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.scheduled_messages; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.user_feature_settings; exception when duplicate_object then null; end;
end $$;
