create or replace function public.get_conversation_messages(p_conversation_id uuid, p_limit integer default 100, p_before timestamptz default null)
returns table(id uuid, conversation_id uuid, sender_id uuid, type text, body text, metadata jsonb, created_at timestamptz, edited_at timestamptz, deleted_at timestamptz, reactions jsonb, is_starred boolean, is_pinned boolean, is_hidden boolean, is_read_by_other boolean)
language plpgsql stable security definer set search_path=public as $$
declare
  v_anti_delete boolean := false;
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select coalesce((settings->'gbFeatures'->>'yoAntiRevoke')::boolean, (settings->'privacy'->>'antiDeleteMessages')::boolean, false)
    into v_anti_delete from public.user_feature_settings where user_id=auth.uid();
  return query
  select m.id,m.conversation_id,m.sender_id,m.type,m.body,
    (case when m.type='poll' then
      m.metadata || jsonb_build_object(
        'poll',(
          select jsonb_build_object(
            'id',p.id,'question',p.question,'allow_multiple',p.allow_multiple,
            'total_votes',(select count(*) from public.poll_votes pv where pv.poll_id=p.id),
            'options',coalesce((
              select jsonb_agg(jsonb_build_object(
                'id',po.id,'label',po.label,'position',po.position,
                'votes',(select count(*) from public.poll_votes pv where pv.option_id=po.id),
                'voted_by_me',exists(select 1 from public.poll_votes pv where pv.option_id=po.id and pv.user_id=auth.uid())
              ) order by po.position)
              from public.poll_options po where po.poll_id=p.id
            ),'[]'::jsonb)
          ) from public.polls p where p.message_id=m.id
        )
      )
    else m.metadata end)
    || case when m.deleted_at is not null and v_anti_delete
       then jsonb_build_object('anti_deleted',true,'deleted_original_at',m.deleted_at)
       else '{}'::jsonb end,
    m.created_at,m.edited_at,
    case when v_anti_delete then null else m.deleted_at end,
    coalesce((select jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) from (
      select mr.emoji,array_agg(mr.user_id) user_ids from public.message_reactions mr where mr.message_id=m.id group by mr.emoji
    )x),'[]'::jsonb),
    coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
    exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null)
  from public.messages m
  left join public.message_user_state mus on mus.message_id=m.id and mus.user_id=auth.uid()
  where m.conversation_id=p_conversation_id and (p_before is null or m.created_at<p_before)
  order by m.created_at desc limit greatest(1,least(coalesce(p_limit,100),200));
end; $$;
revoke all on function public.get_conversation_messages(uuid,integer,timestamptz) from public, anon;
grant execute on function public.get_conversation_messages(uuid,integer,timestamptz) to authenticated;

create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void language plpgsql security definer set search_path=public as $$
declare
  v_hide_seen boolean := false;
  v_blue_reply boolean := false;
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  select
    coalesce((settings->'gbFeatures'->>'yoHideSeen')::boolean, not coalesce((settings->'privacy'->>'readReceipts')::boolean,true), false),
    coalesce((settings->'gbFeatures'->>'yoBlueOnReply')::boolean, (settings->'privacy'->>'showBlueTicksAfterReply')::boolean, false)
  into v_hide_seen,v_blue_reply
  from public.user_feature_settings where user_id=auth.uid();

  update public.conversation_members set unread_count=0,last_read_at=now()
    where conversation_id=p_conversation_id and user_id=auth.uid();

  if not coalesce(v_hide_seen,false) and not coalesce(v_blue_reply,false) then
    insert into public.message_receipts(message_id,user_id,read_at)
    select m.id,auth.uid(),now() from public.messages m
    where m.conversation_id=p_conversation_id and m.sender_id<>auth.uid()
    on conflict(message_id,user_id) do update set read_at=excluded.read_at;
  end if;
end; $$;
revoke all on function public.mark_conversation_read(uuid) from public, anon;
grant execute on function public.mark_conversation_read(uuid) to authenticated;

