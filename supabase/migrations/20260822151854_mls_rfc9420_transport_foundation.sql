-- Chaty RFC 9420 MLS transport foundation.
-- MLS protocol state and private keys remain client-side inside OpenMLS/SQLCipher.
-- The server stores only public device material, one-time KeyPackages, group-routing
-- metadata, Welcome/Commit transport payloads, and opaque application ciphertexts.

ALTER TABLE public.messages
  ADD COLUMN IF NOT EXISTS encrypted_payload text;

CREATE TABLE IF NOT EXISTS public.mls_devices (
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  device_id text NOT NULL,
  protocol_suite text NOT NULL DEFAULT 'mls-rfc9420-v1',
  ciphersuite text NOT NULL,
  credential_identity text NOT NULL,
  signature_public_key text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  revoked_at timestamptz,
  PRIMARY KEY (user_id, device_id),
  FOREIGN KEY (user_id, device_id)
    REFERENCES public.linked_devices(user_id, device_id)
    ON DELETE CASCADE,
  CONSTRAINT mls_devices_protocol_check
    CHECK (protocol_suite = 'mls-rfc9420-v1'),
  CONSTRAINT mls_devices_ciphersuite_check
    CHECK (ciphersuite = 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'),
  CONSTRAINT mls_devices_credential_length_check
    CHECK (octet_length(credential_identity) BETWEEN 3 AND 512),
  CONSTRAINT mls_devices_signature_key_length_check
    CHECK (octet_length(signature_public_key) BETWEEN 16 AND 8192)
);

CREATE TABLE IF NOT EXISTS public.mls_key_packages (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  device_id text NOT NULL,
  key_package text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '30 days'),
  claimed_at timestamptz,
  claimed_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  used_at timestamptz,
  FOREIGN KEY (user_id, device_id)
    REFERENCES public.mls_devices(user_id, device_id)
    ON DELETE CASCADE,
  CONSTRAINT mls_key_packages_payload_length_check
    CHECK (octet_length(key_package) BETWEEN 16 AND 131072),
  CONSTRAINT mls_key_packages_claim_shape_check
    CHECK ((claimed_at IS NULL AND claimed_by IS NULL) OR
           (claimed_at IS NOT NULL AND claimed_by IS NOT NULL)),
  CONSTRAINT mls_key_packages_used_shape_check
    CHECK (used_at IS NULL OR claimed_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS mls_key_packages_available_idx
  ON public.mls_key_packages(user_id, device_id, created_at)
  WHERE claimed_at IS NULL AND used_at IS NULL;
CREATE INDEX IF NOT EXISTS mls_key_packages_claimed_by_idx
  ON public.mls_key_packages(claimed_by, claimed_at)
  WHERE claimed_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.mls_conversation_groups (
  conversation_id uuid PRIMARY KEY REFERENCES public.conversations(id) ON DELETE CASCADE,
  group_id text NOT NULL UNIQUE,
  protocol_suite text NOT NULL DEFAULT 'mls-rfc9420-v1',
  ciphersuite text NOT NULL,
  epoch bigint NOT NULL DEFAULT 0 CHECK (epoch >= 0),
  created_by uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT mls_groups_protocol_check
    CHECK (protocol_suite = 'mls-rfc9420-v1'),
  CONSTRAINT mls_groups_ciphersuite_check
    CHECK (ciphersuite = 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'),
  CONSTRAINT mls_groups_group_id_length_check
    CHECK (octet_length(group_id) BETWEEN 4 AND 1024)
);

CREATE TABLE IF NOT EXISTS public.mls_group_devices (
  conversation_id uuid NOT NULL REFERENCES public.mls_conversation_groups(conversation_id) ON DELETE CASCADE,
  user_id uuid NOT NULL,
  device_id text NOT NULL,
  joined_epoch bigint NOT NULL CHECK (joined_epoch >= 0),
  removed_epoch bigint CHECK (removed_epoch IS NULL OR removed_epoch >= joined_epoch),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id, device_id),
  FOREIGN KEY (user_id, device_id)
    REFERENCES public.mls_devices(user_id, device_id)
    ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS mls_group_devices_active_idx
  ON public.mls_group_devices(conversation_id, user_id, device_id)
  WHERE removed_epoch IS NULL;

CREATE TABLE IF NOT EXISTS public.mls_welcomes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES public.mls_conversation_groups(conversation_id) ON DELETE CASCADE,
  recipient_user_id uuid NOT NULL,
  recipient_device_id text NOT NULL,
  sender_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  sender_device_id text NOT NULL,
  epoch bigint NOT NULL CHECK (epoch >= 0),
  welcome_payload text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  consumed_at timestamptz,
  FOREIGN KEY (recipient_user_id, recipient_device_id)
    REFERENCES public.mls_devices(user_id, device_id)
    ON DELETE CASCADE,
  FOREIGN KEY (sender_user_id, sender_device_id)
    REFERENCES public.mls_devices(user_id, device_id)
    ON DELETE RESTRICT,
  CONSTRAINT mls_welcomes_payload_length_check
    CHECK (octet_length(welcome_payload) BETWEEN 16 AND 1048576)
);
CREATE INDEX IF NOT EXISTS mls_welcomes_recipient_pending_idx
  ON public.mls_welcomes(recipient_user_id, recipient_device_id, created_at)
  WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS public.mls_control_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES public.mls_conversation_groups(conversation_id) ON DELETE CASCADE,
  sender_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
  sender_device_id text NOT NULL,
  epoch bigint NOT NULL CHECK (epoch > 0),
  commit_payload text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  FOREIGN KEY (sender_user_id, sender_device_id)
    REFERENCES public.mls_devices(user_id, device_id)
    ON DELETE RESTRICT,
  UNIQUE (conversation_id, epoch),
  CONSTRAINT mls_control_payload_length_check
    CHECK (octet_length(commit_payload) BETWEEN 16 AND 1048576)
);

ALTER TABLE public.mls_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mls_key_packages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mls_conversation_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mls_group_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mls_welcomes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mls_control_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS mls_devices_owner_read ON public.mls_devices;
CREATE POLICY mls_devices_owner_read ON public.mls_devices
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS mls_key_packages_owner_read ON public.mls_key_packages;
CREATE POLICY mls_key_packages_owner_read ON public.mls_key_packages
FOR SELECT TO authenticated
USING (user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS mls_groups_member_read ON public.mls_conversation_groups;
CREATE POLICY mls_groups_member_read ON public.mls_conversation_groups
FOR SELECT TO authenticated
USING (public.is_conversation_member(conversation_id));

DROP POLICY IF EXISTS mls_group_devices_member_read ON public.mls_group_devices;
CREATE POLICY mls_group_devices_member_read ON public.mls_group_devices
FOR SELECT TO authenticated
USING (public.is_conversation_member(conversation_id));

DROP POLICY IF EXISTS mls_welcomes_recipient_read ON public.mls_welcomes;
CREATE POLICY mls_welcomes_recipient_read ON public.mls_welcomes
FOR SELECT TO authenticated
USING (recipient_user_id = (SELECT auth.uid()));

DROP POLICY IF EXISTS mls_control_member_read ON public.mls_control_messages;
CREATE POLICY mls_control_member_read ON public.mls_control_messages
FOR SELECT TO authenticated
USING (public.is_conversation_member(conversation_id));

-- The Flutter client uses capability RPCs below rather than direct mutation of
-- MLS transport tables. Explicitly keep those tables out of the Data API for
-- anon/authenticated roles; RLS remains enabled as defense in depth.
REVOKE ALL ON TABLE public.mls_devices FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.mls_key_packages FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.mls_conversation_groups FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.mls_group_devices FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.mls_welcomes FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.mls_control_messages FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE public.mls_devices TO service_role;
GRANT ALL ON TABLE public.mls_key_packages TO service_role;
GRANT ALL ON TABLE public.mls_conversation_groups TO service_role;
GRANT ALL ON TABLE public.mls_group_devices TO service_role;
GRANT ALL ON TABLE public.mls_welcomes TO service_role;
GRANT ALL ON TABLE public.mls_control_messages TO service_role;

CREATE OR REPLACE FUNCTION public.register_mls_device_v1(
  p_device_id text,
  p_device_name text,
  p_platform text,
  p_ciphersuite text,
  p_credential_identity text,
  p_signature_public_key text,
  p_key_packages jsonb DEFAULT '[]'::jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_existing public.mls_devices%ROWTYPE;
  v_item jsonb;
  v_id uuid;
  v_payload text;
  v_expires timestamptz;
  v_available integer;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF p_device_id IS NULL OR octet_length(p_device_id) NOT BETWEEN 8 AND 128 THEN RAISE EXCEPTION 'invalid device id'; END IF;
  IF p_ciphersuite <> 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519' THEN RAISE EXCEPTION 'unsupported MLS ciphersuite'; END IF;
  IF p_credential_identity IS NULL OR octet_length(p_credential_identity) NOT BETWEEN 3 AND 512 THEN RAISE EXCEPTION 'invalid credential identity'; END IF;
  IF p_signature_public_key IS NULL OR octet_length(p_signature_public_key) NOT BETWEEN 16 AND 8192 THEN RAISE EXCEPTION 'invalid signature public key'; END IF;
  IF jsonb_typeof(p_key_packages) <> 'array' OR jsonb_array_length(p_key_packages) > 50 THEN RAISE EXCEPTION 'invalid key package batch'; END IF;

  INSERT INTO public.linked_devices(user_id,device_id,device_name,platform,location,last_active_at,revoked_at)
  VALUES(v_me,p_device_id,left(coalesce(nullif(trim(p_device_name),''),'Chaty MLS device'),120),left(coalesce(nullif(trim(p_platform),''),'unknown'),40),'',now(),NULL)
  ON CONFLICT(user_id,device_id) DO UPDATE SET
    device_name=excluded.device_name,
    platform=excluded.platform,
    last_active_at=now(),
    revoked_at=NULL;

  SELECT * INTO v_existing
  FROM public.mls_devices
  WHERE user_id=v_me AND device_id=p_device_id;

  IF FOUND AND (
    v_existing.ciphersuite <> p_ciphersuite OR
    v_existing.credential_identity <> p_credential_identity OR
    v_existing.signature_public_key <> p_signature_public_key
  ) THEN
    RAISE EXCEPTION 'MLS device identity mismatch; rotate to a new device id';
  END IF;

  INSERT INTO public.mls_devices(user_id,device_id,protocol_suite,ciphersuite,credential_identity,signature_public_key,revoked_at)
  VALUES(v_me,p_device_id,'mls-rfc9420-v1',p_ciphersuite,p_credential_identity,p_signature_public_key,NULL)
  ON CONFLICT(user_id,device_id) DO UPDATE SET updated_at=now(),revoked_at=NULL;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_key_packages)
  LOOP
    BEGIN
      v_id := (v_item->>'id')::uuid;
    EXCEPTION WHEN OTHERS THEN
      RAISE EXCEPTION 'invalid key package id';
    END;
    v_payload := v_item->>'key_package';
    IF v_payload IS NULL OR octet_length(v_payload) NOT BETWEEN 16 AND 131072 THEN
      RAISE EXCEPTION 'invalid MLS key package payload';
    END IF;
    v_expires := coalesce(nullif(v_item->>'expires_at','')::timestamptz, now()+interval '30 days');
    IF v_expires <= now()+interval '1 hour' OR v_expires > now()+interval '120 days' THEN
      RAISE EXCEPTION 'invalid MLS key package expiry';
    END IF;
    INSERT INTO public.mls_key_packages(id,user_id,device_id,key_package,expires_at)
    VALUES(v_id,v_me,p_device_id,v_payload,v_expires)
    ON CONFLICT(id) DO NOTHING;
  END LOOP;

  SELECT count(*) INTO v_available
  FROM public.mls_key_packages
  WHERE user_id=v_me AND device_id=p_device_id
    AND claimed_at IS NULL AND used_at IS NULL AND expires_at>now();

  RETURN jsonb_build_object(
    'user_id',v_me,
    'device_id',p_device_id,
    'protocol_suite','mls-rfc9420-v1',
    'available_key_packages',v_available
  );
END;
$function$;
REVOKE ALL ON FUNCTION public.register_mls_device_v1(text,text,text,text,text,text,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_mls_device_v1(text,text,text,text,text,text,jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.claim_mls_conversation_key_packages_v1(
  p_conversation_id uuid,
  p_sender_device_id text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_device record;
  v_pkg record;
  v_packages jsonb := '[]'::jsonb;
  v_recent_claims integer;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_devices md
    JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id
    WHERE md.user_id=v_me AND md.device_id=p_sender_device_id
      AND md.revoked_at IS NULL AND ld.revoked_at IS NULL
  ) THEN RAISE EXCEPTION 'sender MLS device is not active'; END IF;

  IF EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id=p_conversation_id
      AND NOT EXISTS (
        SELECT 1 FROM public.mls_devices md
        JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id
        WHERE md.user_id=cm.user_id AND md.revoked_at IS NULL AND ld.revoked_at IS NULL
      )
  ) THEN RAISE EXCEPTION 'every conversation member must register an MLS device'; END IF;

  SELECT count(*) INTO v_recent_claims
  FROM public.mls_key_packages kp
  WHERE kp.claimed_by=v_me AND kp.claimed_at>now()-interval '1 hour';
  IF v_recent_claims >= 200 THEN RAISE EXCEPTION 'MLS key package claim rate exceeded'; END IF;

  FOR v_device IN
    SELECT md.user_id,md.device_id,md.ciphersuite,md.credential_identity,md.signature_public_key
    FROM public.conversation_members cm
    JOIN public.mls_devices md ON md.user_id=cm.user_id AND md.revoked_at IS NULL
    JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
    WHERE cm.conversation_id=p_conversation_id
      AND NOT (md.user_id=v_me AND md.device_id=p_sender_device_id)
      AND NOT EXISTS (
        SELECT 1 FROM public.mls_group_devices gd
        WHERE gd.conversation_id=p_conversation_id
          AND gd.user_id=md.user_id AND gd.device_id=md.device_id
          AND gd.removed_epoch IS NULL
      )
    ORDER BY md.user_id,md.device_id
  LOOP
    SELECT kp.id,kp.key_package INTO v_pkg
    FROM public.mls_key_packages kp
    WHERE kp.user_id=v_device.user_id AND kp.device_id=v_device.device_id
      AND kp.claimed_at IS NULL AND kp.used_at IS NULL AND kp.expires_at>now()
    ORDER BY kp.created_at
    FOR UPDATE SKIP LOCKED
    LIMIT 1;

    IF v_pkg.id IS NULL THEN
      RAISE EXCEPTION 'MLS key package unavailable for device %/%',v_device.user_id,v_device.device_id;
    END IF;

    UPDATE public.mls_key_packages
    SET claimed_at=now(),claimed_by=v_me
    WHERE id=v_pkg.id AND claimed_at IS NULL;

    v_packages := v_packages || jsonb_build_array(jsonb_build_object(
      'key_package_id',v_pkg.id,
      'user_id',v_device.user_id,
      'device_id',v_device.device_id,
      'ciphersuite',v_device.ciphersuite,
      'credential_identity',v_device.credential_identity,
      'signature_public_key',v_device.signature_public_key,
      'key_package',v_pkg.key_package
    ));
    v_pkg := NULL;
  END LOOP;

  RETURN jsonb_build_object('conversation_id',p_conversation_id,'packages',v_packages);
END;
$function$;
REVOKE ALL ON FUNCTION public.claim_mls_conversation_key_packages_v1(uuid,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_mls_conversation_key_packages_v1(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.publish_mls_group_v1(
  p_conversation_id uuid,
  p_sender_device_id text,
  p_group_id text,
  p_ciphersuite text,
  p_epoch bigint,
  p_welcome_payload text,
  p_recipient_packages jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_item jsonb;
  v_package_id uuid;
  v_user_id uuid;
  v_device_id text;
  v_expected integer;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF p_ciphersuite <> 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519' THEN RAISE EXCEPTION 'unsupported MLS ciphersuite'; END IF;
  IF p_epoch < 0 THEN RAISE EXCEPTION 'invalid MLS epoch'; END IF;
  IF p_group_id IS NULL OR octet_length(p_group_id) NOT BETWEEN 4 AND 1024 THEN RAISE EXCEPTION 'invalid MLS group id'; END IF;
  IF jsonb_typeof(p_recipient_packages) <> 'array' THEN RAISE EXCEPTION 'recipient packages must be an array'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_devices md JOIN public.linked_devices ld
      ON ld.user_id=md.user_id AND ld.device_id=md.device_id
    WHERE md.user_id=v_me AND md.device_id=p_sender_device_id
      AND md.revoked_at IS NULL AND ld.revoked_at IS NULL
  ) THEN RAISE EXCEPTION 'sender MLS device is not active'; END IF;
  IF EXISTS (SELECT 1 FROM public.mls_conversation_groups WHERE conversation_id=p_conversation_id) THEN
    RAISE EXCEPTION 'MLS group already initialized';
  END IF;

  SELECT count(*) INTO v_expected
  FROM public.conversation_members cm
  JOIN public.mls_devices md ON md.user_id=cm.user_id AND md.revoked_at IS NULL
  JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
  WHERE cm.conversation_id=p_conversation_id
    AND NOT (md.user_id=v_me AND md.device_id=p_sender_device_id);
  IF jsonb_array_length(p_recipient_packages) <> v_expected THEN
    RAISE EXCEPTION 'MLS recipient package coverage mismatch';
  END IF;
  IF v_expected > 0 AND (p_welcome_payload IS NULL OR octet_length(p_welcome_payload) NOT BETWEEN 16 AND 1048576) THEN
    RAISE EXCEPTION 'invalid MLS Welcome payload';
  END IF;

  INSERT INTO public.mls_conversation_groups(conversation_id,group_id,protocol_suite,ciphersuite,epoch,created_by)
  VALUES(p_conversation_id,p_group_id,'mls-rfc9420-v1',p_ciphersuite,p_epoch,v_me);
  INSERT INTO public.mls_group_devices(conversation_id,user_id,device_id,joined_epoch)
  VALUES(p_conversation_id,v_me,p_sender_device_id,0);

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_recipient_packages)
  LOOP
    v_package_id := (v_item->>'key_package_id')::uuid;
    v_user_id := (v_item->>'user_id')::uuid;
    v_device_id := v_item->>'device_id';
    IF NOT EXISTS (
      SELECT 1 FROM public.mls_key_packages kp
      JOIN public.mls_devices md ON md.user_id=kp.user_id AND md.device_id=kp.device_id AND md.revoked_at IS NULL
      JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
      JOIN public.conversation_members cm ON cm.conversation_id=p_conversation_id AND cm.user_id=kp.user_id
      WHERE kp.id=v_package_id AND kp.user_id=v_user_id AND kp.device_id=v_device_id
        AND kp.claimed_by=v_me AND kp.claimed_at IS NOT NULL AND kp.used_at IS NULL
    ) THEN RAISE EXCEPTION 'invalid or unclaimed MLS recipient package'; END IF;

    INSERT INTO public.mls_group_devices(conversation_id,user_id,device_id,joined_epoch)
    VALUES(p_conversation_id,v_user_id,v_device_id,p_epoch);
    INSERT INTO public.mls_welcomes(conversation_id,recipient_user_id,recipient_device_id,sender_user_id,sender_device_id,epoch,welcome_payload)
    VALUES(p_conversation_id,v_user_id,v_device_id,v_me,p_sender_device_id,p_epoch,p_welcome_payload);
    UPDATE public.mls_key_packages SET used_at=now() WHERE id=v_package_id;
  END LOOP;

  RETURN jsonb_build_object('conversation_id',p_conversation_id,'group_id',p_group_id,'epoch',p_epoch);
END;
$function$;
REVOKE ALL ON FUNCTION public.publish_mls_group_v1(uuid,text,text,text,bigint,text,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.publish_mls_group_v1(uuid,text,text,text,bigint,text,jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.publish_mls_membership_update_v1(
  p_conversation_id uuid,
  p_sender_device_id text,
  p_group_id text,
  p_new_epoch bigint,
  p_commit_payload text,
  p_welcome_payload text,
  p_additions jsonb DEFAULT '[]'::jsonb,
  p_removals jsonb DEFAULT '[]'::jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_group public.mls_conversation_groups%ROWTYPE;
  v_item jsonb;
  v_package_id uuid;
  v_user_id uuid;
  v_device_id text;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF jsonb_typeof(p_additions) <> 'array' OR jsonb_typeof(p_removals) <> 'array' THEN RAISE EXCEPTION 'invalid MLS membership update'; END IF;
  IF p_commit_payload IS NULL OR octet_length(p_commit_payload) NOT BETWEEN 16 AND 1048576 THEN RAISE EXCEPTION 'invalid MLS commit payload'; END IF;

  SELECT * INTO v_group FROM public.mls_conversation_groups
  WHERE conversation_id=p_conversation_id FOR UPDATE;
  IF NOT FOUND OR v_group.group_id<>p_group_id THEN RAISE EXCEPTION 'MLS group not found'; END IF;
  IF p_new_epoch <> v_group.epoch+1 THEN RAISE EXCEPTION 'MLS epoch must advance exactly once'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_group_devices gd
    JOIN public.mls_devices md ON md.user_id=gd.user_id AND md.device_id=gd.device_id AND md.revoked_at IS NULL
    JOIN public.linked_devices ld ON ld.user_id=gd.user_id AND ld.device_id=gd.device_id AND ld.revoked_at IS NULL
    WHERE gd.conversation_id=p_conversation_id AND gd.user_id=v_me AND gd.device_id=p_sender_device_id
      AND gd.removed_epoch IS NULL
  ) THEN RAISE EXCEPTION 'sender is not an active MLS group device'; END IF;

  IF jsonb_array_length(p_additions)>0 AND (p_welcome_payload IS NULL OR octet_length(p_welcome_payload) NOT BETWEEN 16 AND 1048576) THEN
    RAISE EXCEPTION 'MLS additions require a Welcome payload';
  END IF;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_removals)
  LOOP
    v_user_id := (v_item->>'user_id')::uuid;
    v_device_id := v_item->>'device_id';
    IF NOT EXISTS (
      SELECT 1 FROM public.mls_group_devices gd
      WHERE gd.conversation_id=p_conversation_id AND gd.user_id=v_user_id AND gd.device_id=v_device_id AND gd.removed_epoch IS NULL
    ) THEN RAISE EXCEPTION 'MLS removal target is not active'; END IF;
    IF EXISTS (
      SELECT 1 FROM public.conversation_members cm
      JOIN public.mls_devices md ON md.user_id=cm.user_id AND md.device_id=v_device_id AND md.revoked_at IS NULL
      JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
      WHERE cm.conversation_id=p_conversation_id AND cm.user_id=v_user_id
    ) AND v_user_id<>v_me THEN
      RAISE EXCEPTION 'cannot remove an active device for a current conversation member';
    END IF;
  END LOOP;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_additions)
  LOOP
    v_package_id := (v_item->>'key_package_id')::uuid;
    v_user_id := (v_item->>'user_id')::uuid;
    v_device_id := v_item->>'device_id';
    IF NOT EXISTS (
      SELECT 1 FROM public.mls_key_packages kp
      JOIN public.mls_devices md ON md.user_id=kp.user_id AND md.device_id=kp.device_id AND md.revoked_at IS NULL
      JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
      JOIN public.conversation_members cm ON cm.conversation_id=p_conversation_id AND cm.user_id=kp.user_id
      WHERE kp.id=v_package_id AND kp.user_id=v_user_id AND kp.device_id=v_device_id
        AND kp.claimed_by=v_me AND kp.claimed_at IS NOT NULL AND kp.used_at IS NULL
        AND NOT EXISTS (
          SELECT 1 FROM public.mls_group_devices gd
          WHERE gd.conversation_id=p_conversation_id AND gd.user_id=kp.user_id AND gd.device_id=kp.device_id AND gd.removed_epoch IS NULL
        )
    ) THEN RAISE EXCEPTION 'invalid MLS addition package'; END IF;
  END LOOP;

  INSERT INTO public.mls_control_messages(conversation_id,sender_user_id,sender_device_id,epoch,commit_payload)
  VALUES(p_conversation_id,v_me,p_sender_device_id,p_new_epoch,p_commit_payload);

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_removals)
  LOOP
    v_user_id := (v_item->>'user_id')::uuid;
    v_device_id := v_item->>'device_id';
    UPDATE public.mls_group_devices SET removed_epoch=p_new_epoch
    WHERE conversation_id=p_conversation_id AND user_id=v_user_id AND device_id=v_device_id AND removed_epoch IS NULL;
  END LOOP;

  FOR v_item IN SELECT value FROM jsonb_array_elements(p_additions)
  LOOP
    v_package_id := (v_item->>'key_package_id')::uuid;
    v_user_id := (v_item->>'user_id')::uuid;
    v_device_id := v_item->>'device_id';
    INSERT INTO public.mls_group_devices(conversation_id,user_id,device_id,joined_epoch,removed_epoch)
    VALUES(p_conversation_id,v_user_id,v_device_id,p_new_epoch,NULL)
    ON CONFLICT(conversation_id,user_id,device_id) DO UPDATE
      SET joined_epoch=excluded.joined_epoch,removed_epoch=NULL;
    INSERT INTO public.mls_welcomes(conversation_id,recipient_user_id,recipient_device_id,sender_user_id,sender_device_id,epoch,welcome_payload)
    VALUES(p_conversation_id,v_user_id,v_device_id,v_me,p_sender_device_id,p_new_epoch,p_welcome_payload);
    UPDATE public.mls_key_packages SET used_at=now() WHERE id=v_package_id;
  END LOOP;

  UPDATE public.mls_conversation_groups SET epoch=p_new_epoch,updated_at=now()
  WHERE conversation_id=p_conversation_id;

  RETURN jsonb_build_object('conversation_id',p_conversation_id,'group_id',p_group_id,'epoch',p_new_epoch);
END;
$function$;
REVOKE ALL ON FUNCTION public.publish_mls_membership_update_v1(uuid,text,text,bigint,text,text,jsonb,jsonb) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.publish_mls_membership_update_v1(uuid,text,text,bigint,text,text,jsonb,jsonb) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_mls_conversation_state_v1(
  p_conversation_id uuid,
  p_device_id text,
  p_after_epoch bigint DEFAULT 0
)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_group public.mls_conversation_groups%ROWTYPE;
  v_welcome jsonb;
  v_controls jsonb;
  v_server_devices jsonb;
  v_group_devices jsonb;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_devices md JOIN public.linked_devices ld
      ON ld.user_id=md.user_id AND ld.device_id=md.device_id
    WHERE md.user_id=v_me AND md.device_id=p_device_id AND md.revoked_at IS NULL AND ld.revoked_at IS NULL
  ) THEN RAISE EXCEPTION 'MLS device is not active'; END IF;

  SELECT * INTO v_group FROM public.mls_conversation_groups WHERE conversation_id=p_conversation_id;

  SELECT to_jsonb(x) INTO v_welcome FROM (
    SELECT w.id,w.epoch,w.welcome_payload,w.sender_user_id,w.sender_device_id,w.created_at
    FROM public.mls_welcomes w
    WHERE w.conversation_id=p_conversation_id
      AND w.recipient_user_id=v_me AND w.recipient_device_id=p_device_id
      AND w.consumed_at IS NULL
    ORDER BY w.epoch DESC,w.created_at DESC LIMIT 1
  ) x;

  SELECT coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'epoch',c.epoch,'commit_payload',c.commit_payload,
    'sender_user_id',c.sender_user_id,'sender_device_id',c.sender_device_id,'created_at',c.created_at
  ) ORDER BY c.epoch),'[]'::jsonb) INTO v_controls
  FROM public.mls_control_messages c
  WHERE c.conversation_id=p_conversation_id AND c.epoch>greatest(coalesce(p_after_epoch,0),0);

  SELECT coalesce(jsonb_agg(jsonb_build_object(
    'user_id',md.user_id,'device_id',md.device_id,'credential_identity',md.credential_identity,
    'signature_public_key',md.signature_public_key,'ciphersuite',md.ciphersuite
  ) ORDER BY md.user_id,md.device_id),'[]'::jsonb) INTO v_server_devices
  FROM public.conversation_members cm
  JOIN public.mls_devices md ON md.user_id=cm.user_id AND md.revoked_at IS NULL
  JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id AND ld.revoked_at IS NULL
  WHERE cm.conversation_id=p_conversation_id;

  SELECT coalesce(jsonb_agg(jsonb_build_object(
    'user_id',gd.user_id,'device_id',gd.device_id,'joined_epoch',gd.joined_epoch,'removed_epoch',gd.removed_epoch
  ) ORDER BY gd.user_id,gd.device_id),'[]'::jsonb) INTO v_group_devices
  FROM public.mls_group_devices gd
  WHERE gd.conversation_id=p_conversation_id;

  RETURN jsonb_build_object(
    'conversation_id',p_conversation_id,
    'group',CASE WHEN v_group.conversation_id IS NULL THEN NULL ELSE jsonb_build_object(
      'group_id',v_group.group_id,'protocol_suite',v_group.protocol_suite,
      'ciphersuite',v_group.ciphersuite,'epoch',v_group.epoch
    ) END,
    'welcome',v_welcome,
    'controls',coalesce(v_controls,'[]'::jsonb),
    'server_devices',coalesce(v_server_devices,'[]'::jsonb),
    'group_devices',coalesce(v_group_devices,'[]'::jsonb)
  );
