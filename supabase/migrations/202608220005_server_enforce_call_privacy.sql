CREATE SCHEMA IF NOT EXISTS private;
REVOKE ALL ON SCHEMA private FROM PUBLIC;
GRANT USAGE ON SCHEMA private TO authenticated;

CREATE OR REPLACE FUNCTION private.call_allowed(p_caller uuid,p_callee uuid,p_conversation_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_audience text:='Everyone';
  v_exceptions jsonb:='[]'::jsonb;
  v_is_contact boolean:=false;
BEGIN
  IF p_caller IS NULL OR p_callee IS NULL OR p_caller=p_callee THEN RETURN false; END IF;
  IF EXISTS (
    SELECT 1 FROM public.blocked_users b
    WHERE (b.blocker_id=p_caller AND b.blocked_id=p_callee)
       OR (b.blocker_id=p_callee AND b.blocked_id=p_caller)
  ) THEN RETURN false; END IF;
  SELECT coalesce(nullif(ufs.settings->'privacy'->>'whoCanCallMe',''),'Everyone'),
         coalesce(ufs.settings->'privacy'->'whoCanCallMeExceptions','[]'::jsonb)
    INTO v_audience,v_exceptions
    FROM public.user_feature_settings ufs WHERE ufs.user_id=p_callee;
  IF NOT FOUND THEN v_audience:='Everyone'; v_exceptions:='[]'::jsonb; END IF;
  IF v_audience='Nobody' THEN RETURN false; END IF;
  SELECT EXISTS (
    SELECT 1 FROM public.contact_connections cc
    WHERE cc.conversation_id=p_conversation_id AND cc.low_accepted=true AND cc.high_accepted=true
      AND ((cc.user_low_id=p_caller AND cc.user_high_id=p_callee)
        OR (cc.user_high_id=p_caller AND cc.user_low_id=p_callee))
  ) INTO v_is_contact;
  IF v_audience IN ('My Contacts','My Contacts Except...') AND NOT v_is_contact THEN RETURN false; END IF;
  IF v_audience='My Contacts Except...' AND v_exceptions @> jsonb_build_array(p_caller::text) THEN RETURN false; END IF;
  RETURN true;
END;
$function$;

REVOKE ALL ON FUNCTION private.call_allowed(uuid,uuid,uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION private.call_allowed(uuid,uuid,uuid) FROM anon;
GRANT EXECUTE ON FUNCTION private.call_allowed(uuid,uuid,uuid) TO authenticated;

DROP POLICY IF EXISTS call_sessions_caller_insert ON public.call_sessions;
CREATE POLICY call_sessions_caller_insert ON public.call_sessions FOR INSERT TO authenticated WITH CHECK (
  caller_id=(SELECT auth.uid()) AND caller_id<>callee_id AND status='ringing'
  AND offer_sdp IS NOT NULL AND octet_length(offer_sdp) BETWEEN 1 AND 131072
  AND answer_sdp IS NULL AND connected_at IS NULL AND ended_at IS NULL AND ended_by IS NULL
  AND started_at BETWEEN now()-interval '5 minutes' AND now()+interval '5 minutes'
  AND created_at BETWEEN now()-interval '5 minutes' AND now()+interval '5 minutes'
  AND updated_at BETWEEN now()-interval '5 minutes' AND now()+interval '5 minutes'
  AND EXISTS (SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id=call_sessions.conversation_id AND cm.user_id=(SELECT auth.uid()))
  AND EXISTS (SELECT 1 FROM public.conversation_members cm WHERE cm.conversation_id=call_sessions.conversation_id AND cm.user_id=call_sessions.callee_id)
  AND private.call_allowed((SELECT auth.uid()),call_sessions.callee_id,call_sessions.conversation_id)
);