create or replace function public.send_message(p_conversation_id uuid,p_client_message_id uuid,p_body text,p_type text default 'text',p_metadata jsonb default '{}'::jsonb)
returns uuid language plpgsql security definer set search_path=public as $$
declare
  v_me uuid:=auth.uid();
  v_id uuid;
  v_body text:=coalesce(p_body,'');
  v_kind text;
  v_other uuid;
  v_pause boolean:=false;
  v_hide_seen boolean:=false;
  v_blue_reply boolean:=false;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if p_type not in('text','image','video','audio','document','location','contact','task','system','poll') then raise exception 'unsupported message type'; end if;
  if p_type='text' and trim(v_body)='' then raise exception 'message body is required'; end if;
  if char_length(v_body)>10000 then raise exception 'message too long'; end if;

  select kind into v_kind from public.conversations where id=p_conversation_id;
  if v_kind='direct' then
    select cm.user_id into v_other from public.conversation_members cm
      where cm.conversation_id=p_conversation_id and cm.user_id<>v_me limit 1;
    if v_other is not null and public.is_blocked_pair(v_me,v_other) then raise exception 'message blocked by privacy settings'; end if;
  end if;

  select
    coalesce((settings->'gbFeatures'->>'yo_want_airplanemode')::boolean,false) or coalesce((settings->'gbFeatures'->>'key_notsend_msg')::boolean,false),
    coalesce((settings->'gbFeatures'->>'yoHideSeen')::boolean, not coalesce((settings->'privacy'->>'readReceipts')::boolean,true), false),
    coalesce((settings->'gbFeatures'->>'yoBlueOnReply')::boolean, (settings->'privacy'->>'showBlueTicksAfterReply')::boolean, false)
  into v_pause,v_hide_seen,v_blue_reply
  from public.user_feature_settings where user_id=v_me;
  if coalesce(v_pause,false) then raise exception 'sending is paused by Chaty airplane/privacy mode'; end if;

  insert into public.messages(conversation_id,sender_id,client_message_id,type,body,metadata)
  values(p_conversation_id,v_me,p_client_message_id,p_type,v_body,coalesce(p_metadata,'{}'::jsonb))
  on conflict(sender_id,client_message_id) do update set client_message_id=excluded.client_message_id returning id into v_id;
  update public.conversations set updated_at=now() where id=p_conversation_id;
  update public.conversation_members set unread_count=unread_count+1 where conversation_id=p_conversation_id and user_id<>v_me;

  if coalesce(v_blue_reply,false) and not coalesce(v_hide_seen,false) then
    insert into public.message_receipts(message_id,user_id,read_at)
    select m.id,v_me,now() from public.messages m
    where m.conversation_id=p_conversation_id and m.sender_id<>v_me
    on conflict(message_id,user_id) do update set read_at=excluded.read_at;
  end if;
  return v_id;
end; $$;
revoke all on function public.send_message(uuid,uuid,text,text,jsonb) from public, anon;
grant execute on function public.send_message(uuid,uuid,text,text,jsonb) to authenticated;

create or replace function public.enforce_profile_presence_preferences()
returns trigger language plpgsql security definer set search_path=public as $$
declare
  v_settings jsonb;
  v_freeze boolean:=false;
  v_ghost boolean:=false;
  v_always boolean:=false;
  v_airplane boolean:=false;
begin
  select settings into v_settings from public.user_feature_settings where user_id=new.id;
  v_freeze := coalesce((v_settings->'privacy'->>'freezeLastSeen')::boolean,false);
  v_ghost := coalesce((v_settings->'home'->>'ghostMode')::boolean,false) or coalesce((v_settings->'gbFeatures'->>'yo_want_ghostmode')::boolean,false);
  v_always := coalesce((v_settings->'gbFeatures'->>'always_online')::boolean,false);
  v_airplane := coalesce((v_settings->'home'->>'airplaneModeSimulator')::boolean,false) or coalesce((v_settings->'gbFeatures'->>'yo_want_airplanemode')::boolean,false);

  if v_freeze or v_ghost then new.last_seen_at := old.last_seen_at; end if;
  if v_airplane or v_ghost then
    new.presence := 'offline';
  elsif v_always then
    new.presence := 'online';
  end if;
  return new;
end; $$;
drop trigger if exists profiles_enforce_presence_preferences on public.profiles;
create trigger profiles_enforce_presence_preferences before update of presence,last_seen_at on public.profiles
for each row execute function public.enforce_profile_presence_preferences();
