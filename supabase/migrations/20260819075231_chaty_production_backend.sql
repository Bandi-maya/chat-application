-- Chaty production backend expansion for the full Flutter UI.
-- Existing RPCs whose OUT row shapes are changing must be replaced explicitly.
drop function if exists public.search_profiles(text);
drop function if exists public.get_conversation_members(uuid);
drop function if exists public.get_my_conversations();
drop function if exists public.get_my_tasks();
drop function if exists public.send_message(uuid, uuid, text, text);
drop function if exists public.create_chat_task(uuid, uuid, text, uuid, text, timestamptz);

create extension if not exists pgcrypto;

alter table public.profiles add column if not exists about text not null default 'Hey there! I am using Chaty.';
alter table public.profiles add column if not exists phone text not null default '';
alter table public.profiles add column if not exists avatar_initials text not null default 'CU';
alter table public.profiles add column if not exists avatar_color_hex text not null default '0xFF6366F1';
alter table public.profiles add column if not exists presence text not null default 'offline';
alter table public.profiles add column if not exists last_seen_at timestamptz not null default now();
alter table public.profiles add column if not exists is_verified boolean not null default false;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'profiles_presence_check') then
    alter table public.profiles add constraint profiles_presence_check check (presence in ('online','away','offline','typing'));
  end if;
end $$;

alter table public.conversation_members add column if not exists is_pinned boolean not null default false;
alter table public.conversation_members add column if not exists is_archived boolean not null default false;
alter table public.conversation_members add column if not exists is_muted boolean not null default false;
alter table public.conversation_members add column if not exists unread_count integer not null default 0;
alter table public.conversation_members add column if not exists draft_text text not null default '';
alter table public.conversation_members add column if not exists last_read_at timestamptz;

alter table public.messages drop constraint if exists messages_type_check;
alter table public.messages add constraint messages_type_check check (
  type in ('text','image','video','audio','document','location','contact','task','system')
);

create table if not exists public.message_reactions (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  emoji text not null check (char_length(emoji) between 1 and 32),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id, emoji)
);
create index if not exists message_reactions_message_idx on public.message_reactions(message_id);

