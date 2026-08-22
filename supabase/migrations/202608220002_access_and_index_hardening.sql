revoke all on function public.resolve_login_email(text, text) from public, anon, authenticated;

drop policy if exists status_updates_insert_own on public.status_updates;
create policy status_updates_insert_own on public.status_updates for insert to authenticated
with check (user_id = (select auth.uid()) and expires_at <= now() + interval '25 hours');

drop policy if exists status_updates_update_own on public.status_updates;
create policy status_updates_update_own on public.status_updates for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists status_updates_delete_own on public.status_updates;
create policy status_updates_delete_own on public.status_updates for delete to authenticated
using (user_id = (select auth.uid()));

drop policy if exists "members create e2ee key versions" on public.conversation_key_versions;
create policy "members create e2ee key versions" on public.conversation_key_versions for insert to authenticated
with check (created_by = (select auth.uid()) and public.is_conversation_member(conversation_id));

drop policy if exists "users create own e2ee keys" on public.user_e2ee_keys;
create policy "users create own e2ee keys" on public.user_e2ee_keys for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists "users revoke own e2ee keys" on public.user_e2ee_keys;
create policy "users revoke own e2ee keys" on public.user_e2ee_keys for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists auto_reply_rules_self on public.auto_reply_rules;
create policy auto_reply_rules_self on public.auto_reply_rules for all to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

create index if not exists call_ice_candidates_sender_id_idx on public.call_ice_candidates(sender_id);
create index if not exists call_sessions_conversation_id_idx on public.call_sessions(conversation_id);
create index if not exists call_sessions_ended_by_idx on public.call_sessions(ended_by);
create index if not exists conversation_key_envelopes_recipient_key_id_idx on public.conversation_key_envelopes(recipient_key_id);
create index if not exists conversation_key_versions_created_by_idx on public.conversation_key_versions(created_by);
create index if not exists conversations_created_by_idx on public.conversations(created_by);
create index if not exists message_reactions_user_id_idx on public.message_reactions(user_id);
create index if not exists message_receipts_user_id_idx on public.message_receipts(user_id);
create index if not exists message_user_state_user_id_idx on public.message_user_state(user_id);
create index if not exists poll_votes_option_id_idx on public.poll_votes(option_id);
create index if not exists polls_conversation_id_idx on public.polls(conversation_id);
create index if not exists polls_creator_id_idx on public.polls(creator_id);
create index if not exists reports_conversation_id_idx on public.reports(conversation_id);
create index if not exists reports_message_id_idx on public.reports(message_id);
create index if not exists reports_reporter_id_idx on public.reports(reporter_id);
create index if not exists status_views_viewer_id_idx on public.status_views(viewer_id);
create index if not exists task_activity_user_id_idx on public.task_activity(user_id);
create index if not exists tasks_source_message_id_idx on public.tasks(source_message_id);

drop index if exists public.conversation_members_user_idx;
drop index if exists public.status_updates_user_idx;
