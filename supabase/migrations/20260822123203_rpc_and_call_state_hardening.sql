-- Harden privileged RPCs and the WebRTC signaling state machine.

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'call_sessions_sdp_size_check'
      AND conrelid = 'public.call_sessions'::regclass
  ) THEN
    ALTER TABLE public.call_sessions
      ADD CONSTRAINT call_sessions_sdp_size_check
      CHECK (
        (offer_sdp IS NULL OR octet_length(offer_sdp) BETWEEN 1 AND 131072)
        AND
        (answer_sdp IS NULL OR octet_length(answer_sdp) BETWEEN 1 AND 131072)
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'call_ice_candidates_payload_check'
      AND conrelid = 'public.call_ice_candidates'::regclass
  ) THEN
    ALTER TABLE public.call_ice_candidates
      ADD CONSTRAINT call_ice_candidates_payload_check
      CHECK (
        octet_length(candidate) BETWEEN 1 AND 4096
        AND (sdp_mid IS NULL OR octet_length(sdp_mid) <= 256)
        AND (sdp_mline_index IS NULL OR sdp_mline_index BETWEEN 0 AND 32)
      );
  END IF;
END
$$;

DROP POLICY IF EXISTS call_sessions_caller_insert ON public.call_sessions;
CREATE POLICY call_sessions_caller_insert
ON public.call_sessions
FOR INSERT
TO authenticated
WITH CHECK (
  caller_id = (SELECT auth.uid())
  AND caller_id <> callee_id
  AND status = 'ringing'
  AND offer_sdp IS NOT NULL
  AND octet_length(offer_sdp) BETWEEN 1 AND 131072
  AND answer_sdp IS NULL
  AND connected_at IS NULL
  AND ended_at IS NULL
  AND ended_by IS NULL
  AND started_at BETWEEN now() - interval '5 minutes' AND now() + interval '5 minutes'
  AND created_at BETWEEN now() - interval '5 minutes' AND now() + interval '5 minutes'
  AND updated_at BETWEEN now() - interval '5 minutes' AND now() + interval '5 minutes'
  AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = call_sessions.conversation_id
      AND cm.user_id = (SELECT auth.uid())
  )
  AND EXISTS (
    SELECT 1 FROM public.conversation_members cm
    WHERE cm.conversation_id = call_sessions.conversation_id
      AND cm.user_id = call_sessions.callee_id
  )
  AND EXISTS (
    SELECT 1 FROM public.contact_connections cc
    WHERE cc.conversation_id = call_sessions.conversation_id
      AND cc.low_accepted = true
      AND cc.high_accepted = true
      AND (
        (cc.user_low_id = call_sessions.caller_id AND cc.user_high_id = call_sessions.callee_id)
        OR
        (cc.user_high_id = call_sessions.caller_id AND cc.user_low_id = call_sessions.callee_id)
      )
  )
);

DROP POLICY IF EXISTS call_ice_participants_insert ON public.call_ice_candidates;
CREATE POLICY call_ice_participants_insert
ON public.call_ice_candidates
FOR INSERT
TO authenticated
WITH CHECK (
  sender_id = (SELECT auth.uid())
  AND octet_length(candidate) BETWEEN 1 AND 4096
  AND (sdp_mid IS NULL OR octet_length(sdp_mid) <= 256)
  AND (sdp_mline_index IS NULL OR sdp_mline_index BETWEEN 0 AND 32)
  AND EXISTS (
    SELECT 1 FROM public.call_sessions cs
    WHERE cs.id = call_ice_candidates.call_id
      AND cs.status IN ('ringing', 'accepted')
      AND ((SELECT auth.uid()) = cs.caller_id OR (SELECT auth.uid()) = cs.callee_id)
  )
);