create table if not exists public.message_user_state (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  is_starred boolean not null default false,
  is_pinned boolean not null default false,
  is_hidden boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

create table if not exists public.message_receipts (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  read_at timestamptz,
  primary key (message_id, user_id)
);
create index if not exists message_receipts_message_idx on public.message_receipts(message_id);

alter table public.tasks add column if not exists description text not null default '';
alter table public.tasks add column if not exists labels text[] not null default '{}';
alter table public.tasks add column if not exists source_message_id uuid references public.messages(id) on delete set null;

create table if not exists public.task_activity (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references public.tasks(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete restrict,
  action text not null,
  created_at timestamptz not null default now()
);
create index if not exists task_activity_task_idx on public.task_activity(task_id, created_at);

alter table public.message_reactions enable row level security;
alter table public.message_user_state enable row level security;
alter table public.message_receipts enable row level security;
alter table public.task_activity enable row level security;

drop policy if exists message_reactions_select_member on public.message_reactions;
create policy message_reactions_select_member on public.message_reactions for select to authenticated using (
  exists (select 1 from public.messages m where m.id = message_id and public.is_conversation_member(m.conversation_id))
);
drop policy if exists message_reactions_insert_self on public.message_reactions;
create policy message_reactions_insert_self on public.message_reactions for insert to authenticated with check (
  user_id = auth.uid() and exists (select 1 from public.messages m where m.id = message_id and public.is_conversation_member(m.conversation_id))
);
drop policy if exists message_reactions_delete_self on public.message_reactions;
create policy message_reactions_delete_self on public.message_reactions for delete to authenticated using (user_id = auth.uid());

drop policy if exists message_user_state_select_self on public.message_user_state;
create policy message_user_state_select_self on public.message_user_state for select to authenticated using (user_id = auth.uid());
drop policy if exists message_user_state_insert_self on public.message_user_state;
create policy message_user_state_insert_self on public.message_user_state for insert to authenticated with check (
  user_id = auth.uid() and exists (select 1 from public.messages m where m.id = message_id and public.is_conversation_member(m.conversation_id))
);
drop policy if exists message_user_state_update_self on public.message_user_state;
create policy message_user_state_update_self on public.message_user_state for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists message_receipts_select_member on public.message_receipts;
create policy message_receipts_select_member on public.message_receipts for select to authenticated using (
  exists (select 1 from public.messages m where m.id = message_id and public.is_conversation_member(m.conversation_id))
);
drop policy if exists message_receipts_write_self on public.message_receipts;
create policy message_receipts_write_self on public.message_receipts for insert to authenticated with check (user_id = auth.uid());
drop policy if exists message_receipts_update_self on public.message_receipts;
create policy message_receipts_update_self on public.message_receipts for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists task_activity_select_member on public.task_activity;
create policy task_activity_select_member on public.task_activity for select to authenticated using (
  exists (select 1 from public.tasks t where t.id = task_id and public.is_conversation_member(t.conversation_id))
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_username text; v_display_name text; v_initials text; v_color text;
begin
  v_username := lower(coalesce(nullif(new.raw_user_meta_data ->> 'username', ''), 'user_' || substr(new.id::text, 1, 8)));
  v_display_name := coalesce(nullif(new.raw_user_meta_data ->> 'display_name', ''), 'Chaty User');
  v_initials := upper(coalesce(nullif(new.raw_user_meta_data ->> 'avatar_initials', ''), nullif(left(regexp_replace(v_display_name, '[^A-Za-z0-9]', '', 'g'), 2), ''), 'CU'));
  v_color := coalesce(nullif(new.raw_user_meta_data ->> 'avatar_color_hex', ''), '0xFF6366F1');
  insert into public.profiles(id,username,display_name,about,phone,avatar_initials,avatar_color_hex,presence,last_seen_at)
  values(new.id,v_username,v_display_name,coalesce(nullif(new.raw_user_meta_data ->> 'about',''),'Hey there! I am using Chaty.'),coalesce(new.phone,''),v_initials,v_color,'offline',now())
  on conflict(id) do update set username=excluded.username,display_name=excluded.display_name,about=excluded.about,phone=excluded.phone,avatar_initials=excluded.avatar_initials,avatar_color_hex=excluded.avatar_color_hex,updated_at=now();
  return new;
end; $$;

create or replace function public.is_username_available(p_username text)
returns boolean language sql stable security definer set search_path=public as $$
  select char_length(trim(p_username)) between 3 and 24
     and trim(p_username) ~ '^[A-Za-z0-9_]+$'
     and not exists(select 1 from public.profiles p where lower(p.username)=lower(trim(p_username)));
$$;
revoke all on function public.is_username_available(text) from public;
grant execute on function public.is_username_available(text) to anon,authenticated;

create function public.search_profiles(p_query text)
returns table(id uuid,username text,display_name text,avatar_url text,about text,avatar_initials text,avatar_color_hex text,presence text,last_seen_at timestamptz,is_verified boolean)
language sql stable security definer set search_path=public as $$
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,p.presence,p.last_seen_at,p.is_verified
  from public.profiles p
  where auth.uid() is not null and p.id<>auth.uid() and char_length(trim(p_query))>=2
    and (strpos(lower(p.username),lower(trim(leading '@' from trim(p_query))))>0 or strpos(lower(p.display_name),lower(trim(p_query)))>0)
  order by case when lower(p.username)=lower(trim(leading '@' from trim(p_query))) then 0 else 1 end,p.display_name limit 30;
$$;
revoke all on function public.search_profiles(text) from public;
grant execute on function public.search_profiles(text) to authenticated;

create or replace function public.create_direct_conversation(p_other_user_id uuid)
returns uuid language plpgsql security definer set search_path=public as $$
declare v_me uuid:=auth.uid(); v_conversation_id uuid; v_direct_key text;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id=v_me then raise exception 'cannot create a direct conversation with yourself'; end if;
  if not exists(select 1 from public.profiles where id=p_other_user_id) then raise exception 'user not found'; end if;
  v_direct_key:=least(v_me::text,p_other_user_id::text)||':'||greatest(v_me::text,p_other_user_id::text);
  insert into public.conversations(kind,direct_key,created_by) values('direct',v_direct_key,v_me)
  on conflict(direct_key) do update set updated_at=public.conversations.updated_at returning id into v_conversation_id;
  insert into public.conversation_members(conversation_id,user_id,role)
  values(v_conversation_id,v_me,'owner'),(v_conversation_id,p_other_user_id,'member') on conflict(conversation_id,user_id) do nothing;
  return v_conversation_id;
end; $$;
revoke all on function public.create_direct_conversation(uuid) from public;
grant execute on function public.create_direct_conversation(uuid) to authenticated;

create or replace function public.create_group_conversation(p_title text,p_member_ids uuid[])
returns uuid language plpgsql security definer set search_path=public as $$
declare v_me uuid:=auth.uid(); v_id uuid; v_member uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if char_length(trim(p_title)) not between 1 and 100 then raise exception 'group title must be 1 to 100 characters'; end if;
  insert into public.conversations(kind,title,created_by) values('group',trim(p_title),v_me) returning id into v_id;
  insert into public.conversation_members(conversation_id,user_id,role) values(v_id,v_me,'owner');
  foreach v_member in array coalesce(p_member_ids,'{}'::uuid[]) loop
    if v_member<>v_me and exists(select 1 from public.profiles where id=v_member) then
      insert into public.conversation_members(conversation_id,user_id,role) values(v_id,v_member,'member') on conflict do nothing;
    end if;
  end loop;
  return v_id;
end; $$;
revoke all on function public.create_group_conversation(text,uuid[]) from public;
grant execute on function public.create_group_conversation(text,uuid[]) to authenticated;

create function public.get_conversation_members(p_conversation_id uuid)
returns table(id uuid,username text,display_name text,avatar_url text,about text,avatar_initials text,avatar_color_hex text,presence text,last_seen_at timestamptz,is_verified boolean,role text)
language plpgsql stable security definer set search_path=public as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,p.presence,p.last_seen_at,p.is_verified,cm.role
  from public.conversation_members cm join public.profiles p on p.id=cm.user_id where cm.conversation_id=p_conversation_id
  order by case cm.role when 'owner' then 0 when 'admin' then 1 else 2 end,cm.joined_at;
end; $$;
revoke all on function public.get_conversation_members(uuid) from public;
grant execute on function public.get_conversation_members(uuid) to authenticated;

create function public.get_my_conversations()
returns table(conversation_id uuid,kind text,title text,avatar_url text,avatar_initials text,avatar_color_hex text,participant_ids uuid[],admin_ids uuid[],last_message text,last_message_sender_id uuid,last_message_at timestamptz,unread_count integer,is_pinned boolean,is_archived boolean,is_muted boolean,draft_text text)
language sql stable security definer set search_path=public as $$
  select c.id,c.kind,
    case when c.kind='direct' then coalesce(other_profile.display_name,other_profile.username,'Conversation') else coalesce(c.title,'Group') end,
    case when c.kind='direct' then other_profile.avatar_url else null end,
    case when c.kind='direct' then other_profile.avatar_initials else upper(left(coalesce(c.title,'GP'),2)) end,
    case when c.kind='direct' then other_profile.avatar_color_hex else '0xFF8B5CF6' end,
    members.participant_ids,members.admin_ids,coalesce(last_message.body,''),last_message.sender_id,coalesce(last_message.created_at,c.created_at),mine.unread_count,mine.is_pinned,mine.is_archived,mine.is_muted,mine.draft_text
  from public.conversations c join public.conversation_members mine on mine.conversation_id=c.id and mine.user_id=auth.uid()
  left join lateral(select p.username,p.display_name,p.avatar_url,p.avatar_initials,p.avatar_color_hex from public.conversation_members cm join public.profiles p on p.id=cm.user_id where cm.conversation_id=c.id and cm.user_id<>auth.uid() order by cm.joined_at limit 1) other_profile on true
  left join lateral(select array_agg(cm.user_id order by cm.joined_at) participant_ids,coalesce(array_agg(cm.user_id order by cm.joined_at) filter(where cm.role in('owner','admin')),'{}'::uuid[]) admin_ids from public.conversation_members cm where cm.conversation_id=c.id) members on true
  left join lateral(select m.body,m.sender_id,m.created_at from public.messages m where m.conversation_id=c.id and m.deleted_at is null order by m.created_at desc limit 1) last_message on true
  order by mine.is_pinned desc,coalesce(last_message.created_at,c.updated_at) desc;
$$;
revoke all on function public.get_my_conversations() from public;
grant execute on function public.get_my_conversations() to authenticated;

create or replace function public.get_conversation_messages(p_conversation_id uuid,p_limit integer default 100,p_before timestamptz default null)
returns table(id uuid,conversation_id uuid,sender_id uuid,type text,body text,metadata jsonb,created_at timestamptz,edited_at timestamptz,deleted_at timestamptz,reactions jsonb,is_starred boolean,is_pinned boolean,is_hidden boolean,is_read_by_other boolean)
language plpgsql stable security definer set search_path=public as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query select m.id,m.conversation_id,m.sender_id,m.type,m.body,m.metadata,m.created_at,m.edited_at,m.deleted_at,
    coalesce((select jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) from (select mr.emoji,array_agg(mr.user_id) user_ids from public.message_reactions mr where mr.message_id=m.id group by mr.emoji)x),'[]'::jsonb),
    coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
    exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null)
  from public.messages m left join public.message_user_state mus on mus.message_id=m.id and mus.user_id=auth.uid()
  where m.conversation_id=p_conversation_id and(p_before is null or m.created_at<p_before)
  order by m.created_at desc limit greatest(1,least(coalesce(p_limit,100),200));
end; $$;
revoke all on function public.get_conversation_messages(uuid,integer,timestamptz) from public;
grant execute on function public.get_conversation_messages(uuid,integer,timestamptz) to authenticated;

create function public.send_message(p_conversation_id uuid,p_client_message_id uuid,p_body text,p_type text default 'text',p_metadata jsonb default '{}'::jsonb)
returns uuid language plpgsql security definer set search_path=public as $$
declare v_me uuid:=auth.uid(); v_id uuid; v_body text:=coalesce(p_body,'');
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if p_type not in('text','image','video','audio','document','location','contact','task','system') then raise exception 'unsupported message type'; end if;
  if p_type='text' and trim(v_body)='' then raise exception 'message body is required'; end if;
  if char_length(v_body)>10000 then raise exception 'message too long'; end if;
  insert into public.messages(conversation_id,sender_id,client_message_id,type,body,metadata) values(p_conversation_id,v_me,p_client_message_id,p_type,v_body,coalesce(p_metadata,'{}'::jsonb))
  on conflict(sender_id,client_message_id) do update set client_message_id=excluded.client_message_id returning id into v_id;
  update public.conversations set updated_at=now() where id=p_conversation_id;
  update public.conversation_members set unread_count=unread_count+1 where conversation_id=p_conversation_id and user_id<>v_me;
  return v_id;
end; $$;
revoke all on function public.send_message(uuid,uuid,text,text,jsonb) from public;
grant execute on function public.send_message(uuid,uuid,text,text,jsonb) to authenticated;

create or replace function public.toggle_message_reaction(p_message_id uuid,p_emoji text)
returns void language plpgsql security definer set search_path=public as $$
declare v_conversation uuid; begin
  select conversation_id into v_conversation from public.messages where id=p_message_id;
  if v_conversation is null or not public.is_conversation_member(v_conversation) then raise exception 'not authorized'; end if;
  if exists(select 1 from public.message_reactions where message_id=p_message_id and user_id=auth.uid() and emoji=p_emoji) then
    delete from public.message_reactions where message_id=p_message_id and user_id=auth.uid() and emoji=p_emoji;
  else insert into public.message_reactions(message_id,user_id,emoji) values(p_message_id,auth.uid(),p_emoji); end if;
end; $$;
revoke all on function public.toggle_message_reaction(uuid,text) from public;
grant execute on function public.toggle_message_reaction(uuid,text) to authenticated;

create or replace function public.set_message_user_state(p_message_id uuid,p_field text,p_value boolean)
returns void language plpgsql security definer set search_path=public as $$
declare v_conversation uuid; begin
  select conversation_id into v_conversation from public.messages where id=p_message_id;
  if v_conversation is null or not public.is_conversation_member(v_conversation) then raise exception 'not authorized'; end if;
  insert into public.message_user_state(message_id,user_id) values(p_message_id,auth.uid()) on conflict do nothing;
  if p_field='starred' then update public.message_user_state set is_starred=p_value,updated_at=now() where message_id=p_message_id and user_id=auth.uid();
  elsif p_field='pinned' then update public.message_user_state set is_pinned=p_value,updated_at=now() where message_id=p_message_id and user_id=auth.uid();
  elsif p_field='hidden' then update public.message_user_state set is_hidden=p_value,updated_at=now() where message_id=p_message_id and user_id=auth.uid();
  else raise exception 'invalid state field'; end if;
end; $$;
revoke all on function public.set_message_user_state(uuid,text,boolean) from public;
grant execute on function public.set_message_user_state(uuid,text,boolean) to authenticated;

create or replace function public.delete_chat_message(p_message_id uuid,p_for_everyone boolean)
returns void language plpgsql security definer set search_path=public as $$
declare v_sender uuid; v_conversation uuid; begin
  select sender_id,conversation_id into v_sender,v_conversation from public.messages where id=p_message_id;
  if v_conversation is null or not public.is_conversation_member(v_conversation) then raise exception 'not authorized'; end if;
  if p_for_everyone then
    if v_sender<>auth.uid() then raise exception 'only sender can delete for everyone'; end if;
    update public.messages set body='',metadata='{}'::jsonb,deleted_at=now() where id=p_message_id;
  else perform public.set_message_user_state(p_message_id,'hidden',true); end if;
end; $$;
revoke all on function public.delete_chat_message(uuid,boolean) from public;
grant execute on function public.delete_chat_message(uuid,boolean) to authenticated;

create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  update public.conversation_members set unread_count=0,last_read_at=now() where conversation_id=p_conversation_id and user_id=auth.uid();
  insert into public.message_receipts(message_id,user_id,read_at)
  select m.id,auth.uid(),now() from public.messages m where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
  on conflict(message_id,user_id) do update set read_at=excluded.read_at;
end; $$;
revoke all on function public.mark_conversation_read(uuid) from public;
grant execute on function public.mark_conversation_read(uuid) to authenticated;

create or replace function public.set_conversation_state(p_conversation_id uuid,p_field text,p_value boolean)
returns void language plpgsql security definer set search_path=public as $$ begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if p_field='pinned' then update public.conversation_members set is_pinned=p_value where conversation_id=p_conversation_id and user_id=auth.uid();
  elsif p_field='archived' then update public.conversation_members set is_archived=p_value where conversation_id=p_conversation_id and user_id=auth.uid();
  elsif p_field='muted' then update public.conversation_members set is_muted=p_value where conversation_id=p_conversation_id and user_id=auth.uid();
  else raise exception 'invalid state field'; end if;
end; $$;
revoke all on function public.set_conversation_state(uuid,text,boolean) from public;
grant execute on function public.set_conversation_state(uuid,text,boolean) to authenticated;

create or replace function public.set_conversation_draft(p_conversation_id uuid,p_draft text)
returns void language plpgsql security definer set search_path=public as $$ begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  update public.conversation_members set draft_text=left(coalesce(p_draft,''),5000) where conversation_id=p_conversation_id and user_id=auth.uid();
end; $$;
revoke all on function public.set_conversation_draft(uuid,text) from public;
grant execute on function public.set_conversation_draft(uuid,text) to authenticated;

create function public.create_chat_task(p_conversation_id uuid,p_client_task_id uuid,p_title text,p_assignee_ids uuid[],p_priority text default 'normal',p_due_at timestamptz default null,p_description text default '',p_labels text[] default '{}',p_source_message_id uuid default null)
returns uuid language plpgsql security definer set search_path=public as $$
declare v_me uuid:=auth.uid(); v_task_id uuid; v_assignee uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if char_length(trim(p_title)) not between 1 and 140 then raise exception 'task title must be 1 to 140 characters'; end if;
  if p_priority not in('low','normal','high','urgent') then raise exception 'invalid priority'; end if;
  insert into public.tasks(conversation_id,creator_id,client_task_id,title,status,priority,due_at,description,labels,source_message_id)
  values(p_conversation_id,v_me,p_client_task_id,trim(p_title),'todo',p_priority,p_due_at,coalesce(p_description,''),coalesce(p_labels,'{}'::text[]),p_source_message_id)
  on conflict(creator_id,client_task_id) do nothing returning id into v_task_id;
  if v_task_id is null then select id into v_task_id from public.tasks where creator_id=v_me and client_task_id=p_client_task_id; return v_task_id; end if;
  foreach v_assignee in array coalesce(p_assignee_ids,array[v_me]) loop
    if not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id and user_id=v_assignee) then raise exception 'assignee is not a member of this conversation'; end if;
    insert into public.task_assignees(task_id,user_id) values(v_task_id,v_assignee) on conflict do nothing;
  end loop;
  insert into public.task_activity(task_id,user_id,action) values(v_task_id,v_me,'created');
  perform public.send_message(p_conversation_id,gen_random_uuid(),trim(p_title),'task',jsonb_build_object('task_id',v_task_id,'title',trim(p_title),'priority',p_priority,'status','todo','due_at',p_due_at,'assignee_ids',coalesce(p_assignee_ids,array[v_me]),'description',coalesce(p_description,''),'labels',coalesce(p_labels,'{}'::text[])));
  return v_task_id;
