-- Backward-compatible foundation for per-device Signal-style E2EE.
-- This does NOT claim protocol completion: clients must still implement vetted
-- session/prekey/ratchet cryptography before using send_encrypted_message_v1.

CREATE TABLE IF NOT EXISTS public.e2ee_device_bundles (
  user_id uuid NOT NULL,
  device_id text NOT NULL,
  registration_id integer NOT NULL CHECK (registration_id BETWEEN 1 AND 16380),
  identity_public_key text NOT NULL CHECK (octet_length(identity_public_key) BETWEEN 16 AND 4096),
  signed_prekey_id integer NOT NULL CHECK (signed_prekey_id BETWEEN 0 AND 16777215),
  signed_prekey_public text NOT NULL CHECK (octet_length(signed_prekey_public) BETWEEN 16 AND 4096),
  signed_prekey_signature text NOT NULL CHECK (octet_length(signed_prekey_signature) BETWEEN 16 AND 4096),
  pq_prekey_id integer,
  pq_prekey_public text,
  pq_prekey_signature text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  PRIMARY KEY (user_id, device_id),
  FOREIGN KEY (user_id, device_id)
    REFERENCES public.linked_devices(user_id, device_id)
    ON DELETE CASCADE,
  CHECK (
    (pq_prekey_id IS NULL AND pq_prekey_public IS NULL AND pq_prekey_signature IS NULL)
    OR
    (pq_prekey_id BETWEEN 0 AND 16777215
      AND octet_length(pq_prekey_public) BETWEEN 16 AND 16384
      AND octet_length(pq_prekey_signature) BETWEEN 16 AND 4096)
  )
);

CREATE TABLE IF NOT EXISTS public.e2ee_one_time_prekeys (
  user_id uuid NOT NULL,
  device_id text NOT NULL,
  prekey_id integer NOT NULL CHECK (prekey_id BETWEEN 0 AND 16777215),
  public_key text NOT NULL CHECK (octet_length(public_key) BETWEEN 16 AND 4096),
  created_at timestamptz NOT NULL DEFAULT now(),
  claimed_at timestamptz,
  claimed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  PRIMARY KEY (user_id, device_id, prekey_id),
  FOREIGN KEY (user_id, device_id)
    REFERENCES public.e2ee_device_bundles(user_id, device_id)
    ON DELETE CASCADE,
  CHECK ((claimed_at IS NULL AND claimed_by IS NULL) OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL))
);

ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS encryption_version smallint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS encryption_protocol text,
  ADD COLUMN IF NOT EXISTS sender_device_id text;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'messages_encryption_version_check'
      AND conrelid = 'public.messages'::regclass
  ) THEN
    ALTER TABLE public.messages
      ADD CONSTRAINT messages_encryption_version_check
      CHECK (encryption_version BETWEEN 0 AND 32767);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'messages_encryption_shape_check'
      AND conrelid = 'public.messages'::regclass
  ) THEN
    ALTER TABLE public.messages
      ADD CONSTRAINT messages_encryption_shape_check
      CHECK (
        (encryption_version = 0 AND encryption_protocol IS NULL AND sender_device_id IS NULL)
        OR
        (encryption_version > 0
          AND body = ''
          AND encryption_protocol IS NOT NULL
          AND octet_length(encryption_protocol) BETWEEN 1 AND 64
          AND sender_device_id IS NOT NULL
          AND octet_length(sender_device_id) BETWEEN 1 AND 128)
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'messages_sender_device_fkey'
      AND conrelid = 'public.messages'::regclass
  ) THEN
    ALTER TABLE public.messages
      ADD CONSTRAINT messages_sender_device_fkey
      FOREIGN KEY (sender_id, sender_device_id)
      REFERENCES public.linked_devices(user_id, device_id)
      ON DELETE RESTRICT;
  END IF;
END
$$;

