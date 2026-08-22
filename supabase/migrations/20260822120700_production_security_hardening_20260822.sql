-- Chaty production security/performance hardening.
-- Fixes verified authorization defects without changing product data.

-- The current Flutter username login path uses the username-login Edge Function.
-- The legacy password-taking resolver is no longer a supported client API.
revoke all on function public.resolve_login_email(text, text) from public, anon, authenticated;

-- Status privacy: the broad authenticated SELECT policy made every unexpired
-- status visible and defeated the participant-scoped policy because RLS
-- permissive policies are ORed together.
drop policy if exists status_updates_select_authenticated on public.status_updates;
drop policy if exists status_updates_select_shared_conversation on public.status_updates;
create policy status_updates_select_shared_conversation
on public.status_updates
for select
to authenticated
using (
  expires_at > now()
  and (
    user_id = (select auth.uid())
    or exists (
      select 1
      from public.conversation_members me
      join public.conversation_members owner
        on owner.conversation_id = me.conversation_id
      where me.user_id = (select auth.uid())
        and owner.user_id = status_updates.user_id
    )
  )
);

drop policy if exists status_updates_insert_own on public.status_updates;
create policy status_updates_insert_own
on public.status_updates
for insert
to authenticated
with check (
  user_id = (select auth.uid())
  and expires_at <= now() + interval '25 hours'
);

drop policy if exists status_updates_update_own on public.status_updates;
create policy status_updates_update_own
on public.status_updates
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists status_updates_delete_own on public.status_updates;
create policy status_updates_delete_own
on public.status_updates
for delete
to authenticated
using (user_id = (select auth.uid()));

-- E2EE key envelope authorization. The previous INSERT policy contained the
-- tautologies cm.user_id = cm.user_id and k.user_id = k.user_id, so it did not
-- bind the envelope's intended recipient to a conversation member/key owner.
drop policy if exists "members create e2ee envelopes" on public.conversation_key_envelopes;
create policy "members create e2ee envelopes"
on public.conversation_key_envelopes
for insert
to authenticated
with check (
  exists (
    select 1
    from public.conversation_key_versions v
    where v.id = conversation_key_envelopes.key_version_id
      and public.is_conversation_member(v.conversation_id)
      and exists (
        select 1
        from public.conversation_members recipient_member
        where recipient_member.conversation_id = v.conversation_id
          and recipient_member.user_id = conversation_key_envelopes.user_id
      )
      and exists (
        select 1
        from public.user_e2ee_keys recipient_key
        where recipient_key.id = conversation_key_envelopes.recipient_key_id
          and recipient_key.user_id = conversation_key_envelopes.user_id
          and recipient_key.revoked_at is null
      )
  )
);

drop policy if exists "members read e2ee envelopes" on public.conversation_key_envelopes;
create policy "recipient reads e2ee envelopes"
on public.conversation_key_envelopes
for select
to authenticated
using (
  conversation_key_envelopes.user_id = (select auth.uid())
  and exists (
    select 1
    from public.user_e2ee_keys recipient_key
    where recipient_key.id = conversation_key_envelopes.recipient_key_id
      and recipient_key.user_id = (select auth.uid())
  )
);

-- Optimize identity checks without changing authorization semantics.
drop policy if exists "members create e2ee key versions" on public.conversation_key_versions;
create policy "members create e2ee key versions"
on public.conversation_key_versions
for insert
to authenticated
with check (
  created_by = (select auth.uid())
  and public.is_conversation_member(conversation_id)
);

drop policy if exists "users create own e2ee keys" on public.user_e2ee_keys;
create policy "users create own e2ee keys"
on public.user_e2ee_keys
for insert
to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists "users revoke own e2ee keys" on public.user_e2ee_keys;
create policy "users revoke own e2ee keys"
on public.user_e2ee_keys
for update
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- auto_reply_log is private operational state for a user's own automation rule
-- and only for conversations in which that user participates.
drop policy if exists auto_reply_log_owner_access on public.auto_reply_log;
create policy auto_reply_log_owner_access
on public.auto_reply_log
for all
to authenticated
using (
  exists (
    select 1
    from public.auto_reply_rules r
    where r.id = auto_reply_log.rule_id
      and r.user_id = (select auth.uid())
  )
  and public.is_conversation_member(auto_reply_log.conversation_id)
)
with check (
  exists (
    select 1
    from public.auto_reply_rules r
    where r.id = auto_reply_log.rule_id
      and r.user_id = (select auth.uid())
  )
  and public.is_conversation_member(auto_reply_log.conversation_id)
);

-- Bring the parent automation policy onto the same optimized identity form.
drop policy if exists auto_reply_rules_self on public.auto_reply_rules;
create policy auto_reply_rules_self
on public.auto_reply_rules
for all
to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

-- Missing FK/supporting indexes reported by the production advisor.
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

-- Verified exact duplicate indexes; retain the names already used by the
-- repository migration and remove only the redundant copies.
drop index if exists public.conversation_members_user_idx;
drop index if exists public.status_updates_user_idx;
