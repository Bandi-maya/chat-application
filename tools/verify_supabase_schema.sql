\set ON_ERROR_STOP on

DO $$
DECLARE
  expected_tables text[] := ARRAY[
    'auto_reply_log','auto_reply_rules','blocked_users','call_ice_candidates',
    'call_sessions','contact_connections','contact_presence_visibility',
    'contact_privacy_overrides','conversation_key_envelopes',
    'conversation_key_versions','conversation_members','conversations',
    'e2ee_device_bundles','e2ee_one_time_prekeys','linked_devices',
    'mass_message_collections','message_device_ciphertexts',
    'message_edit_history','message_reactions','message_receipts',
    'message_user_state','messages','poll_options','poll_votes','polls',
    'profiles','quick_reply_templates','reports','scheduled_messages',
    'status_updates','status_views','task_activity','task_assignees','tasks',
    'typing_states','user_e2ee_keys','user_feature_settings'
  ];
  missing text[];
  without_rls text[];
BEGIN
  SELECT array_agg(t)
  INTO missing
  FROM unnest(expected_tables) t
  WHERE to_regclass('public.' || t) IS NULL;
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Missing required public tables: %', missing;
  END IF;

  SELECT array_agg(c.relname ORDER BY c.relname)
  INTO without_rls
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relkind = 'r'
    AND c.relname = ANY(expected_tables)
    AND NOT c.relrowsecurity;
  IF without_rls IS NOT NULL THEN
    RAISE EXCEPTION 'Required tables without RLS: %', without_rls;
  END IF;

  IF (SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = ANY(expected_tables)) <> cardinality(expected_tables) THEN
    RAISE EXCEPTION 'Public table count invariant failed';
  END IF;

  IF to_regprocedure('public.create_direct_conversation(uuid)') IS NULL
     OR to_regprocedure('public.get_conversation_messages(uuid,integer,timestamp with time zone)') IS NULL
     OR to_regprocedure('public.send_encrypted_message_v1(uuid,uuid,text,text,jsonb)') IS NULL
     OR to_regprocedure('public.get_e2ee_prekey_bundle(uuid)') IS NULL
     OR to_regprocedure('public.accept_call_session(uuid,text)') IS NULL
     OR to_regprocedure('public.decline_call_session(uuid)') IS NULL
     OR to_regprocedure('public.end_call_session(uuid)') IS NULL THEN
    RAISE EXCEPTION 'One or more critical application RPC signatures are missing';
  END IF;

  IF has_function_privilege('anon', 'public.resolve_login_email(text,text)', 'EXECUTE')
     OR has_function_privilege('authenticated', 'public.resolve_login_email(text,text)', 'EXECUTE') THEN
    RAISE EXCEPTION 'Legacy password-taking resolve_login_email RPC is executable by client roles';
  END IF;

  IF EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'status_updates'
      AND policyname = 'status_updates_select_authenticated'
  ) THEN
    RAISE EXCEPTION 'Broad status_updates_select_authenticated policy must not exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.messages'::regclass
      AND conname = 'messages_encryption_shape_check'
  ) OR NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'public.e2ee_device_bundles'::regclass
      AND conname = 'e2ee_device_bundles_protocol_suite_check'
  ) THEN
    RAISE EXCEPTION 'E2EE schema invariants are missing';
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets
    WHERE id = 'chat-media' AND public = false AND file_size_limit = 52428800
  ) THEN
    RAISE EXCEPTION 'chat-media bucket invariant failed';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM storage.buckets
    WHERE id = 'status-media' AND public = false AND file_size_limit = 52428800
  ) THEN
    RAISE EXCEPTION 'status-media bucket invariant failed';
  END IF;
END
$$;

DO $$
DECLARE
  required_tables text[] := ARRAY[
    'messages','tasks','conversation_members','message_reactions',
    'message_receipts','status_updates','call_sessions','call_ice_candidates'
  ];
  missing text[];
BEGIN
  SELECT array_agg(t)
  INTO missing
  FROM unnest(required_tables) t
  WHERE NOT EXISTS (
    SELECT 1
    FROM pg_publication_tables p
    WHERE p.pubname = 'supabase_realtime'
      AND p.schemaname = 'public'
      AND p.tablename = t
  );
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'Required realtime tables missing from publication: %', missing;
  END IF;
END
$$;

SELECT 'Chaty Supabase clean-replay invariants passed.' AS result;