CREATE TABLE IF NOT EXISTS public.message_device_ciphertexts (
  message_id uuid NOT NULL REFERENCES public.messages(id) ON DELETE CASCADE,
  recipient_user_id uuid NOT NULL,
  recipient_device_id text NOT NULL,
  protocol_type text NOT NULL CHECK (octet_length(protocol_type) BETWEEN 1 AND 64),
  ciphertext text NOT NULL CHECK (octet_length(ciphertext) BETWEEN 1 AND 262144),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (message_id, recipient_user_id, recipient_device_id),
  FOREIGN KEY (recipient_user_id, recipient_device_id)
    REFERENCES public.e2ee_device_bundles(user_id, device_id)
    ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS e2ee_one_time_prekeys_available_idx
  ON public.e2ee_one_time_prekeys(user_id, device_id, prekey_id)
  WHERE claimed_at IS NULL;
CREATE INDEX IF NOT EXISTS e2ee_one_time_prekeys_claim_rate_idx
  ON public.e2ee_one_time_prekeys(claimed_by, user_id, claimed_at)
  WHERE claimed_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS message_device_ciphertexts_recipient_idx
  ON public.message_device_ciphertexts(recipient_user_id, recipient_device_id, created_at DESC);

ALTER TABLE public.e2ee_device_bundles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.e2ee_one_time_prekeys ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.message_device_ciphertexts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS e2ee_device_bundles_read_peer ON public.e2ee_device_bundles;
CREATE POLICY e2ee_device_bundles_read_peer
ON public.e2ee_device_bundles
FOR SELECT TO authenticated
USING (
  revoked_at IS NULL
  AND (
    user_id = (SELECT auth.uid())
    OR EXISTS (
      SELECT 1
      FROM public.conversation_members me
      JOIN public.conversation_members peer
        ON peer.conversation_id = me.conversation_id
      WHERE me.user_id = (SELECT auth.uid())
        AND peer.user_id = e2ee_device_bundles.user_id
    )
  )
);

DROP POLICY IF EXISTS e2ee_device_bundles_insert_self ON public.e2ee_device_bundles;
CREATE POLICY e2ee_device_bundles_insert_self
ON public.e2ee_device_bundles
FOR INSERT TO authenticated
WITH CHECK (
  user_id = (SELECT auth.uid())
  AND revoked_at IS NULL
  AND EXISTS (
    SELECT 1 FROM public.linked_devices ld
    WHERE ld.user_id = (SELECT auth.uid())
      AND ld.device_id = e2ee_device_bundles.device_id
      AND ld.revoked_at IS NULL
  )
);

DROP POLICY IF EXISTS e2ee_device_bundles_update_self ON public.e2ee_device_bundles;
CREATE POLICY e2ee_device_bundles_update_self
ON public.e2ee_device_bundles
FOR UPDATE TO authenticated
USING (user_id = (SELECT auth.uid()))
WITH CHECK (
  user_id = (SELECT auth.uid())
  AND EXISTS (
    SELECT 1 FROM public.linked_devices ld
    WHERE ld.user_id = (SELECT auth.uid())
      AND ld.device_id = e2ee_device_bundles.device_id
  )
);

DROP POLICY IF EXISTS e2ee_prekeys_read_self ON public.e2ee_one_time_prekeys;
CREATE POLICY e2ee_prekeys_read_self
ON public.e2ee_one_time_prekeys
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS e2ee_prekeys_insert_self ON public.e2ee_one_time_prekeys;
CREATE POLICY e2ee_prekeys_insert_self
ON public.e2ee_one_time_prekeys
FOR INSERT TO authenticated
WITH CHECK (
  user_id = (SELECT auth.uid())
  AND claimed_at IS NULL
  AND claimed_by IS NULL
  AND EXISTS (
    SELECT 1 FROM public.e2ee_device_bundles b
    WHERE b.user_id = (SELECT auth.uid())
      AND b.device_id = e2ee_one_time_prekeys.device_id
      AND b.revoked_at IS NULL
  )
);

DROP POLICY IF EXISTS e2ee_prekeys_delete_self ON public.e2ee_one_time_prekeys;
CREATE POLICY e2ee_prekeys_delete_self
ON public.e2ee_one_time_prekeys
FOR DELETE TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS message_device_ciphertexts_read_related ON public.message_device_ciphertexts;
CREATE POLICY message_device_ciphertexts_read_related
ON public.message_device_ciphertexts
FOR SELECT TO authenticated
USING (
  recipient_user_id = (SELECT auth.uid())
  OR EXISTS (
    SELECT 1 FROM public.messages m
    WHERE m.id = message_device_ciphertexts.message_id
      AND m.sender_id = (SELECT auth.uid())
  )
);

CREATE OR REPLACE FUNCTION private.e2ee_peer_allowed(p_requester uuid, p_target uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
  SELECT
    p_requester IS NOT NULL
    AND p_target IS NOT NULL
    AND (
      p_requester = p_target
      OR (
        NOT public.is_blocked_pair(p_requester, p_target)
        AND EXISTS (
          SELECT 1
          FROM public.conversation_members me
          JOIN public.conversation_members peer
            ON peer.conversation_id = me.conversation_id
          WHERE me.user_id = p_requester
            AND peer.user_id = p_target
        )
      )
    );
$function$;

REVOKE ALL ON FUNCTION private.e2ee_peer_allowed(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION private.e2ee_peer_allowed(uuid, uuid) FROM anon;
REVOKE ALL ON FUNCTION private.e2ee_peer_allowed(uuid, uuid) FROM authenticated;

CREATE OR REPLACE FUNCTION public.get_e2ee_prekey_bundle(p_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_device record;
  v_prekey record;
  v_devices jsonb := '[]'::jsonb;
  v_recent_claims integer;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT private.e2ee_peer_allowed(v_me, p_user_id) THEN
    RAISE EXCEPTION 'not authorized to fetch this key bundle';
  END IF;

  IF v_me <> p_user_id THEN
    SELECT count(*) INTO v_recent_claims
    FROM public.e2ee_one_time_prekeys p
    WHERE p.user_id = p_user_id
      AND p.claimed_by = v_me
      AND p.claimed_at > now() - interval '1 hour';
    IF v_recent_claims >= 20 THEN
      RAISE EXCEPTION 'prekey claim rate exceeded';
    END IF;
  END IF;

  FOR v_device IN
    SELECT b.*
    FROM public.e2ee_device_bundles b
    JOIN public.linked_devices ld
      ON ld.user_id = b.user_id AND ld.device_id = b.device_id
    WHERE b.user_id = p_user_id
      AND b.revoked_at IS NULL
      AND ld.revoked_at IS NULL
    ORDER BY b.created_at
  LOOP
    v_prekey := NULL;
    IF v_me <> p_user_id THEN
      SELECT p.user_id,p.device_id,p.prekey_id,p.public_key
      INTO v_prekey
      FROM public.e2ee_one_time_prekeys p
      WHERE p.user_id = p_user_id
        AND p.device_id = v_device.device_id
        AND p.claimed_at IS NULL
      ORDER BY p.prekey_id
      FOR UPDATE SKIP LOCKED
      LIMIT 1;

      IF v_prekey.prekey_id IS NOT NULL THEN
        UPDATE public.e2ee_one_time_prekeys
        SET claimed_at = now(), claimed_by = v_me
        WHERE user_id = p_user_id
          AND device_id = v_device.device_id
          AND prekey_id = v_prekey.prekey_id
          AND claimed_at IS NULL;
      END IF;
    END IF;

    v_devices := v_devices || jsonb_build_array(jsonb_build_object(
      'user_id', v_device.user_id,
      'device_id', v_device.device_id,
      'registration_id', v_device.registration_id,
      'identity_public_key', v_device.identity_public_key,
      'signed_prekey_id', v_device.signed_prekey_id,
      'signed_prekey_public', v_device.signed_prekey_public,
      'signed_prekey_signature', v_device.signed_prekey_signature,
      'pq_prekey_id', v_device.pq_prekey_id,
      'pq_prekey_public', v_device.pq_prekey_public,
      'pq_prekey_signature', v_device.pq_prekey_signature,
      'one_time_prekey_id', v_prekey.prekey_id,
      'one_time_prekey_public', v_prekey.public_key
    ));
  END LOOP;

  RETURN jsonb_build_object('user_id', p_user_id, 'devices', v_devices);
END;
$function$;

REVOKE ALL ON FUNCTION public.get_e2ee_prekey_bundle(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_e2ee_prekey_bundle(uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.get_e2ee_prekey_bundle(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.send_encrypted_message_v1(
  p_conversation_id uuid,
  p_client_message_id uuid,
  p_sender_device_id text,
  p_protocol text,
  p_ciphertexts jsonb
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_message_id uuid;
  v_expected integer;
  v_supplied integer;
  v_item jsonb;
  v_recipient_user uuid;
  v_recipient_device text;
  v_ciphertext text;
  v_protocol_type text;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF p_protocol IS NULL OR octet_length(p_protocol) NOT BETWEEN 1 AND 64 THEN RAISE EXCEPTION 'invalid encryption protocol'; END IF;
  IF p_sender_device_id IS NULL OR octet_length(p_sender_device_id) NOT BETWEEN 1 AND 128 THEN RAISE EXCEPTION 'invalid sender device'; END IF;
  IF jsonb_typeof(p_ciphertexts) <> 'array' THEN RAISE EXCEPTION 'ciphertexts must be an array'; END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM public.e2ee_device_bundles b
    JOIN public.linked_devices ld
      ON ld.user_id=b.user_id AND ld.device_id=b.device_id
    WHERE b.user_id=v_me
      AND b.device_id=p_sender_device_id
      AND b.revoked_at IS NULL
      AND ld.revoked_at IS NULL
  ) THEN
    RAISE EXCEPTION 'sender device has no active E2EE bundle';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM public.conversation_members cm
    WHERE cm.conversation_id=p_conversation_id
      AND NOT EXISTS (
        SELECT 1
        FROM public.e2ee_device_bundles b
        JOIN public.linked_devices ld
          ON ld.user_id=b.user_id AND ld.device_id=b.device_id
        WHERE b.user_id=cm.user_id
          AND b.revoked_at IS NULL
          AND ld.revoked_at IS NULL
      )
  ) THEN
    RAISE EXCEPTION 'every conversation member must have an active E2EE device';
  END IF;

  SELECT count(*) INTO v_expected
  FROM public.conversation_members cm
  JOIN public.e2ee_device_bundles b ON b.user_id=cm.user_id AND b.revoked_at IS NULL
  JOIN public.linked_devices ld ON ld.user_id=b.user_id AND ld.device_id=b.device_id AND ld.revoked_at IS NULL
  WHERE cm.conversation_id=p_conversation_id
    AND NOT (b.user_id=v_me AND b.device_id=p_sender_device_id);

  v_supplied := jsonb_array_length(p_ciphertexts);
  IF v_expected < 1 OR v_supplied <> v_expected THEN
    RAISE EXCEPTION 'ciphertext coverage does not match active recipient devices';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM (
      SELECT (x->>'user_id')::uuid AS user_id, x->>'device_id' AS device_id, count(*) AS n
      FROM jsonb_array_elements(p_ciphertexts) x
      GROUP BY 1,2
      HAVING count(*) <> 1
    ) dupes
  ) THEN
    RAISE EXCEPTION 'duplicate recipient device ciphertext';
  END IF;

  FOR v_item IN SELECT * FROM jsonb_array_elements(p_ciphertexts)
  LOOP
    BEGIN
      v_recipient_user := (v_item->>'user_id')::uuid;
    EXCEPTION WHEN others THEN
      RAISE EXCEPTION 'invalid recipient user id';
    END;
    v_recipient_device := v_item->>'device_id';
    v_ciphertext := v_item->>'ciphertext';
    v_protocol_type := coalesce(nullif(v_item->>'protocol_type',''), p_protocol);

    IF v_recipient_device IS NULL OR octet_length(v_recipient_device) NOT BETWEEN 1 AND 128
       OR v_ciphertext IS NULL OR octet_length(v_ciphertext) NOT BETWEEN 1 AND 262144
       OR octet_length(v_protocol_type) NOT BETWEEN 1 AND 64 THEN
      RAISE EXCEPTION 'invalid recipient ciphertext payload';
    END IF;

    IF v_recipient_user=v_me AND v_recipient_device=p_sender_device_id THEN
      RAISE EXCEPTION 'sender device must not receive its own ciphertext';
    END IF;

    IF NOT EXISTS (
      SELECT 1
      FROM public.conversation_members cm
      JOIN public.e2ee_device_bundles b ON b.user_id=cm.user_id AND b.device_id=v_recipient_device
      JOIN public.linked_devices ld ON ld.user_id=b.user_id AND ld.device_id=b.device_id
      WHERE cm.conversation_id=p_conversation_id
        AND cm.user_id=v_recipient_user
        AND b.revoked_at IS NULL
        AND ld.revoked_at IS NULL
    ) THEN
      RAISE EXCEPTION 'ciphertext recipient is not an active conversation device';
    END IF;
  END LOOP;

  SELECT m.id INTO v_message_id
  FROM public.messages m
  WHERE m.sender_id=v_me AND m.client_message_id=p_client_message_id;
  IF v_message_id IS NOT NULL THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.messages m
      WHERE m.id=v_message_id AND m.encryption_version=1 AND m.sender_device_id=p_sender_device_id
    ) THEN
      RAISE EXCEPTION 'client message id already belongs to a different message shape';
    END IF;
    RETURN v_message_id;
  END IF;

  INSERT INTO public.messages(
    conversation_id,sender_id,client_message_id,type,body,metadata,
    encryption_version,encryption_protocol,sender_device_id
  ) VALUES (
    p_conversation_id,v_me,p_client_message_id,'text','',
    jsonb_build_object('encrypted',true,'encryption_version',1),
    1,p_protocol,p_sender_device_id
  ) RETURNING id INTO v_message_id;

  FOR v_item IN SELECT * FROM jsonb_array_elements(p_ciphertexts)
  LOOP
    v_recipient_user := (v_item->>'user_id')::uuid;
    v_recipient_device := v_item->>'device_id';
    v_ciphertext := v_item->>'ciphertext';
    v_protocol_type := coalesce(nullif(v_item->>'protocol_type',''), p_protocol);
    INSERT INTO public.message_device_ciphertexts(
      message_id,recipient_user_id,recipient_device_id,protocol_type,ciphertext
    ) VALUES (
      v_message_id,v_recipient_user,v_recipient_device,v_protocol_type,v_ciphertext
    );
  END LOOP;

  UPDATE public.conversations SET updated_at=now() WHERE id=p_conversation_id;
  UPDATE public.conversation_members
    SET unread_count=unread_count+1
    WHERE conversation_id=p_conversation_id AND user_id<>v_me;

  RETURN v_message_id;
END;
$function$;

REVOKE ALL ON FUNCTION public.send_encrypted_message_v1(uuid,uuid,text,text,jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.send_encrypted_message_v1(uuid,uuid,text,text,jsonb) FROM anon;
GRANT EXECUTE ON FUNCTION public.send_encrypted_message_v1(uuid,uuid,text,text,jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.revoke_e2ee_bundle_with_linked_device()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  IF old.revoked_at IS NULL AND new.revoked_at IS NOT NULL THEN
    UPDATE public.e2ee_device_bundles
      SET revoked_at=coalesce(revoked_at,new.revoked_at), updated_at=now()
      WHERE user_id=new.user_id AND device_id=new.device_id;
    DELETE FROM public.e2ee_one_time_prekeys
      WHERE user_id=new.user_id AND device_id=new.device_id AND claimed_at IS NULL;
  END IF;
  RETURN new;
END;
$function$;

REVOKE ALL ON FUNCTION public.revoke_e2ee_bundle_with_linked_device() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.revoke_e2ee_bundle_with_linked_device() FROM anon;
REVOKE ALL ON FUNCTION public.revoke_e2ee_bundle_with_linked_device() FROM authenticated;

DROP TRIGGER IF EXISTS linked_device_revoke_e2ee_bundle ON public.linked_devices;
CREATE TRIGGER linked_device_revoke_e2ee_bundle
AFTER UPDATE OF revoked_at ON public.linked_devices
FOR EACH ROW
EXECUTE FUNCTION public.revoke_e2ee_bundle_with_linked_device();
