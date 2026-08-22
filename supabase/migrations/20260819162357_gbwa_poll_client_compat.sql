create or replace function public.get_conversation_messages(p_conversation_id uuid, p_limit integer default 100, p_before timestamptz default null)
returns table(id uuid, conversation_id uuid, sender_id uuid, type text, body text, metadata jsonb, created_at timestamptz, edited_at timestamptz, deleted_at timestamptz, reactions jsonb, is_starred boolean, is_pinned boolean, is_hidden boolean, is_read_by_other boolean)
language plpgsql stable security definer set search_path=public as $$
declare v_anti_delete boolean:=false;
begin
 if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
 select coalesce((settings->'gbFeatures'->>'yoAntiRevoke')::boolean,(settings->'privacy'->>'antiDeleteMessages')::boolean,false)
 into v_anti_delete from public.user_feature_settings where user_id=auth.uid();
 return query
 select m.id,m.conversation_id,m.sender_id,m.type,
   case when m.type='poll' then '[POLL] '||m.body else m.body end,
   (case when m.type='poll' then m.metadata||jsonb_build_object('poll',(select jsonb_build_object(
      'id',p.id,'question',p.question,'allow_multiple',p.allow_multiple,
      'total_votes',(select count(*) from public.poll_votes pv where pv.poll_id=p.id),
      'options',coalesce((select jsonb_agg(jsonb_build_object('id',po.id,'label',po.label,'position',po.position,'votes',(select count(*) from public.poll_votes pv where pv.option_id=po.id),'voted_by_me',exists(select 1 from public.poll_votes pv where pv.option_id=po.id and pv.user_id=auth.uid())) order by po.position) from public.poll_options po where po.poll_id=p.id),'[]'::jsonb)
   ) from public.polls p where p.message_id=m.id)) else m.metadata end)
   ||case when m.deleted_at is not null and v_anti_delete then jsonb_build_object('anti_deleted',true,'deleted_original_at',m.deleted_at) else '{}'::jsonb end,
   m.created_at,m.edited_at,case when v_anti_delete then null else m.deleted_at end,
   coalesce((select jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) from (select mr.emoji,array_agg(mr.user_id) user_ids from public.message_reactions mr where mr.message_id=m.id group by mr.emoji)x),'[]'::jsonb),
   coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
   exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null)
 from public.messages m left join public.message_user_state mus on mus.message_id=m.id and mus.user_id=auth.uid()
 where m.conversation_id=p_conversation_id and (p_before is null or m.created_at<p_before)
 order by m.created_at desc limit greatest(1,least(coalesce(p_limit,100),200));
end; $$;
revoke all on function public.get_conversation_messages(uuid,integer,timestamptz) from public,anon;
grant execute on function public.get_conversation_messages(uuid,integer,timestamptz) to authenticated;
