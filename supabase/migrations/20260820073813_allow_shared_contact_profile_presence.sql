drop policy if exists profiles_select_self on public.profiles;
drop policy if exists profiles_select_shared_or_self on public.profiles;
create policy profiles_select_shared_or_self
on public.profiles
for select
to authenticated
using (
  id = (select auth.uid())
  or exists (
    select 1
    from public.conversation_members cm
    where cm.user_id = profiles.id
      and public.is_conversation_member(cm.conversation_id)
  )
);

-- Ensure peer profile lookups and direct-conversation membership checks stay indexed.
create index if not exists conversation_members_user_conversation_idx
  on public.conversation_members(user_id, conversation_id);
