revoke execute on function public.enforce_profile_presence_preferences() from public, anon, authenticated;
revoke execute on function public.handle_message_auto_reply() from public, anon, authenticated;
revoke execute on function public.is_blocked_pair(uuid,uuid) from public, anon, authenticated;

create or replace function public.mark_status_viewed(p_status_id uuid,p_hide_view boolean default false)
returns void language plpgsql security definer set search_path=public as $$
declare
  v_owner uuid;
  v_hide boolean:=false;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  select user_id into v_owner from public.status_updates where id=p_status_id and expires_at>now();
  if v_owner is null or v_owner=auth.uid() then return; end if;
  select coalesce((settings->'gbFeatures'->>'yoHideStatViewV2')::boolean,(settings->'privacy'->>'hideViewStatus')::boolean,false)
    into v_hide from public.user_feature_settings where user_id=auth.uid();
  if coalesce(v_hide,false) or p_hide_view then return; end if;
  insert into public.status_views(status_id,viewer_id,viewed_at) values(p_status_id,auth.uid(),now())
  on conflict(status_id,viewer_id) do update set viewed_at=excluded.viewed_at;
end; $$;
revoke all on function public.mark_status_viewed(uuid,boolean) from public,anon;
grant execute on function public.mark_status_viewed(uuid,boolean) to authenticated;
