create index if not exists contact_connections_user_high_idx on public.contact_connections(user_high_id);
create index if not exists contact_privacy_target_idx on public.contact_privacy_overrides(target_user_id);

-- Recreate only the newly-added policies using init-plan friendly auth.uid() lookups.
drop policy if exists contact_privacy_select_self on public.contact_privacy_overrides;
create policy contact_privacy_select_self on public.contact_privacy_overrides for select to authenticated using (owner_user_id = (select auth.uid()));
drop policy if exists contact_privacy_insert_self on public.contact_privacy_overrides;
create policy contact_privacy_insert_self on public.contact_privacy_overrides for insert to authenticated with check (owner_user_id = (select auth.uid()));
drop policy if exists contact_privacy_update_self on public.contact_privacy_overrides;
create policy contact_privacy_update_self on public.contact_privacy_overrides for update to authenticated using (owner_user_id = (select auth.uid())) with check (owner_user_id = (select auth.uid()));
drop policy if exists contact_privacy_delete_self on public.contact_privacy_overrides;
create policy contact_privacy_delete_self on public.contact_privacy_overrides for delete to authenticated using (owner_user_id = (select auth.uid()));

drop policy if exists contact_connections_select_participant on public.contact_connections;
create policy contact_connections_select_participant on public.contact_connections for select to authenticated using ((select auth.uid()) = user_low_id or (select auth.uid()) = user_high_id);

drop policy if exists linked_devices_manage_self on public.linked_devices;
create policy linked_devices_manage_self on public.linked_devices for all to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- New read-only status RPC needs no elevated privileges because RLS already scopes the row.
create or replace function public.get_contact_connection_status(p_other_user_id uuid)
returns table(conversation_id uuid, my_accepted boolean, other_accepted boolean, calls_allowed boolean)
language sql
stable
security invoker
set search_path to 'public'
as $function$
  select cc.conversation_id,
    case when (select auth.uid())=cc.user_low_id then cc.low_accepted else cc.high_accepted end,
    case when (select auth.uid())=cc.user_low_id then cc.high_accepted else cc.low_accepted end,
    cc.low_accepted and cc.high_accepted
  from public.contact_connections cc
  where (cc.user_low_id=(select auth.uid()) and cc.user_high_id=p_other_user_id)
     or (cc.user_high_id=(select auth.uid()) and cc.user_low_id=p_other_user_id)
  limit 1;
$function$;

-- Delivery and activity writes can run under the caller's RLS rights.
alter function public.mark_conversation_delivered(uuid) security invoker;
alter function public.set_typing_state(uuid,boolean) security invoker;
alter function public.set_recording_state(uuid,boolean) security invoker;

-- Keep privileged connection mutation out of the exposed public schema.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.accept_contact_connection(p_other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path to 'public','private'
as $function$
declare
  v_me uuid := auth.uid();
  v_conversation_id uuid;
  v_low uuid;
  v_high uuid;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if p_other_user_id = v_me then raise exception 'invalid contact'; end if;
  v_conversation_id := public.create_direct_conversation(p_other_user_id);
  if v_me::text < p_other_user_id::text then v_low := v_me; v_high := p_other_user_id; else v_low := p_other_user_id; v_high := v_me; end if;
  update public.contact_connections set
    low_accepted = case when v_me=v_low then true else low_accepted end,
    high_accepted = case when v_me=v_high then true else high_accepted end,
    updated_at = now()
  where user_low_id=v_low and user_high_id=v_high;
  return v_conversation_id;
end;
$function$;
revoke all on function private.accept_contact_connection(uuid) from public, anon;
grant execute on function private.accept_contact_connection(uuid) to authenticated;

create or replace function public.accept_contact_connection(p_other_user_id uuid)
returns uuid
language sql
security invoker
set search_path to 'public','private'
as $function$
  select private.accept_contact_connection(p_other_user_id);
$function$;
revoke all on function public.accept_contact_connection(uuid) from public, anon;
grant execute on function public.accept_contact_connection(uuid) to authenticated;
