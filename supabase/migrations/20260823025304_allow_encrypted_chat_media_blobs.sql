update storage.buckets
set allowed_mime_types = case
  when allowed_mime_types is null then array['application/octet-stream']::text[]
  when 'application/octet-stream' = any(allowed_mime_types) then allowed_mime_types
  else array_append(allowed_mime_types, 'application/octet-stream')
end
where id = 'chat-media';