end; $$;
revoke all on function public.create_chat_task(uuid,uuid,text,uuid[],text,timestamptz,text,text[],uuid) from public;
grant execute on function public.create_chat_task(uuid,uuid,text,uuid[],text,timestamptz,text,text[],uuid) to authenticated;

create function public.get_my_tasks()
returns table(task_id uuid,conversation_id uuid,title text,description text,status text,priority text,due_at timestamptz,created_at timestamptz,updated_at timestamptz,creator_id uuid,assignee_ids uuid[],labels text[],source_message_id uuid)
language sql stable security definer set search_path=public as $$
  select t.id,t.conversation_id,t.title,t.description,t.status,t.priority,t.due_at,t.created_at,t.updated_at,t.creator_id,coalesce(array_agg(ta.user_id) filter(where ta.user_id is not null),'{}'::uuid[]),t.labels,t.source_message_id
  from public.tasks t left join public.task_assignees ta on ta.task_id=t.id
  where t.creator_id=auth.uid() or exists(select 1 from public.task_assignees x where x.task_id=t.id and x.user_id=auth.uid())
  group by t.id order by case when t.status='completed' then 1 else 0 end,t.due_at nulls last,t.created_at desc;
$$;
revoke all on function public.get_my_tasks() from public;
grant execute on function public.get_my_tasks() to authenticated;

