alter function public.touch_updated_at() set search_path = public;

revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.is_conversation_member(uuid) from public, anon;
revoke execute on function public.search_profiles(text) from public, anon;
revoke execute on function public.create_direct_conversation(uuid) from public, anon;
revoke execute on function public.get_conversation_members(uuid) from public, anon;
revoke execute on function public.send_message(uuid, uuid, text, text) from public, anon;
revoke execute on function public.create_chat_task(uuid, uuid, text, uuid, text, timestamptz) from public, anon;
revoke execute on function public.get_my_conversations() from public, anon;
revoke execute on function public.get_my_tasks() from public, anon;
revoke execute on function public.update_task_status(uuid, text) from public, anon;

grant execute on function public.is_conversation_member(uuid) to authenticated;
grant execute on function public.search_profiles(text) to authenticated;
grant execute on function public.create_direct_conversation(uuid) to authenticated;
grant execute on function public.get_conversation_members(uuid) to authenticated;
grant execute on function public.send_message(uuid, uuid, text, text) to authenticated;
grant execute on function public.create_chat_task(uuid, uuid, text, uuid, text, timestamptz) to authenticated;
grant execute on function public.get_my_conversations() to authenticated;
grant execute on function public.get_my_tasks() to authenticated;
grant execute on function public.update_task_status(uuid, text) to authenticated;
