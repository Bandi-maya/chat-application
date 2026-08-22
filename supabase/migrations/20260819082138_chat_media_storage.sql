insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values (
  'chat-media',
  'chat-media',
  false,
  52428800,
  array[
    'image/jpeg','image/png','image/webp','image/gif',
    'video/mp4','video/quicktime','video/webm',
    'audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/wav','audio/x-m4a',
    'application/pdf','text/plain','application/zip',
    'application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  ]::text[]
)
on conflict (id) do update set
  public=false,
  file_size_limit=excluded.file_size_limit,
  allowed_mime_types=excluded.allowed_mime_types;

-- Object path format: <senderUserId>/<conversationId>/<uuid>_<safeName>
drop policy if exists chat_media_select_members on storage.objects;
create policy chat_media_select_members on storage.objects
for select to authenticated
using (
  bucket_id='chat-media'
  and array_length(storage.foldername(name),1)>=2
  and public.is_conversation_member((storage.foldername(name))[2]::uuid)
);

drop policy if exists chat_media_insert_sender_member on storage.objects;
create policy chat_media_insert_sender_member on storage.objects
for insert to authenticated
with check (
  bucket_id='chat-media'
  and array_length(storage.foldername(name),1)>=2
  and (storage.foldername(name))[1]=auth.uid()::text
  and public.is_conversation_member((storage.foldername(name))[2]::uuid)
);

drop policy if exists chat_media_delete_owner on storage.objects;
create policy chat_media_delete_owner on storage.objects
for delete to authenticated
using (
  bucket_id='chat-media'
  and array_length(storage.foldername(name),1)>=2
  and (storage.foldername(name))[1]=auth.uid()::text
  and public.is_conversation_member((storage.foldername(name))[2]::uuid)
);