CREATE OR REPLACE FUNCTION public.guard_call_session_update()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_me uuid := auth.uid();
BEGIN
  IF v_me IS NULL OR (v_me <> old.caller_id AND v_me <> old.callee_id) THEN
    RAISE EXCEPTION 'Not a call participant';
  END IF;

  IF new.id <> old.id
     OR new.conversation_id <> old.conversation_id
     OR new.caller_id <> old.caller_id
     OR new.callee_id <> old.callee_id
     OR new.kind <> old.kind
     OR new.offer_sdp IS DISTINCT FROM old.offer_sdp
     OR new.started_at <> old.started_at
     OR new.created_at <> old.created_at THEN
    RAISE EXCEPTION 'Immutable call session fields cannot be changed';
  END IF;

  IF new.status = old.status THEN
    IF new.answer_sdp IS DISTINCT FROM old.answer_sdp
       OR new.connected_at IS DISTINCT FROM old.connected_at
       OR new.ended_at IS DISTINCT FROM old.ended_at
       OR new.ended_by IS DISTINCT FROM old.ended_by THEN
      RAISE EXCEPTION 'Call state fields require a valid status transition';
    END IF;
  ELSIF new.status = 'accepted' THEN
    IF old.status <> 'ringing' OR v_me <> old.callee_id THEN
      RAISE EXCEPTION 'Only callee can accept a ringing call';
    END IF;
    IF new.answer_sdp IS NULL OR octet_length(new.answer_sdp) NOT BETWEEN 1 AND 131072 THEN
      RAISE EXCEPTION 'A valid bounded answer SDP is required';
    END IF;
    IF new.connected_at IS DISTINCT FROM old.connected_at
       OR new.ended_at IS DISTINCT FROM old.ended_at
       OR new.ended_by IS DISTINCT FROM old.ended_by THEN
      RAISE EXCEPTION 'Accepting a call cannot forge transport or end timestamps';
    END IF;
  ELSIF new.status = 'declined' THEN
    IF old.status <> 'ringing' OR v_me <> old.callee_id THEN
      RAISE EXCEPTION 'Only callee can decline a ringing call';
    END IF;
    IF new.answer_sdp IS DISTINCT FROM old.answer_sdp
       OR new.connected_at IS DISTINCT FROM old.connected_at
       OR new.ended_at IS NULL
       OR new.ended_by IS DISTINCT FROM v_me THEN
      RAISE EXCEPTION 'Invalid declined-call state';
    END IF;
  ELSIF new.status = 'ended' THEN
    IF old.status NOT IN ('ringing', 'accepted') THEN
      RAISE EXCEPTION 'Only active calls can be ended';
    END IF;
    IF new.answer_sdp IS DISTINCT FROM old.answer_sdp
       OR new.connected_at IS DISTINCT FROM old.connected_at
       OR new.ended_at IS NULL
       OR new.ended_by IS DISTINCT FROM v_me THEN
      RAISE EXCEPTION 'Invalid ended-call state';
    END IF;
  ELSIF new.status = 'failed' THEN
    IF old.status NOT IN ('ringing', 'accepted') THEN
      RAISE EXCEPTION 'Only active calls can fail';
    END IF;
    IF new.answer_sdp IS DISTINCT FROM old.answer_sdp
       OR new.connected_at IS DISTINCT FROM old.connected_at
       OR new.ended_at IS NULL
       OR new.ended_by IS DISTINCT FROM v_me THEN
      RAISE EXCEPTION 'Invalid failed-call state';
    END IF;
  ELSE
    RAISE EXCEPTION 'Invalid call status transition';
  END IF;

  new.updated_at := now();
  RETURN new;
END;
$function$;

CREATE OR REPLACE FUNCTION public.accept_call_session(p_call_id uuid, p_answer_sdp text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;
  IF p_answer_sdp IS NULL OR octet_length(p_answer_sdp) NOT BETWEEN 1 AND 131072 THEN
    RAISE EXCEPTION 'invalid answer SDP';
  END IF;

  UPDATE public.call_sessions
  SET status = 'accepted',
      answer_sdp = p_answer_sdp,
      updated_at = now()
  WHERE id = p_call_id
    AND callee_id = auth.uid()
    AND status = 'ringing';

  IF NOT FOUND THEN RAISE EXCEPTION 'Call is not available to accept'; END IF;
END;
$function$;

CREATE OR REPLACE FUNCTION public.mark_status_viewed(p_status_id uuid, p_hide_view boolean DEFAULT false)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_me uuid := auth.uid();
  v_owner uuid;
  v_hide boolean := false;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'authentication required'; END IF;

  SELECT s.user_id
    INTO v_owner
  FROM public.status_updates s
  WHERE s.id = p_status_id
    AND s.expires_at > now()
    AND s.deleted_at IS NULL
    AND (
      s.user_id = v_me
      OR EXISTS (
        SELECT 1
        FROM public.conversation_members me
        JOIN public.conversation_members owner
          ON owner.conversation_id = me.conversation_id
        WHERE me.user_id = v_me
          AND owner.user_id = s.user_id
      )
    );

  IF v_owner IS NULL THEN
    RAISE EXCEPTION 'status is not available to this user';
  END IF;
  IF v_owner = v_me THEN RETURN; END IF;

  SELECT coalesce(
    (settings->'gbFeatures'->>'yoHideStatViewV2')::boolean,
    (settings->'privacy'->>'hideViewStatus')::boolean,
    false
  )
  INTO v_hide
  FROM public.user_feature_settings
  WHERE user_id = v_me;

  IF coalesce(v_hide, false) OR p_hide_view THEN RETURN; END IF;

  INSERT INTO public.status_views(status_id, viewer_id, viewed_at)
  VALUES (p_status_id, v_me, now())
  ON CONFLICT(status_id, viewer_id)
  DO UPDATE SET viewed_at = excluded.viewed_at;
END;
$function$;
