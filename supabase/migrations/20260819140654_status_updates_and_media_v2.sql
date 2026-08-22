create table if not exists public.status_updates (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  text_content text not null default '',
  media_path text,
  media_type text not null default 'text' check (media_type in ('text','image','video','audio','document')),
  media_name text,
  media_size bigint,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours')
);
create index if not exists status_updates_active_idx on public.status_updates (expires_at desc, created_at desc);
create index if not exists status_updates_user_idx on public.status_updates (user_id, created_at desc);
alter table public.status_updates enable row level security;
revoke all on public.status_updates from anon;
grant select, insert, delete on public.status_updates to authenticated;
drop policy if exists status_updates_select_authenticated on public.status_updates;
create policy status_updates_select_authenticated on public.status_updates for select to authenticated using (expires_at > now());
drop policy if exists status_updates_insert_own on public.status_updates;
create policy status_updates_insert_own on public.status_updates for insert to authenticated with check (user_id = auth.uid() and expires_at <= now() + interval '25 hours');
drop policy if exists status_updates_delete_own on public.status_updates;
create policy status_updates_delete_own on public.status_updates for delete to authenticated using (user_id = auth.uid());
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('status-media','status-media',false,52428800,array['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/quicktime','video/webm','audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/wav','audio/x-m4a','application/pdf','text/plain','application/zip','application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document','application/vnd.ms-excel','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet']::text[])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;
drop policy if exists status_media_select_authenticated on storage.objects;
create policy status_media_select_authenticated on storage.objects for select to authenticated using (bucket_id = 'status-media');
drop policy if exists status_media_insert_own on storage.objects;
create policy status_media_insert_own on storage.objects for insert to authenticated with check (bucket_id = 'status-media' and array_length(storage.foldername(name), 1) >= 1 and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists status_media_delete_own on storage.objects;
create policy status_media_delete_own on storage.objects for delete to authenticated using (bucket_id = 'status-media' and array_length(storage.foldername(name), 1) >= 1 and (storage.foldername(name))[1] = auth.uid()::text);
do $$ begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'status_updates'
  ) then
    alter publication supabase_realtime add table public.status_updates;
  end if;
end $$;
