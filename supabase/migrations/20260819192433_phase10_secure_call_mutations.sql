revoke update on public.call_sessions from authenticated;

drop function if exists public.accept_call_session(uuid,text);
create function public.accept_call_session(p_call_id uuid, p_answer_sdp text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.call_sessions
  set status = 'accepted',
      answer_sdp = p_answer_sdp,
      connected_at = coalesce(connected_at, now()),
      updated_at = now()
  where id = p_call_id
    and callee_id = auth.uid()
    and status = 'ringing';
  if not found then raise exception 'Call is not available to accept'; end if;
end;
$$;

drop function if exists public.decline_call_session(uuid);
create function public.decline_call_session(p_call_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.call_sessions
  set status = 'declined', ended_at = now(), ended_by = auth.uid(), updated_at = now()
  where id = p_call_id and callee_id = auth.uid() and status = 'ringing';
  if not found then raise exception 'Call is not available to decline'; end if;
end;
$$;

drop function if exists public.end_call_session(uuid);
create function public.end_call_session(p_call_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  update public.call_sessions
  set status = 'ended', ended_at = coalesce(ended_at, now()), ended_by = auth.uid(), updated_at = now()
  where id = p_call_id
    and (caller_id = auth.uid() or callee_id = auth.uid())
    and status in ('ringing','accepted');
  if not found then raise exception 'Call is not active'; end if;
end;
$$;

grant execute on function public.accept_call_session(uuid,text) to authenticated;
grant execute on function public.decline_call_session(uuid) to authenticated;
grant execute on function public.end_call_session(uuid) to authenticated;
