create table if not exists public.status_updates (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  content text not null default '' check (char_length(content) <= 2000),
  media_type text not null default 'text' check (media_type in ('text','image','video','audio','document')),
  media_path text,
  mime_type text,
  background_gradient text not null default 'indigo_purple',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '24 hours')
);

create index if not exists status_updates_expires_at_idx on public.status_updates(expires_at desc);
create index if not exists status_updates_user_id_idx on public.status_updates(user_id, created_at desc);

alter table public.status_updates enable row level security;

create policy "status_updates_select_authenticated"
on public.status_updates for select
to authenticated
using (expires_at > now());

create policy "status_updates_insert_own"
on public.status_updates for insert
to authenticated
with check (auth.uid() = user_id and expires_at <= now() + interval '25 hours');

create policy "status_updates_update_own"
on public.status_updates for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "status_updates_delete_own"
on public.status_updates for delete
to authenticated
using (auth.uid() = user_id);

create table if not exists public.status_views (
  status_id uuid not null references public.status_updates(id) on delete cascade,
  viewer_id uuid not null references auth.users(id) on delete cascade,
  viewed_at timestamptz not null default now(),
  primary key (status_id, viewer_id)
);

alter table public.status_views enable row level security;

create policy "status_views_insert_self"
on public.status_views for insert
to authenticated
with check (auth.uid() = viewer_id);

create policy "status_views_select_related"
on public.status_views for select
to authenticated
using (
  auth.uid() = viewer_id
  or exists (
    select 1 from public.status_updates s
    where s.id = status_views.status_id and s.user_id = auth.uid()
  )
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'status-media',
  'status-media',
  false,
  52428800,
  array[
    'image/jpeg','image/png','image/webp','image/gif',
    'video/mp4','video/quicktime','video/webm',
    'audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/wav','audio/x-m4a',
    'application/pdf','text/plain','application/zip',
    'application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  ]::text[]
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create policy "status_media_insert_owner"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'status-media'
  and array_length(storage.foldername(name), 1) >= 1
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "status_media_select_authenticated"
on storage.objects for select
to authenticated
using (bucket_id = 'status-media');

create policy "status_media_delete_owner"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'status-media'
  and array_length(storage.foldername(name), 1) >= 1
  and (storage.foldername(name))[1] = auth.uid()::text
);

alter publication supabase_realtime add table public.status_updates;
alter publication supabase_realtime add table public.status_views;
