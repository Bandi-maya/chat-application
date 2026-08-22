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
      join public.conversation_members owner on owner.conversation_id = me.conversation_id
      where me.user_id = (select auth.uid())
        and owner.user_id = status_updates.user_id
    )
  )
);

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
drop policy if exists "recipient reads e2ee envelopes" on public.conversation_key_envelopes;
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

drop policy if exists auto_reply_log_owner_access on public.auto_reply_log;
create policy auto_reply_log_owner_access
on public.auto_reply_log
for all
to authenticated
using (
  exists (
    select 1 from public.auto_reply_rules r
    where r.id = auto_reply_log.rule_id
      and r.user_id = (select auth.uid())
  )
  and public.is_conversation_member(auto_reply_log.conversation_id)
)
with check (
  exists (
    select 1 from public.auto_reply_rules r
    where r.id = auto_reply_log.rule_id
      and r.user_id = (select auth.uid())
  )
  and public.is_conversation_member(auto_reply_log.conversation_id)
);
