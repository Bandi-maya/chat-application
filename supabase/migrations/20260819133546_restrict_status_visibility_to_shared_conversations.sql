drop policy if exists "status_updates_select_authenticated" on public.status_updates;
create policy "status_updates_select_shared_conversation"
on public.status_updates for select
to authenticated
using (
  auth.uid() = user_id
  or exists (
    select 1
    from public.conversation_members me
    join public.conversation_members owner
      on owner.conversation_id = me.conversation_id
    where me.user_id = auth.uid()
      and owner.user_id = status_updates.user_id
  )
);

drop policy if exists "status_views_insert_self" on public.status_views;
create policy "status_views_insert_visible_status"
on public.status_views for insert
to authenticated
with check (
  auth.uid() = viewer_id
  and exists (
    select 1
    from public.status_updates s
    where s.id = status_views.status_id
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

drop policy if exists "status_media_select_authenticated" on storage.objects;
create policy "status_media_select_shared_conversation"
on storage.objects for select
to authenticated
using (
  bucket_id = 'status-media'
  and array_length(storage.foldername(name), 1) >= 1
  and (
    (storage.foldername(name))[1] = auth.uid()::text
    or exists (
      select 1
      from public.conversation_members me
      join public.conversation_members owner
        on owner.conversation_id = me.conversation_id
      where me.user_id = auth.uid()
        and owner.user_id::text = (storage.foldername(name))[1]
    )
  )
);
