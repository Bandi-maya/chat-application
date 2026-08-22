ALTER TABLE public.e2ee_device_bundles
  ADD COLUMN IF NOT EXISTS protocol_suite text;

ALTER TABLE public.e2ee_device_bundles
  DROP CONSTRAINT IF EXISTS e2ee_device_bundles_protocol_suite_check;

ALTER TABLE public.e2ee_device_bundles
  ADD CONSTRAINT e2ee_device_bundles_protocol_suite_check
  CHECK (protocol_suite IS NULL OR protocol_suite ~ '^[a-z0-9][a-z0-9._-]{0,63}$');

-- The tables are unused at migration time; require every future bundle to
-- declare the exact cryptographic suite that produced its key material.
ALTER TABLE public.e2ee_device_bundles
  ALTER COLUMN protocol_suite SET NOT NULL;

CREATE OR REPLACE FUNCTION private.enforce_encrypted_message_protocol_suite()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  IF NEW.encryption_version > 0 THEN
    IF NEW.encryption_protocol IS NULL OR NEW.sender_device_id IS NULL THEN
      RAISE EXCEPTION 'encrypted messages require protocol and sender device';
    END IF;

    IF NOT EXISTS (
      SELECT 1
      FROM public.e2ee_device_bundles b
      JOIN public.linked_devices ld
        ON ld.user_id = b.user_id
       AND ld.device_id = b.device_id
      WHERE b.user_id = NEW.sender_id
        AND b.device_id = NEW.sender_device_id
        AND b.protocol_suite = NEW.encryption_protocol
        AND b.revoked_at IS NULL
        AND ld.revoked_at IS NULL
    ) THEN
      RAISE EXCEPTION 'encrypted message protocol does not match active sender device suite';
    END IF;
  END IF;
  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION private.enforce_encrypted_message_protocol_suite() FROM PUBLIC;
REVOKE ALL ON FUNCTION private.enforce_encrypted_message_protocol_suite() FROM anon;
REVOKE ALL ON FUNCTION private.enforce_encrypted_message_protocol_suite() FROM authenticated;

DROP TRIGGER IF EXISTS enforce_encrypted_message_protocol_suite ON public.messages;
CREATE TRIGGER enforce_encrypted_message_protocol_suite
BEFORE INSERT OR UPDATE OF sender_id, sender_device_id, encryption_version, encryption_protocol
ON public.messages
FOR EACH ROW
EXECUTE FUNCTION private.enforce_encrypted_message_protocol_suite();

CREATE OR REPLACE FUNCTION public.get_e2ee_prekey_bundle(p_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_device record;
  v_prekey_id integer;
  v_prekey_public text;
  v_devices jsonb := '[]'::jsonb;
  v_recent_claims integer;
BEGIN
  IF v_me IS NULL THEN
    RAISE EXCEPTION 'authentication required';
  END IF;
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
      ON ld.user_id = b.user_id
     AND ld.device_id = b.device_id
    WHERE b.user_id = p_user_id
      AND b.revoked_at IS NULL
      AND ld.revoked_at IS NULL
    ORDER BY b.created_at
  LOOP
    v_prekey_id := NULL;
    v_prekey_public := NULL;

    IF v_me <> p_user_id THEN
      SELECT p.prekey_id, p.public_key
      INTO v_prekey_id, v_prekey_public
      FROM public.e2ee_one_time_prekeys p
      WHERE p.user_id = p_user_id
        AND p.device_id = v_device.device_id
        AND p.claimed_at IS NULL
      ORDER BY p.prekey_id
      FOR UPDATE SKIP LOCKED
      LIMIT 1;

      IF v_prekey_id IS NOT NULL THEN
        UPDATE public.e2ee_one_time_prekeys
        SET claimed_at = now(), claimed_by = v_me
        WHERE user_id = p_user_id
          AND device_id = v_device.device_id
          AND prekey_id = v_prekey_id
          AND claimed_at IS NULL;
      END IF;
    END IF;

    v_devices := v_devices || jsonb_build_array(jsonb_build_object(
      'user_id', v_device.user_id,
      'device_id', v_device.device_id,
      'protocol_suite', v_device.protocol_suite,
      'registration_id', v_device.registration_id,
      'identity_public_key', v_device.identity_public_key,
      'signed_prekey_id', v_device.signed_prekey_id,
      'signed_prekey_public', v_device.signed_prekey_public,
      'signed_prekey_signature', v_device.signed_prekey_signature,
      'pq_prekey_id', v_device.pq_prekey_id,
      'pq_prekey_public', v_device.pq_prekey_public,
      'pq_prekey_signature', v_device.pq_prekey_signature,
      'one_time_prekey_id', v_prekey_id,
      'one_time_prekey_public', v_prekey_public
    ));
  END LOOP;

  RETURN jsonb_build_object('user_id', p_user_id, 'devices', v_devices);
END;
$function$;

REVOKE ALL ON FUNCTION public.get_e2ee_prekey_bundle(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_e2ee_prekey_bundle(uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.get_e2ee_prekey_bundle(uuid) TO authenticated;