create or replace function public.update_task_status(p_task_id uuid,p_status text)
returns void language plpgsql security definer set search_path=public as $$
declare v_me uuid:=auth.uid(); v_conversation uuid; v_creator uuid; begin
  if p_status not in('todo','in_progress','completed','cancelled') then raise exception 'invalid task status'; end if;
  select conversation_id,creator_id into v_conversation,v_creator from public.tasks where id=p_task_id;
  if v_conversation is null then raise exception 'task not found'; end if;
  if v_creator<>v_me and not exists(select 1 from public.task_assignees where task_id=p_task_id and user_id=v_me) then raise exception 'not authorized'; end if;
  update public.tasks set status=p_status where id=p_task_id;
  insert into public.task_activity(task_id,user_id,action) values(p_task_id,v_me,'status:'||p_status);
  update public.messages set metadata=jsonb_set(metadata,'{status}',to_jsonb(p_status),true) where type='task' and metadata->>'task_id'=p_task_id::text;
end; $$;
revoke all on function public.update_task_status(uuid,text) from public;
grant execute on function public.update_task_status(uuid,text) to authenticated;

grant select on public.profiles,public.conversations,public.conversation_members,public.messages,public.tasks,public.task_assignees,public.message_reactions,public.message_user_state,public.message_receipts,public.task_activity to authenticated;
grant update(username,display_name,avatar_url,bio,about,phone,avatar_initials,avatar_color_hex,presence,last_seen_at) on public.profiles to authenticated;
grant insert,update,delete on public.message_reactions to authenticated;
grant insert,update on public.message_user_state to authenticated;
grant insert,update on public.message_receipts to authenticated;

DO $$ BEGIN
  begin alter publication supabase_realtime add table public.conversation_members; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.message_reactions; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.message_receipts; exception when duplicate_object then null; end;
END $$;
