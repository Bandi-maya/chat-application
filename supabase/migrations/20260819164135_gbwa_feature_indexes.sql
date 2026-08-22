create index if not exists blocked_users_blocked_id_idx on public.blocked_users(blocked_id);
create index if not exists auto_reply_log_conversation_idx on public.auto_reply_log(conversation_id);
create index if not exists scheduled_messages_user_idx on public.scheduled_messages(user_id);
create index if not exists scheduled_messages_conversation_idx on public.scheduled_messages(conversation_id);
create index if not exists typing_states_user_idx on public.typing_states(user_id);
create index if not exists message_edit_history_message_idx on public.message_edit_history(message_id);
create index if not exists message_edit_history_editor_idx on public.message_edit_history(editor_id);
create index if not exists mass_message_collections_user_idx on public.mass_message_collections(user_id);
