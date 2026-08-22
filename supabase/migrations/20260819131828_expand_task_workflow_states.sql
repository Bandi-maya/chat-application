alter table public.tasks drop constraint if exists tasks_status_check;

update public.tasks set status = case
  when status = 'todo' then 'inbox'
  when status = 'cancelled' then 'archived'
  else status
end;

alter table public.tasks alter column status set default 'inbox';

alter table public.tasks add constraint tasks_status_check
check (status in ('inbox','assigned','in_progress','blocked','completed','archived'));

create or replace function public.update_task_status(p_task_id uuid, p_status text)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_me uuid := auth.uid();
  v_conversation uuid;
  v_creator uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_status not in ('inbox','assigned','in_progress','blocked','completed','archived') then
    raise exception 'invalid task status';
  end if;
  select conversation_id, creator_id
    into v_conversation, v_creator
  from public.tasks
  where id = p_task_id;
  if v_conversation is null then raise exception 'task not found'; end if;
  if v_creator <> v_me and not exists(
    select 1 from public.task_assignees
    where task_id = p_task_id and user_id = v_me
  ) then
    raise exception 'not authorized';
  end if;
  update public.tasks
    set status = p_status, updated_at = now()
  where id = p_task_id;
  insert into public.task_activity(task_id,user_id,action)
    values(p_task_id,v_me,'status:' || p_status);
  update public.messages
    set metadata = jsonb_set(metadata,'{status}',to_jsonb(p_status),true)
  where type='task' and metadata->>'task_id'=p_task_id::text;
end;
$function$;

revoke all on function public.update_task_status(uuid,text) from public, anon;
grant execute on function public.update_task_status(uuid,text) to authenticated;

create or replace function public.get_my_tasks()
returns table(
  task_id uuid,
  conversation_id uuid,
  title text,
  description text,
  status text,
  priority text,
  due_at timestamptz,
  created_at timestamptz,
  updated_at timestamptz,
  creator_id uuid,
  assignee_ids uuid[],
  labels text[],
  source_message_id uuid
)
language sql
stable
security definer
set search_path to 'public'
as $function$
  select
    t.id,
    t.conversation_id,
    t.title,
    t.description,
    t.status,
    t.priority,
    t.due_at,
    t.created_at,
    t.updated_at,
    t.creator_id,
    coalesce(array_agg(ta.user_id) filter(where ta.user_id is not null),'{}'::uuid[]),
    t.labels,
    t.source_message_id
  from public.tasks t
  left join public.task_assignees ta on ta.task_id=t.id
  where t.creator_id=auth.uid()
     or exists(select 1 from public.task_assignees x where x.task_id=t.id and x.user_id=auth.uid())
  group by t.id
  order by
    case t.status
      when 'inbox' then 0
      when 'assigned' then 1
      when 'in_progress' then 2
      when 'blocked' then 3
      when 'completed' then 4
      else 5
    end,
    t.due_at nulls last,
    t.created_at desc;
$function$;

revoke all on function public.get_my_tasks() from public, anon;
grant execute on function public.get_my_tasks() to authenticated;
