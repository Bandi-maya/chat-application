alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;
alter table public.tasks enable row level security;
alter table public.task_assignees enable row level security;

drop policy if exists profiles_select_self on public.profiles;
create policy profiles_select_self on public.profiles for select to authenticated using (id = auth.uid());
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
drop policy if exists conversations_select_member on public.conversations;
create policy conversations_select_member on public.conversations for select to authenticated using (public.is_conversation_member(id));
drop policy if exists conversation_members_select_member on public.conversation_members;
create policy conversation_members_select_member on public.conversation_members for select to authenticated using (public.is_conversation_member(conversation_id));
drop policy if exists messages_select_member on public.messages;
create policy messages_select_member on public.messages for select to authenticated using (public.is_conversation_member(conversation_id));
drop policy if exists tasks_select_member on public.tasks;
create policy tasks_select_member on public.tasks for select to authenticated using (public.is_conversation_member(conversation_id));
drop policy if exists task_assignees_select_member on public.task_assignees;
create policy task_assignees_select_member on public.task_assignees for select to authenticated using (
  exists (select 1 from public.tasks t where t.id = task_id and public.is_conversation_member(t.conversation_id))
);

create or replace function public.search_profiles(p_query text)
returns table (id uuid, username text, display_name text, avatar_url text)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, p.display_name, p.avatar_url
  from public.profiles p
  where p.id <> auth.uid()
    and char_length(trim(p_query)) >= 2
    and (strpos(lower(p.username), lower(trim(p_query))) > 0 or strpos(lower(p.display_name), lower(trim(p_query))) > 0)
  order by case when lower(p.username) = lower(trim(p_query)) then 0 else 1 end, p.display_name
  limit 20;
$$;
revoke all on function public.search_profiles(text) from public;
grant execute on function public.search_profiles(text) to authenticated;

create or replace function public.create_direct_conversation(p_other_user_id uuid)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_conversation_id uuid; v_direct_key text;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id = v_me then raise exception 'cannot create a direct conversation with yourself'; end if;
  if not exists(select 1 from public.profiles where id = p_other_user_id) then raise exception 'user not found'; end if;
  v_direct_key := least(v_me::text, p_other_user_id::text) || ':' || greatest(v_me::text, p_other_user_id::text);
  insert into public.conversations(kind, direct_key, created_by)
  values ('direct', v_direct_key, v_me)
  on conflict (direct_key) do update set direct_key = excluded.direct_key
  returning id into v_conversation_id;
  insert into public.conversation_members(conversation_id, user_id, role)
  values (v_conversation_id, v_me, 'owner'), (v_conversation_id, p_other_user_id, 'member')
  on conflict (conversation_id, user_id) do nothing;
  return v_conversation_id;
end; $$;
revoke all on function public.create_direct_conversation(uuid) from public;
grant execute on function public.create_direct_conversation(uuid) to authenticated;

create or replace function public.get_conversation_members(p_conversation_id uuid)
returns table (id uuid, username text, display_name text, avatar_url text)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query
  select p.id, p.username, p.display_name, p.avatar_url
  from public.conversation_members cm join public.profiles p on p.id = cm.user_id
  where cm.conversation_id = p_conversation_id
  order by case when p.id = auth.uid() then 0 else 1 end, p.display_name;
end; $$;
revoke all on function public.get_conversation_members(uuid) from public;
grant execute on function public.get_conversation_members(uuid) to authenticated;

create or replace function public.send_message(p_conversation_id uuid, p_client_message_id uuid, p_body text, p_type text default 'text')
returns uuid language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_id uuid; v_body text := trim(coalesce(p_body, ''));
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if p_type <> 'text' then raise exception 'unsupported message type'; end if;
  if v_body = '' then raise exception 'message body is required'; end if;
  if char_length(v_body) > 10000 then raise exception 'message too long'; end if;
  insert into public.messages(conversation_id, sender_id, client_message_id, type, body)
  values (p_conversation_id, v_me, p_client_message_id, 'text', v_body)
  on conflict (sender_id, client_message_id) do update set client_message_id = excluded.client_message_id
  returning id into v_id;
  update public.conversations set updated_at = now() where id = p_conversation_id;
  return v_id;
end; $$;
revoke all on function public.send_message(uuid, uuid, text, text) from public;
grant execute on function public.send_message(uuid, uuid, text, text) to authenticated;

