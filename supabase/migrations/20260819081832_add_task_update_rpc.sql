create or replace function public.update_chat_task(
  p_task_id uuid,
  p_title text,
  p_description text,
  p_assignee_ids uuid[],
  p_priority text,
  p_due_at timestamptz,
  p_labels text[] default '{}'
)
returns void
language plpgsql
security definer
set search_path=public
as $$
declare
  v_me uuid:=auth.uid();
  v_conversation uuid;
  v_creator uuid;
  v_assignee uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  select conversation_id,creator_id into v_conversation,v_creator from public.tasks where id=p_task_id;
  if v_conversation is null then raise exception 'task not found'; end if;
  if v_creator<>v_me then raise exception 'only the task creator can edit task details'; end if;
  if char_length(trim(p_title)) not between 1 and 140 then raise exception 'task title must be 1 to 140 characters'; end if;
  if p_priority not in('low','normal','high','urgent') then raise exception 'invalid priority'; end if;
  if coalesce(array_length(p_assignee_ids,1),0)=0 then raise exception 'at least one assignee is required'; end if;

  foreach v_assignee in array p_assignee_ids loop
    if not exists(select 1 from public.conversation_members where conversation_id=v_conversation and user_id=v_assignee) then
      raise exception 'assignee is not a member of this conversation';
    end if;
  end loop;

  update public.tasks
  set title=trim(p_title), description=coalesce(p_description,''), priority=p_priority,
      due_at=p_due_at, labels=coalesce(p_labels,'{}'::text[]), updated_at=now()
  where id=p_task_id;

  delete from public.task_assignees where task_id=p_task_id;
  insert into public.task_assignees(task_id,user_id)
  select p_task_id,unnest(p_assignee_ids)
  on conflict do nothing;

  insert into public.task_activity(task_id,user_id,action) values(p_task_id,v_me,'details_updated');

  update public.messages
  set body=trim(p_title),
      metadata=jsonb_set(
        jsonb_set(
          jsonb_set(
            jsonb_set(
              jsonb_set(metadata,'{title}',to_jsonb(trim(p_title)),true),
              '{description}',to_jsonb(coalesce(p_description,'')),true
            ),
            '{priority}',to_jsonb(p_priority),true
          ),
          '{due_at}',to_jsonb(p_due_at),true
        ),
        '{assignee_ids}',to_jsonb(p_assignee_ids),true
      )
  where type='task' and metadata->>'task_id'=p_task_id::text;
end;
$$;
revoke all on function public.update_chat_task(uuid,text,text,uuid[],text,timestamptz,text[]) from public;
grant execute on function public.update_chat_task(uuid,text,text,uuid[],text,timestamptz,text[]) to authenticated;
