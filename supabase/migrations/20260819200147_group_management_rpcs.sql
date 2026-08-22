create or replace function public.update_group_title(p_conversation_id uuid, p_title text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if char_length(trim(coalesce(p_title,''))) not between 1 and 100 then
    raise exception 'group title must be 1 to 100 characters';
  end if;
  if not exists (
    select 1 from public.conversations c
    join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='group' and cm.user_id=v_me and cm.role in ('owner','admin')
  ) then raise exception 'group admin permission required'; end if;
  update public.conversations set title=trim(p_title), updated_at=now() where id=p_conversation_id;
end;
$$;

create or replace function public.add_group_member(p_conversation_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_user_id is null then raise exception 'user is required'; end if;
  if not exists (
    select 1 from public.conversations c
    join public.conversation_members cm on cm.conversation_id=c.id
    where c.id=p_conversation_id and c.kind='group' and cm.user_id=v_me and cm.role in ('owner','admin')
  ) then raise exception 'group admin permission required'; end if;
  if not exists(select 1 from public.profiles where id=p_user_id) then raise exception 'user not found'; end if;
  insert into public.conversation_members(conversation_id,user_id,role)
  values(p_conversation_id,p_user_id,'member')
  on conflict (conversation_id,user_id) do nothing;
  update public.conversations set updated_at=now() where id=p_conversation_id;
end;
$$;

create or replace function public.leave_group_conversation(p_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_me uuid := auth.uid();
  v_role text;
  v_replacement uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  select cm.role into v_role
  from public.conversation_members cm
  join public.conversations c on c.id=cm.conversation_id and c.kind='group'
  where cm.conversation_id=p_conversation_id and cm.user_id=v_me;
  if v_role is null then raise exception 'not a group member'; end if;

  if v_role='owner' then
    select user_id into v_replacement
    from public.conversation_members
    where conversation_id=p_conversation_id and user_id<>v_me
    order by case when role='admin' then 0 else 1 end, joined_at
    limit 1;
    if v_replacement is not null then
      update public.conversation_members set role='owner'
      where conversation_id=p_conversation_id and user_id=v_replacement;
    end if;
  end if;

  delete from public.conversation_members
  where conversation_id=p_conversation_id and user_id=v_me;

  if not exists(select 1 from public.conversation_members where conversation_id=p_conversation_id) then
    delete from public.conversations where id=p_conversation_id;
  else
    update public.conversations set updated_at=now() where id=p_conversation_id;
  end if;
end;
$$;

revoke all on function public.update_group_title(uuid,text) from public, anon;
revoke all on function public.add_group_member(uuid,uuid) from public, anon;
revoke all on function public.leave_group_conversation(uuid) from public, anon;
grant execute on function public.update_group_title(uuid,text) to authenticated;
grant execute on function public.add_group_member(uuid,uuid) to authenticated;
grant execute on function public.leave_group_conversation(uuid) to authenticated;