create or replace function public.create_chat_task(p_conversation_id uuid, p_client_task_id uuid, p_title text, p_assignee_id uuid, p_priority text default 'normal', p_due_at timestamptz default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_task_id uuid; v_title text := trim(coalesce(p_title, '')); v_assignee_name text;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if not exists (select 1 from public.conversation_members cm where cm.conversation_id = p_conversation_id and cm.user_id = p_assignee_id) then raise exception 'assignee is not a member of this conversation'; end if;
  if char_length(v_title) < 1 or char_length(v_title) > 140 then raise exception 'task title must be 1 to 140 characters'; end if;
  if p_priority not in ('low', 'normal', 'high', 'urgent') then raise exception 'invalid priority'; end if;
  insert into public.tasks(conversation_id, creator_id, client_task_id, title, priority, due_at)
  values (p_conversation_id, v_me, p_client_task_id, v_title, p_priority, p_due_at)
  on conflict (creator_id, client_task_id) do nothing returning id into v_task_id;
  if v_task_id is null then select t.id into v_task_id from public.tasks t where t.creator_id = v_me and t.client_task_id = p_client_task_id; return v_task_id; end if;
  insert into public.task_assignees(task_id, user_id) values (v_task_id, p_assignee_id);
  select p.display_name into v_assignee_name from public.profiles p where p.id = p_assignee_id;
  insert into public.messages(conversation_id, sender_id, client_message_id, type, body, metadata)
  values (p_conversation_id, v_me, gen_random_uuid(), 'task', v_title,
    jsonb_build_object('task_id', v_task_id, 'title', v_title, 'assignee_id', p_assignee_id, 'assignee_name', v_assignee_name, 'priority', p_priority, 'status', 'todo', 'due_at', p_due_at));
  update public.conversations set updated_at = now() where id = p_conversation_id;
  return v_task_id;
end; $$;
revoke all on function public.create_chat_task(uuid, uuid, text, uuid, text, timestamptz) from public;
grant execute on function public.create_chat_task(uuid, uuid, text, uuid, text, timestamptz) to authenticated;

create or replace function public.get_my_conversations()
returns table (conversation_id uuid, kind text, title text, avatar_url text, last_message text, last_message_at timestamptz)
language sql stable security definer set search_path = public as $$
  select c.id, c.kind,
    case when c.kind = 'direct' then coalesce(other_profile.display_name, other_profile.username, 'Conversation') else coalesce(c.title, 'Group') end,
    case when c.kind = 'direct' then other_profile.avatar_url else null end,
    last_message.body, last_message.created_at
  from public.conversations c
  join public.conversation_members mine on mine.conversation_id = c.id and mine.user_id = auth.uid()
  left join lateral (
    select p.username, p.display_name, p.avatar_url
    from public.conversation_members cm join public.profiles p on p.id = cm.user_id
    where cm.conversation_id = c.id and cm.user_id <> auth.uid() order by cm.joined_at limit 1
  ) other_profile on true
  left join lateral (
    select m.body, m.created_at from public.messages m where m.conversation_id = c.id and m.deleted_at is null order by m.created_at desc limit 1
  ) last_message on true
  order by coalesce(last_message.created_at, c.updated_at) desc;
$$;
revoke all on function public.get_my_conversations() from public;
grant execute on function public.get_my_conversations() to authenticated;

create or replace function public.get_my_tasks()
returns table (task_id uuid, conversation_id uuid, title text, status text, priority text, due_at timestamptz, created_at timestamptz, conversation_title text)
language sql stable security definer set search_path = public as $$
  select t.id, t.conversation_id, t.title, t.status, t.priority, t.due_at, t.created_at,
    case when c.kind = 'direct' then coalesce(other_profile.display_name, other_profile.username, 'Conversation') else coalesce(c.title, 'Group') end
  from public.tasks t
  join public.task_assignees ta on ta.task_id = t.id and ta.user_id = auth.uid()
  join public.conversations c on c.id = t.conversation_id
  left join lateral (
    select p.username, p.display_name from public.conversation_members cm join public.profiles p on p.id = cm.user_id
    where cm.conversation_id = c.id and cm.user_id <> auth.uid() order by cm.joined_at limit 1
  ) other_profile on true
  order by case when t.status = 'completed' then 1 else 0 end, t.due_at nulls last, t.created_at desc;
$$;
revoke all on function public.get_my_tasks() from public;
grant execute on function public.get_my_tasks() to authenticated;

create or replace function public.update_task_status(p_task_id uuid, p_status text)
returns void language plpgsql security definer set search_path = public as $$
declare v_me uuid := auth.uid(); v_conversation_id uuid; v_creator_id uuid;
begin
  if p_status not in ('todo', 'in_progress', 'completed', 'cancelled') then raise exception 'invalid task status'; end if;
  select t.conversation_id, t.creator_id into v_conversation_id, v_creator_id from public.tasks t where t.id = p_task_id;
  if v_conversation_id is null then raise exception 'task not found'; end if;
  if v_creator_id <> v_me and not exists (select 1 from public.task_assignees ta where ta.task_id = p_task_id and ta.user_id = v_me) then raise exception 'not authorized'; end if;
  update public.tasks set status = p_status where id = p_task_id;
  update public.messages set metadata = jsonb_set(metadata, '{status}', to_jsonb(p_status), true)
  where type = 'task' and metadata ->> 'task_id' = p_task_id::text;
end; $$;
revoke all on function public.update_task_status(uuid, text) from public;
grant execute on function public.update_task_status(uuid, text) to authenticated;

grant select on public.profiles to authenticated;
grant update (username, display_name, avatar_url, bio) on public.profiles to authenticated;
grant select on public.conversations to authenticated;
grant select on public.conversation_members to authenticated;
grant select on public.messages to authenticated;
grant select on public.tasks to authenticated;
grant select on public.task_assignees to authenticated;

do $$ begin
  begin alter publication supabase_realtime add table public.messages; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.tasks; exception when duplicate_object then null; end;
end $$;
