create unique index if not exists auto_reply_rules_user_rule_uidx on public.auto_reply_rules(user_id,lower(keyword),response_body);

create or replace function public.handle_message_auto_reply()
returns trigger language plpgsql security definer set search_path=public as $$
declare r record; v_kind text;
begin
  if coalesce((new.metadata->>'automation_generated')::boolean,false) then return new; end if;
  select kind into v_kind from public.conversations where id=new.conversation_id;
  for r in
    select ar.* from public.auto_reply_rules ar
    join public.conversation_members cm on cm.user_id=ar.user_id and cm.conversation_id=new.conversation_id
    left join public.user_feature_settings ufs on ufs.user_id=ar.user_id
    where ar.enabled and ar.user_id<>new.sender_id
      and coalesce((ufs.settings->'automation'->>'enableAutoReply')::boolean,false)
      and (ar.scope='all' or ar.scope=v_kind)
      and position(lower(ar.keyword) in lower(new.body))>0
      and not public.is_blocked_pair(ar.user_id,new.sender_id)
      and not exists(
        select 1 from public.auto_reply_log l where l.rule_id=ar.id and l.conversation_id=new.conversation_id
        and l.last_sent_at > now() - make_interval(secs=>ar.cooldown_seconds)
      )
  loop
    insert into public.messages(conversation_id,sender_id,client_message_id,type,body,metadata)
    values(new.conversation_id,r.user_id,gen_random_uuid(),'text',r.response_body,jsonb_build_object('automation_generated',true,'auto_reply_rule_id',r.id));
    insert into public.auto_reply_log(rule_id,conversation_id,last_sent_at)
    values(r.id,new.conversation_id,now())
    on conflict(rule_id,conversation_id) do update set last_sent_at=excluded.last_sent_at;
    update public.conversations set updated_at=now() where id=new.conversation_id;
    update public.conversation_members set unread_count=unread_count+1 where conversation_id=new.conversation_id and user_id<>r.user_id;
  end loop;
  return new;
end; $$;
