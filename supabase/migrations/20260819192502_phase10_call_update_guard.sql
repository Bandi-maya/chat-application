create or replace function public.guard_call_session_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.id <> old.id or new.conversation_id <> old.conversation_id or new.caller_id <> old.caller_id or new.callee_id <> old.callee_id or new.kind <> old.kind or new.offer_sdp is distinct from old.offer_sdp then
    raise exception 'Immutable call session fields cannot be changed';
  end if;

  if auth.uid() is null or (auth.uid() <> old.caller_id and auth.uid() <> old.callee_id) then
    raise exception 'Not a call participant';
  end if;

  if new.status = 'accepted' then
    if auth.uid() <> old.callee_id or old.status <> 'ringing' then raise exception 'Only callee can accept ringing call'; end if;
    if new.answer_sdp is null or length(new.answer_sdp) = 0 then raise exception 'Answer SDP required'; end if;
  elsif new.status = 'declined' then
    if auth.uid() <> old.callee_id or old.status <> 'ringing' then raise exception 'Only callee can decline ringing call'; end if;
  elsif new.status = 'ended' then
    if old.status not in ('ringing','accepted') then raise exception 'Only active calls can be ended'; end if;
  elsif new.status <> old.status then
    raise exception 'Invalid call status transition';
  end if;

  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists call_sessions_update_guard on public.call_sessions;
create trigger call_sessions_update_guard
before update on public.call_sessions
for each row execute function public.guard_call_session_update();

grant update on public.call_sessions to authenticated;
