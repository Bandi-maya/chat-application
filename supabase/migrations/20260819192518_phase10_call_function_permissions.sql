revoke all on function public.accept_call_session(uuid,text) from public, anon;
revoke all on function public.decline_call_session(uuid) from public, anon;
revoke all on function public.end_call_session(uuid) from public, anon;
revoke all on function public.guard_call_session_update() from public, anon, authenticated;
grant execute on function public.accept_call_session(uuid,text) to authenticated;
grant execute on function public.decline_call_session(uuid) to authenticated;
grant execute on function public.end_call_session(uuid) to authenticated;
