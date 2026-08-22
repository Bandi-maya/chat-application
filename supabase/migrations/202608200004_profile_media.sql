-- Profile media: avatar photo + profile banner.
-- Idempotent, mirrors the production schema so restored/self-hosted Chaty
-- databases converge. Profiles.avatar_url already exists in production;
-- banner_url is added alongside it. Media objects live in the public
-- 'profile-media' storage bucket under owner-prefixed paths.

alter table public.profiles
  add column if not exists banner_url text;

insert into storage.buckets (id, name, public)
values ('profile-media', 'profile-media', true)
on conflict (id) do nothing;

drop policy if exists profile_media_owner_write on storage.objects;
create policy profile_media_owner_write on storage.objects for insert to authenticated
with check (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists profile_media_owner_update on storage.objects;
create policy profile_media_owner_update on storage.objects for update to authenticated
using (
  bucket_id = 'profile-media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists profile_media_public_read on storage.objects;
create policy profile_media_public_read on storage.objects for select to public
using (bucket_id = 'profile-media');
