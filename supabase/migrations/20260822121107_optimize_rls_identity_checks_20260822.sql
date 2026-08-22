-- Preserve authorization semantics while evaluating auth.uid() once per statement.

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated
using (id = (select auth.uid())) with check (id = (select auth.uid()));

drop policy if exists message_reactions_insert_self on public.message_reactions;
create policy message_reactions_insert_self on public.message_reactions for insert to authenticated
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.messages m
    where m.id = message_reactions.message_id
      and public.is_conversation_member(m.conversation_id)
  )
);

drop policy if exists message_reactions_delete_self on public.message_reactions;
create policy message_reactions_delete_self on public.message_reactions for delete to authenticated
using (user_id = (select auth.uid()));

drop policy if exists message_user_state_select_self on public.message_user_state;
create policy message_user_state_select_self on public.message_user_state for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists message_user_state_insert_self on public.message_user_state;
create policy message_user_state_insert_self on public.message_user_state for insert to authenticated
with check (
  user_id = (select auth.uid())
  and exists (
    select 1 from public.messages m
    where m.id = message_user_state.message_id
      and public.is_conversation_member(m.conversation_id)
  )
);

drop policy if exists message_user_state_update_self on public.message_user_state;
create policy message_user_state_update_self on public.message_user_state for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists message_receipts_write_self on public.message_receipts;
create policy message_receipts_write_self on public.message_receipts for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists message_receipts_update_self on public.message_receipts;
create policy message_receipts_update_self on public.message_receipts for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists reports_insert_self_member on public.reports;
create policy reports_insert_self_member on public.reports for insert to authenticated
with check (
  reporter_id = (select auth.uid())
  and public.is_conversation_member(conversation_id)
);

drop policy if exists status_views_select_related on public.status_views;
create policy status_views_select_related on public.status_views for select to authenticated
using (
  viewer_id = (select auth.uid())
  or exists (
    select 1 from public.status_updates s
    where s.id = status_views.status_id
      and s.user_id = (select auth.uid())
  )
);

drop policy if exists status_views_insert_visible_status on public.status_views;
create policy status_views_insert_visible_status on public.status_views for insert to authenticated
with check (
  viewer_id = (select auth.uid())
  and exists (
    select 1
    from public.status_updates s
    where s.id = status_views.status_id
      and s.expires_at > now()
      and (
        s.user_id = (select auth.uid())
        or exists (
          select 1
          from public.conversation_members me
          join public.conversation_members owner on owner.conversation_id = me.conversation_id
          where me.user_id = (select auth.uid())
            and owner.user_id = s.user_id
        )
      )
  )
);

drop policy if exists user_feature_settings_select_self on public.user_feature_settings;
create policy user_feature_settings_select_self on public.user_feature_settings for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists user_feature_settings_insert_self on public.user_feature_settings;
create policy user_feature_settings_insert_self on public.user_feature_settings for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists user_feature_settings_update_self on public.user_feature_settings;
create policy user_feature_settings_update_self on public.user_feature_settings for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists user_feature_settings_delete_self on public.user_feature_settings;
create policy user_feature_settings_delete_self on public.user_feature_settings for delete to authenticated
using (user_id = (select auth.uid()));

drop policy if exists blocked_users_manage_self on public.blocked_users;
create policy blocked_users_manage_self on public.blocked_users for all to authenticated
using (blocker_id = (select auth.uid())) with check (blocker_id = (select auth.uid()));

drop policy if exists typing_states_insert_self on public.typing_states;
create policy typing_states_insert_self on public.typing_states for insert to authenticated
with check (
  user_id = (select auth.uid())
  and public.is_conversation_member(conversation_id)
);

drop policy if exists typing_states_update_self on public.typing_states;
create policy typing_states_update_self on public.typing_states for update to authenticated
using (user_id = (select auth.uid()))
with check (
  user_id = (select auth.uid())
  and public.is_conversation_member(conversation_id)
);

drop policy if exists typing_states_delete_self on public.typing_states;
create policy typing_states_delete_self on public.typing_states for delete to authenticated
using (user_id = (select auth.uid()));

drop policy if exists scheduled_messages_self on public.scheduled_messages;
create policy scheduled_messages_self on public.scheduled_messages for all to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists quick_reply_templates_self on public.quick_reply_templates;
create policy quick_reply_templates_self on public.quick_reply_templates for all to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists mass_message_collections_self on public.mass_message_collections;
create policy mass_message_collections_self on public.mass_message_collections for all to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists call_sessions_participants_select on public.call_sessions;
create policy call_sessions_participants_select on public.call_sessions for select to authenticated
using ((select auth.uid()) = caller_id or (select auth.uid()) = callee_id);

drop policy if exists call_sessions_participants_update on public.call_sessions;
create policy call_sessions_participants_update on public.call_sessions for update to authenticated
using ((select auth.uid()) = caller_id or (select auth.uid()) = callee_id)
with check ((select auth.uid()) = caller_id or (select auth.uid()) = callee_id);

drop policy if exists call_ice_participants_select on public.call_ice_candidates;
create policy call_ice_participants_select on public.call_ice_candidates for select to authenticated
using (
  exists (
    select 1 from public.call_sessions cs
    where cs.id = call_ice_candidates.call_id
      and ((select auth.uid()) = cs.caller_id or (select auth.uid()) = cs.callee_id)
  )
);

drop policy if exists call_ice_participants_insert on public.call_ice_candidates;
create policy call_ice_participants_insert on public.call_ice_candidates for insert to authenticated
with check (
  sender_id = (select auth.uid())
  and exists (
    select 1 from public.call_sessions cs
    where cs.id = call_ice_candidates.call_id
      and ((select auth.uid()) = cs.caller_id or (select auth.uid()) = cs.callee_id)
  )
);