END;
$function$;
REVOKE ALL ON FUNCTION public.get_mls_conversation_state_v1(uuid,text,bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_mls_conversation_state_v1(uuid,text,bigint) TO authenticated;

CREATE OR REPLACE FUNCTION public.ack_mls_welcome_v1(p_welcome_id uuid,p_device_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE v_me uuid:=auth.uid();
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  UPDATE public.mls_welcomes SET consumed_at=coalesce(consumed_at,now())
  WHERE id=p_welcome_id AND recipient_user_id=v_me AND recipient_device_id=p_device_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'MLS Welcome not found'; END IF;
END;
$function$;
REVOKE ALL ON FUNCTION public.ack_mls_welcome_v1(uuid,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.ack_mls_welcome_v1(uuid,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.send_mls_message_v1(
  p_conversation_id uuid,
  p_client_message_id uuid,
  p_sender_device_id text,
  p_group_id text,
  p_epoch bigint,
  p_ciphertext text
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid:=auth.uid();
  v_group public.mls_conversation_groups%ROWTYPE;
  v_id uuid;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF p_ciphertext IS NULL OR octet_length(p_ciphertext) NOT BETWEEN 16 AND 1048576 THEN RAISE EXCEPTION 'invalid MLS ciphertext'; END IF;
  SELECT * INTO v_group FROM public.mls_conversation_groups WHERE conversation_id=p_conversation_id;
  IF NOT FOUND OR v_group.group_id<>p_group_id THEN RAISE EXCEPTION 'MLS group mismatch'; END IF;
  IF p_epoch<greatest(v_group.epoch-5,0) OR p_epoch>v_group.epoch THEN RAISE EXCEPTION 'MLS message epoch is outside accepted window'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_group_devices gd
    JOIN public.mls_devices md ON md.user_id=gd.user_id AND md.device_id=gd.device_id AND md.revoked_at IS NULL
    JOIN public.linked_devices ld ON ld.user_id=gd.user_id AND ld.device_id=gd.device_id AND ld.revoked_at IS NULL
    WHERE gd.conversation_id=p_conversation_id AND gd.user_id=v_me AND gd.device_id=p_sender_device_id
      AND gd.removed_epoch IS NULL
  ) THEN RAISE EXCEPTION 'sender is not an active MLS group device'; END IF;

  INSERT INTO public.messages(conversation_id,sender_id,client_message_id,type,body,metadata,encryption_version,encryption_protocol,sender_device_id,encrypted_payload)
  VALUES(p_conversation_id,v_me,p_client_message_id,'text','',jsonb_build_object('encrypted',true,'mls_group_id',p_group_id,'mls_epoch',p_epoch),2,'mls-rfc9420-v1',p_sender_device_id,p_ciphertext)
  ON CONFLICT(sender_id,client_message_id) DO NOTHING
  RETURNING id INTO v_id;
  IF v_id IS NULL THEN
    SELECT id INTO v_id FROM public.messages WHERE sender_id=v_me AND client_message_id=p_client_message_id;
  ELSE
    UPDATE public.conversations SET updated_at=now() WHERE id=p_conversation_id;
    UPDATE public.conversation_members SET unread_count=unread_count+1
    WHERE conversation_id=p_conversation_id AND user_id<>v_me;
  END IF;
  RETURN v_id;
END;
$function$;
REVOKE ALL ON FUNCTION public.send_mls_message_v1(uuid,uuid,text,text,bigint,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.send_mls_message_v1(uuid,uuid,text,text,bigint,text) TO authenticated;

CREATE OR REPLACE FUNCTION public.edit_mls_message_v1(
  p_message_id uuid,
  p_sender_device_id text,
  p_group_id text,
  p_epoch bigint,
  p_ciphertext text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE
  v_me uuid:=auth.uid();
  v_conversation uuid;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  SELECT conversation_id INTO v_conversation FROM public.messages
  WHERE id=p_message_id AND sender_id=v_me AND deleted_at IS NULL AND encryption_protocol='mls-rfc9420-v1';
  IF v_conversation IS NULL THEN RAISE EXCEPTION 'encrypted message not found'; END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.mls_conversation_groups g
    JOIN public.mls_group_devices gd ON gd.conversation_id=g.conversation_id
    WHERE g.conversation_id=v_conversation AND g.group_id=p_group_id
      AND p_epoch BETWEEN greatest(g.epoch-5,0) AND g.epoch
      AND gd.user_id=v_me AND gd.device_id=p_sender_device_id AND gd.removed_epoch IS NULL
  ) THEN RAISE EXCEPTION 'MLS sender or group mismatch'; END IF;
  IF p_ciphertext IS NULL OR octet_length(p_ciphertext) NOT BETWEEN 16 AND 1048576 THEN RAISE EXCEPTION 'invalid MLS ciphertext'; END IF;
  UPDATE public.messages SET encrypted_payload=p_ciphertext,
    metadata=jsonb_build_object('encrypted',true,'mls_group_id',p_group_id,'mls_epoch',p_epoch),
    sender_device_id=p_sender_device_id,edited_at=now()
  WHERE id=p_message_id;
END;
$function$;
REVOKE ALL ON FUNCTION public.edit_mls_message_v1(uuid,text,text,bigint,text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.edit_mls_message_v1(uuid,text,text,bigint,text) TO authenticated;

-- Accept the MLS protocol suite in the encrypted-message invariant while
-- preserving the legacy Signal-style transport check for its unused v1 path.
CREATE OR REPLACE FUNCTION private.enforce_encrypted_message_protocol_suite()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
  IF NEW.encryption_version > 0 THEN
    IF NEW.encryption_protocol IS NULL OR NEW.sender_device_id IS NULL THEN
      RAISE EXCEPTION 'encrypted messages require protocol and sender device';
    END IF;
    IF NEW.encryption_protocol='mls-rfc9420-v1' THEN
      IF NEW.encryption_version<>2 THEN RAISE EXCEPTION 'invalid MLS encryption version'; END IF;
      IF NEW.deleted_at IS NULL AND (NEW.encrypted_payload IS NULL OR octet_length(NEW.encrypted_payload) NOT BETWEEN 16 AND 1048576) THEN
        RAISE EXCEPTION 'MLS message requires an opaque ciphertext payload';
      END IF;
      IF NOT EXISTS (
        SELECT 1 FROM public.mls_devices md
        JOIN public.linked_devices ld ON ld.user_id=md.user_id AND ld.device_id=md.device_id
        WHERE md.user_id=NEW.sender_id AND md.device_id=NEW.sender_device_id
          AND md.protocol_suite=NEW.encryption_protocol
          AND md.revoked_at IS NULL AND ld.revoked_at IS NULL
      ) THEN RAISE EXCEPTION 'MLS message sender device is not active'; END IF;
    ELSE
      IF NOT EXISTS (
        SELECT 1 FROM public.e2ee_device_bundles b
        JOIN public.linked_devices ld ON ld.user_id=b.user_id AND ld.device_id=b.device_id
        WHERE b.user_id=NEW.sender_id AND b.device_id=NEW.sender_device_id
          AND b.protocol_suite=NEW.encryption_protocol
          AND b.revoked_at IS NULL AND ld.revoked_at IS NULL
      ) THEN RAISE EXCEPTION 'encrypted message protocol does not match active sender device suite'; END IF;
    END IF;
  END IF;
  RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION private.enforce_encrypted_message_protocol_suite() FROM PUBLIC, anon, authenticated;

-- Delete-for-everyone must remove opaque ciphertext too.
CREATE OR REPLACE FUNCTION public.delete_chat_message(p_message_id uuid,p_for_everyone boolean)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE v_sender uuid; v_conversation uuid;
BEGIN
  SELECT sender_id,conversation_id INTO v_sender,v_conversation FROM public.messages WHERE id=p_message_id;
  IF v_conversation IS NULL OR NOT public.is_conversation_member(v_conversation) THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF p_for_everyone THEN
    IF v_sender<>auth.uid() THEN RAISE EXCEPTION 'only sender can delete for everyone'; END IF;
    UPDATE public.messages SET deleted_at=coalesce(deleted_at,now()),encrypted_payload=NULL WHERE id=p_message_id;
  ELSE
    PERFORM public.set_message_user_state(p_message_id,'hidden',true);
  END IF;
END;
$function$;
REVOKE ALL ON FUNCTION public.delete_chat_message(uuid,boolean) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_chat_message(uuid,boolean) TO authenticated;

-- Keep server-side conversation previews content-blind for MLS messages.
CREATE OR REPLACE FUNCTION public.get_my_conversations()
RETURNS TABLE(conversation_id uuid,kind text,title text,avatar_url text,avatar_initials text,avatar_color_hex text,participant_ids uuid[],admin_ids uuid[],last_message text,last_message_sender_id uuid,last_message_at timestamptz,unread_count integer,is_pinned boolean,is_archived boolean,is_muted boolean,draft_text text)
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path = ''
AS $function$
  SELECT c.id,c.kind,
    CASE WHEN c.kind='direct' THEN coalesce(other_profile.display_name,other_profile.username,'Conversation') ELSE coalesce(c.title,'Group') END,
    CASE WHEN c.kind='direct' THEN other_profile.avatar_url ELSE NULL END,
    CASE WHEN c.kind='direct' THEN other_profile.avatar_initials ELSE upper(left(coalesce(c.title,'GP'),2)) END,
    CASE WHEN c.kind='direct' THEN other_profile.avatar_color_hex ELSE '0xFF8B5CF6' END,
    members.participant_ids,members.admin_ids,
    CASE WHEN last_message.encryption_protocol='mls-rfc9420-v1' THEN 'Encrypted message' ELSE coalesce(last_message.body,'') END,
    last_message.sender_id,coalesce(last_message.created_at,c.created_at),mine.unread_count,mine.is_pinned,mine.is_archived,mine.is_muted,mine.draft_text
  FROM public.conversations c JOIN public.conversation_members mine ON mine.conversation_id=c.id AND mine.user_id=auth.uid()
  LEFT JOIN LATERAL(SELECT p.username,p.display_name,p.avatar_url,p.avatar_initials,p.avatar_color_hex FROM public.conversation_members cm JOIN public.profiles p ON p.id=cm.user_id WHERE cm.conversation_id=c.id AND cm.user_id<>auth.uid() ORDER BY cm.joined_at LIMIT 1) other_profile ON true
  LEFT JOIN LATERAL(SELECT array_agg(cm.user_id ORDER BY cm.joined_at) participant_ids,coalesce(array_agg(cm.user_id ORDER BY cm.joined_at) FILTER(WHERE cm.role IN('owner','admin')),'{}'::uuid[]) admin_ids FROM public.conversation_members cm WHERE cm.conversation_id=c.id) members ON true
  LEFT JOIN LATERAL(SELECT m.body,m.sender_id,m.created_at,m.encryption_protocol FROM public.messages m WHERE m.conversation_id=c.id AND m.deleted_at IS NULL ORDER BY m.created_at DESC LIMIT 1) last_message ON true
  ORDER BY mine.is_pinned DESC,coalesce(last_message.created_at,c.updated_at) DESC;
$function$;
REVOKE ALL ON FUNCTION public.get_my_conversations() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_my_conversations() TO authenticated;

-- Expand the message read contract with opaque MLS transport fields.
DROP FUNCTION IF EXISTS public.get_conversation_messages(uuid,integer,timestamptz);
CREATE FUNCTION public.get_conversation_messages(p_conversation_id uuid,p_limit integer DEFAULT 100,p_before timestamptz DEFAULT NULL)
RETURNS TABLE(id uuid,conversation_id uuid,sender_id uuid,type text,body text,metadata jsonb,created_at timestamptz,edited_at timestamptz,deleted_at timestamptz,reactions jsonb,is_starred boolean,is_pinned boolean,is_hidden boolean,is_read_by_other boolean,encryption_version smallint,encryption_protocol text,sender_device_id text,encrypted_payload text)
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path = ''
AS $function$
DECLARE v_anti_delete boolean:=false;
BEGIN
  IF NOT public.is_conversation_member(p_conversation_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  SELECT coalesce((settings->'gbFeatures'->>'yoAntiRevoke')::boolean,(settings->'privacy'->>'antiDeleteMessages')::boolean,false)
  INTO v_anti_delete FROM public.user_feature_settings WHERE user_id=auth.uid();
  RETURN QUERY
  SELECT m.id,m.conversation_id,m.sender_id,m.type,
    CASE WHEN m.type='poll' THEN '[POLL] '||m.body ELSE m.body END,
    ((CASE WHEN m.type='poll' THEN m.metadata||jsonb_build_object('poll',(SELECT jsonb_build_object(
       'id',p.id,'question',p.question,'allow_multiple',p.allow_multiple,
       'total_votes',(SELECT count(*) FROM public.poll_votes pv WHERE pv.poll_id=p.id),
       'options',coalesce((SELECT jsonb_agg(jsonb_build_object('id',po.id,'label',po.label,'position',po.position,'votes',(SELECT count(*) FROM public.poll_votes pv WHERE pv.option_id=po.id),'voted_by_me',exists(SELECT 1 FROM public.poll_votes pv WHERE pv.option_id=po.id AND pv.user_id=auth.uid())) ORDER BY po.position) FROM public.poll_options po WHERE po.poll_id=p.id),'[]'::jsonb)
    ) FROM public.polls p WHERE p.message_id=m.id)) ELSE m.metadata END)
    || CASE WHEN m.deleted_at IS NOT NULL AND v_anti_delete THEN jsonb_build_object('anti_deleted',true,'deleted_original_at',m.deleted_at) ELSE '{}'::jsonb END
    || jsonb_build_object('delivery_state', CASE WHEN m.sender_id=auth.uid() THEN
         CASE WHEN exists(SELECT 1 FROM public.message_receipts rec WHERE rec.message_id=m.id AND rec.user_id<>m.sender_id AND rec.read_at IS NOT NULL) THEN 'read'
              WHEN exists(SELECT 1 FROM public.message_receipts rec WHERE rec.message_id=m.id AND rec.user_id<>m.sender_id AND rec.delivered_at IS NOT NULL) THEN 'delivered'
              ELSE 'sent' END
         ELSE 'delivered' END)),
    m.created_at,m.edited_at,CASE WHEN v_anti_delete THEN NULL ELSE m.deleted_at END,
    coalesce((SELECT jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) FROM (SELECT mr.emoji,array_agg(mr.user_id) user_ids FROM public.message_reactions mr WHERE mr.message_id=m.id GROUP BY mr.emoji)x),'[]'::jsonb),
    coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
    exists(SELECT 1 FROM public.message_receipts rec WHERE rec.message_id=m.id AND rec.user_id<>m.sender_id AND rec.read_at IS NOT NULL),
    m.encryption_version,m.encryption_protocol,m.sender_device_id,m.encrypted_payload
  FROM public.messages m LEFT JOIN public.message_user_state mus ON mus.message_id=m.id AND mus.user_id=auth.uid()
  WHERE m.conversation_id=p_conversation_id AND (p_before IS NULL OR m.created_at<p_before)
  ORDER BY m.created_at DESC LIMIT greatest(1,least(coalesce(p_limit,100),200));
END;
$function$;
REVOKE ALL ON FUNCTION public.get_conversation_messages(uuid,integer,timestamptz) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_conversation_messages(uuid,integer,timestamptz) TO authenticated;

-- Index opaque encrypted messages by conversation/time using the existing messages
-- conversation index; no plaintext search/index is intentionally introduced.
