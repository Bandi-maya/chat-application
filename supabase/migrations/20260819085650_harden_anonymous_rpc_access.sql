revoke execute on function public.create_poll(uuid, uuid, text, text[], boolean) from anon;
revoke execute on function public.get_poll(uuid) from anon;
revoke execute on function public.update_chat_task(uuid, text, text, uuid[], text, timestamptz, text[]) from anon;
revoke execute on function public.vote_poll(uuid, uuid) from anon;

grant execute on function public.create_poll(uuid, uuid, text, text[], boolean) to authenticated;
grant execute on function public.get_poll(uuid) to authenticated;
grant execute on function public.update_chat_task(uuid, text, text, uuid[], text, timestamptz, text[]) to authenticated;
grant execute on function public.vote_poll(uuid, uuid) to authenticated;
