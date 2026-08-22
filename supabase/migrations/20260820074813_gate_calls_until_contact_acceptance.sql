drop policy if exists call_sessions_caller_insert on public.call_sessions;
create policy call_sessions_caller_insert
on public.call_sessions
for insert
to authenticated
with check (
  (select auth.uid()) = caller_id
  and caller_id <> callee_id
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = call_sessions.conversation_id
      and cm.user_id = (select auth.uid())
  )
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = call_sessions.conversation_id
      and cm.user_id = call_sessions.callee_id
  )
  and exists (
    select 1 from public.contact_connections cc
    where cc.conversation_id = call_sessions.conversation_id
      and cc.low_accepted = true
      and cc.high_accepted = true
      and ((cc.user_low_id = caller_id and cc.user_high_id = callee_id)
        or (cc.user_high_id = caller_id and cc.user_low_id = callee_id))
  )
);
