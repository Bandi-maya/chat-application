create or replace function public.get_conversation_members(p_conversation_id uuid)
returns table(id uuid, username text, display_name text, avatar_url text, about text, avatar_initials text, avatar_color_hex text, presence text, last_seen_at timestamptz, is_verified boolean, role text)
language plpgsql
stable
security definer
set search_path to 'public'
as $function$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,
    case when p.id=auth.uid() then p.presence else coalesce(cpv.presence,'offline') end,
    case when p.id=auth.uid() then p.last_seen_at else cpv.last_seen_at end,
    p.is_verified,cm.role
  from public.conversation_members cm
  join public.profiles p on p.id=cm.user_id
  left join public.contact_presence_visibility cpv
    on cpv.owner_user_id=p.id and cpv.viewer_user_id=auth.uid()
  where cm.conversation_id=p_conversation_id
  order by case cm.role when 'owner' then 0 when 'admin' then 1 else 2 end,cm.joined_at;
end;
$function$;

create or replace function public.search_profiles(p_query text)
returns table(id uuid, username text, display_name text, avatar_url text, about text, avatar_initials text, avatar_color_hex text, presence text, last_seen_at timestamptz, is_verified boolean)
language sql
stable
security definer
set search_path to 'public'
as $function$
  select p.id,p.username,p.display_name,p.avatar_url,p.about,p.avatar_initials,p.avatar_color_hex,
    'offline'::text as presence,
    null::timestamptz as last_seen_at,
    p.is_verified
  from public.profiles p
  where auth.uid() is not null and p.id<>auth.uid() and char_length(trim(p_query))>=2
    and (strpos(lower(p.username),lower(trim(leading '@' from trim(p_query))))>0 or strpos(lower(p.display_name),lower(trim(p_query)))>0)
  order by case when lower(p.username)=lower(trim(leading '@' from trim(p_query))) then 0 else 1 end,p.display_name limit 30;
$function$;
