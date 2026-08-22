DROP POLICY IF EXISTS status_media_select_authenticated ON storage.objects;
DROP POLICY IF EXISTS status_media_insert_own ON storage.objects;
DROP POLICY IF EXISTS status_media_delete_own ON storage.objects;

DROP POLICY IF EXISTS status_media_insert_owner ON storage.objects;
CREATE POLICY status_media_insert_owner
ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'status-media'
  AND array_length(storage.foldername(name), 1) >= 1
  AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
);

DROP POLICY IF EXISTS status_media_delete_owner ON storage.objects;
CREATE POLICY status_media_delete_owner
ON storage.objects
FOR DELETE TO authenticated
USING (
  bucket_id = 'status-media'
  AND array_length(storage.foldername(name), 1) >= 1
  AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
);

DROP POLICY IF EXISTS status_media_select_active_shared_status ON storage.objects;
CREATE POLICY status_media_select_active_shared_status
ON storage.objects
FOR SELECT TO authenticated
USING (
  bucket_id = 'status-media'
  AND EXISTS (
    SELECT 1
    FROM public.status_updates s
    WHERE s.media_path = objects.name
      AND s.expires_at > now()
      AND (
        s.user_id = (SELECT auth.uid())
        OR EXISTS (
          SELECT 1
          FROM public.conversation_members me
          JOIN public.conversation_members owner
            ON owner.conversation_id = me.conversation_id
          WHERE me.user_id = (SELECT auth.uid())
            AND owner.user_id = s.user_id
        )
      )
  )
);

DROP POLICY IF EXISTS chat_media_insert_sender_member ON storage.objects;
CREATE POLICY chat_media_insert_sender_member
ON storage.objects
FOR INSERT TO authenticated
WITH CHECK (
  bucket_id = 'chat-media'
  AND array_length(storage.foldername(name), 1) >= 2
  AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
  AND public.is_conversation_member(((storage.foldername(name))[2])::uuid)
);

DROP POLICY IF EXISTS chat_media_delete_owner ON storage.objects;
CREATE POLICY chat_media_delete_owner
ON storage.objects
FOR DELETE TO authenticated
USING (
  bucket_id = 'chat-media'
  AND array_length(storage.foldername(name), 1) >= 2
  AND (storage.foldername(name))[1] = (SELECT auth.uid())::text
  AND public.is_conversation_member(((storage.foldername(name))[2])::uuid)
);

DROP POLICY IF EXISTS chat_media_select_members ON storage.objects;
CREATE POLICY chat_media_select_members
ON storage.objects
FOR SELECT TO authenticated
USING (
  bucket_id = 'chat-media'
  AND array_length(storage.foldername(name), 1) >= 2
  AND public.is_conversation_member(((storage.foldername(name))[2])::uuid)
);
