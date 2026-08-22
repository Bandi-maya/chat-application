create or replace function public.create_poll(
  p_conversation_id uuid,
  p_client_message_id uuid,
  p_question text,
  p_options text[],
  p_allow_multiple boolean default false
)
returns uuid
language plpgsql
security definer
set search_path=public
as $$
declare
  v_me uuid:=auth.uid();
  v_message_id uuid;
  v_poll_id uuid;
  v_label text;
  v_position integer:=0;
  v_visible_body text;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if char_length(trim(p_question)) not between 1 and 300 then raise exception 'poll question must be 1 to 300 characters'; end if;
  if coalesce(array_length(p_options,1),0) < 2 or array_length(p_options,1) > 12 then raise exception 'poll requires 2 to 12 options'; end if;

  v_visible_body:='[POLL] '||trim(p_question);
  v_message_id:=public.send_message(
    p_conversation_id,
    p_client_message_id,
    v_visible_body,
    'poll',
    jsonb_build_object('poll_question',trim(p_question))
  );

  select id into v_poll_id from public.polls where message_id=v_message_id;
  if v_poll_id is not null then return v_message_id; end if;

  insert into public.polls(message_id,conversation_id,creator_id,question,allow_multiple)
  values(v_message_id,p_conversation_id,v_me,trim(p_question),coalesce(p_allow_multiple,false))
  returning id into v_poll_id;

  foreach v_label in array p_options loop
    v_label:=trim(v_label);
    if char_length(v_label) not between 1 and 160 then raise exception 'poll option must be 1 to 160 characters'; end if;
    insert into public.poll_options(poll_id,position,label) values(v_poll_id,v_position,v_label);
    v_position:=v_position+1;
  end loop;
  return v_message_id;
end;
$$;
revoke all on function public.create_poll(uuid,uuid,text,text[],boolean) from public;
grant execute on function public.create_poll(uuid,uuid,text,text[],boolean) to authenticated;

create or replace function public.get_poll(p_message_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path=public
as $$
declare v_poll public.polls%rowtype;
begin
  select * into v_poll from public.polls where message_id=p_message_id;
  if v_poll.id is null then raise exception 'poll not found'; end if;
  if not public.is_conversation_member(v_poll.conversation_id) then raise exception 'not authorized'; end if;
  return jsonb_build_object(
    'id',v_poll.id,
    'message_id',v_poll.message_id,
    'question',v_poll.question,
    'allow_multiple',v_poll.allow_multiple,
    'total_votes',(select count(*) from public.poll_votes pv where pv.poll_id=v_poll.id),
    'options',coalesce((
      select jsonb_agg(jsonb_build_object(
        'id',po.id,
        'label',po.label,
        'position',po.position,
        'votes',(select count(*) from public.poll_votes pv where pv.option_id=po.id),
        'voted_by_me',exists(select 1 from public.poll_votes pv where pv.option_id=po.id and pv.user_id=auth.uid())
      ) order by po.position)
      from public.poll_options po where po.poll_id=v_poll.id
    ),'[]'::jsonb)
  );
end;
$$;
revoke all on function public.get_poll(uuid) from public;
grant execute on function public.get_poll(uuid) to authenticated;
