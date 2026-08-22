drop policy if exists "status_updates_select_shared_conversation" on public.status_updates;
create policy "status_updates_select_shared_conversation"
on public.status_updates for select
to authenticated
using (
  expires_at > now()
  and (
    auth.uid() = user_id
    or exists (
      select 1
      from public.conversation_members me
      join public.conversation_members owner
        on owner.conversation_id = me.conversation_id
      where me.user_id = auth.uid()
        and owner.user_id = status_updates.user_id
    )
  )
);

drop policy if exists "status_media_select_shared_conversation" on storage.objects;
create policy "status_media_select_active_shared_status"
on storage.objects for select
to authenticated
using (
  bucket_id = 'status-media'
  and exists (
    select 1
    from public.status_updates s
    where s.media_path = storage.objects.name
      and s.expires_at > now()
      and (
        s.user_id = auth.uid()
        or exists (
          select 1
          from public.conversation_members me
          join public.conversation_members owner
            on owner.conversation_id = me.conversation_id
          where me.user_id = auth.uid()
            and owner.user_id = s.user_id
        )
      )
  )
);
