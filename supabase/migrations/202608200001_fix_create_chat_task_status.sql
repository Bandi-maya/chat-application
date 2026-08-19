-- `tasks.status` only permits inbox/assigned/in_progress/blocked/completed/archived.
-- The previous RPC inserted `todo`, causing Postgres error 23514.
create or replace function public.create_chat_task(
  p_conversation_id uuid,
  p_client_task_id uuid,
  p_title text,
  p_assignee_ids uuid[],
  p_priority text default 'normal'::text,
  p_due_at timestamptz default null,
  p_description text default ''::text,
  p_labels text[] default '{}'::text[],
  p_source_message_id uuid default null
)
returns uuid
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_me uuid := auth.uid();
  v_task_id uuid;
  v_assignee uuid;
  v_assignees uuid[];
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if char_length(trim(p_title)) not between 1 and 140 then raise exception 'task title must be 1 to 140 characters'; end if;
  if p_priority not in ('low','normal','high','urgent') then raise exception 'invalid priority'; end if;

  v_assignees := case
    when p_assignee_ids is null or cardinality(p_assignee_ids) = 0 then array[v_me]
    else p_assignee_ids
  end;

  insert into public.tasks(
    conversation_id, creator_id, client_task_id, title, status, priority,
    due_at, description, labels, source_message_id
  )
  values(
    p_conversation_id, v_me, p_client_task_id, trim(p_title), 'assigned', p_priority,
    p_due_at, coalesce(p_description,''), coalesce(p_labels,'{}'::text[]), p_source_message_id
  )
  on conflict (creator_id, client_task_id) do nothing
  returning id into v_task_id;

  if v_task_id is null then
    select id into v_task_id from public.tasks
    where creator_id = v_me and client_task_id = p_client_task_id;
    return v_task_id;
  end if;

  foreach v_assignee in array v_assignees loop
    if not exists(
      select 1 from public.conversation_members
      where conversation_id = p_conversation_id and user_id = v_assignee
    ) then
      raise exception 'assignee is not a member of this conversation';
    end if;
    insert into public.task_assignees(task_id,user_id)
    values(v_task_id,v_assignee)
    on conflict do nothing;
  end loop;

  insert into public.task_activity(task_id,user_id,action)
  values(v_task_id,v_me,'created');

  perform public.send_message(
    p_conversation_id,
    gen_random_uuid(),
    trim(p_title),
    'task',
    jsonb_build_object(
      'task_id',v_task_id,
      'title',trim(p_title),
      'priority',p_priority,
      'status','assigned',
      'due_at',p_due_at,
      'assignee_ids',v_assignees,
      'description',coalesce(p_description,''),
      'labels',coalesce(p_labels,'{}'::text[])
    )
  );
  return v_task_id;
end;
$function$;
