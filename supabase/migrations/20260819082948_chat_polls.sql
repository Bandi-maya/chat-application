alter table public.messages drop constraint if exists messages_type_check;
alter table public.messages add constraint messages_type_check check (
  type in ('text','image','video','audio','document','location','contact','poll','task','system')
);

create table if not exists public.polls (
  id uuid primary key default gen_random_uuid(),
  message_id uuid not null unique references public.messages(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  creator_id uuid not null references auth.users(id) on delete restrict,
  question text not null check (char_length(question) between 1 and 300),
  allow_multiple boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.poll_options (
  id uuid primary key default gen_random_uuid(),
  poll_id uuid not null references public.polls(id) on delete cascade,
  position integer not null check (position >= 0),
  label text not null check (char_length(label) between 1 and 160),
  unique (poll_id, position)
);

create table if not exists public.poll_votes (
  poll_id uuid not null references public.polls(id) on delete cascade,
  option_id uuid not null references public.poll_options(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (poll_id, option_id, user_id)
);
create index if not exists poll_votes_poll_idx on public.poll_votes(poll_id);
create index if not exists poll_votes_user_idx on public.poll_votes(user_id,poll_id);

alter table public.polls enable row level security;
alter table public.poll_options enable row level security;
alter table public.poll_votes enable row level security;

drop policy if exists polls_select_members on public.polls;
create policy polls_select_members on public.polls for select to authenticated using (
  public.is_conversation_member(conversation_id)
);

drop policy if exists poll_options_select_members on public.poll_options;
create policy poll_options_select_members on public.poll_options for select to authenticated using (
  exists(select 1 from public.polls p where p.id=poll_id and public.is_conversation_member(p.conversation_id))
);

drop policy if exists poll_votes_select_members on public.poll_votes;
create policy poll_votes_select_members on public.poll_votes for select to authenticated using (
  exists(select 1 from public.polls p where p.id=poll_id and public.is_conversation_member(p.conversation_id))
);

create or replace function public.create_poll(
  p_conversation_id uuid,
  p_client_message_id uuid,
  p_question text,
  p_options text[],
  p_allow_multiple boolean default false
)
returns uuid
language plpgsql
security definer
set search_path=public
as $$
declare
  v_me uuid:=auth.uid();
  v_message_id uuid;
  v_poll_id uuid;
  v_label text;
  v_position integer:=0;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  if char_length(trim(p_question)) not between 1 and 300 then raise exception 'poll question must be 1 to 300 characters'; end if;
  if coalesce(array_length(p_options,1),0) < 2 or array_length(p_options,1) > 12 then raise exception 'poll requires 2 to 12 options'; end if;

  v_message_id:=public.send_message(
    p_conversation_id,
    p_client_message_id,
    trim(p_question),
    'poll',
    jsonb_build_object('poll_question',trim(p_question))
  );

  select id into v_poll_id from public.polls where message_id=v_message_id;
  if v_poll_id is not null then return v_message_id; end if;

  insert into public.polls(message_id,conversation_id,creator_id,question,allow_multiple)
  values(v_message_id,p_conversation_id,v_me,trim(p_question),coalesce(p_allow_multiple,false))
  returning id into v_poll_id;

  foreach v_label in array p_options loop
    v_label:=trim(v_label);
    if char_length(v_label) not between 1 and 160 then raise exception 'poll option must be 1 to 160 characters'; end if;
    insert into public.poll_options(poll_id,position,label) values(v_poll_id,v_position,v_label);
    v_position:=v_position+1;
  end loop;
  return v_message_id;
end;
$$;
revoke all on function public.create_poll(uuid,uuid,text,text[],boolean) from public;
grant execute on function public.create_poll(uuid,uuid,text,text[],boolean) to authenticated;

create or replace function public.vote_poll(p_message_id uuid,p_option_id uuid)
returns void
language plpgsql
security definer
set search_path=public
as $$
declare
  v_me uuid:=auth.uid();
  v_poll public.polls%rowtype;
  v_exists boolean;
begin
  if v_me is null then raise exception 'authentication required'; end if;
  select * into v_poll from public.polls where message_id=p_message_id;
  if v_poll.id is null then raise exception 'poll not found'; end if;
  if not public.is_conversation_member(v_poll.conversation_id) then raise exception 'not authorized'; end if;
  if not exists(select 1 from public.poll_options where id=p_option_id and poll_id=v_poll.id) then raise exception 'invalid poll option'; end if;

  select exists(select 1 from public.poll_votes where poll_id=v_poll.id and option_id=p_option_id and user_id=v_me) into v_exists;
  if v_exists then
    delete from public.poll_votes where poll_id=v_poll.id and option_id=p_option_id and user_id=v_me;
    return;
  end if;

  if not v_poll.allow_multiple then
    delete from public.poll_votes where poll_id=v_poll.id and user_id=v_me;
  end if;
  insert into public.poll_votes(poll_id,option_id,user_id) values(v_poll.id,p_option_id,v_me) on conflict do nothing;
end;
$$;
revoke all on function public.vote_poll(uuid,uuid) from public;
grant execute on function public.vote_poll(uuid,uuid) to authenticated;

create or replace function public.get_conversation_messages(p_conversation_id uuid,p_limit integer default 100,p_before timestamptz default null)
returns table(id uuid,conversation_id uuid,sender_id uuid,type text,body text,metadata jsonb,created_at timestamptz,edited_at timestamptz,deleted_at timestamptz,reactions jsonb,is_starred boolean,is_pinned boolean,is_hidden boolean,is_read_by_other boolean)
language plpgsql stable security definer set search_path=public as $$
begin
  if not public.is_conversation_member(p_conversation_id) then raise exception 'not authorized'; end if;
  return query select m.id,m.conversation_id,m.sender_id,m.type,m.body,
    case when m.type='poll' then
      m.metadata || jsonb_build_object(
        'poll',(
          select jsonb_build_object(
            'id',p.id,
            'question',p.question,
            'allow_multiple',p.allow_multiple,
            'total_votes',(select count(*) from public.poll_votes pv where pv.poll_id=p.id),
            'options',coalesce((
              select jsonb_agg(jsonb_build_object(
                'id',po.id,
                'label',po.label,
                'position',po.position,
                'votes',(select count(*) from public.poll_votes pv where pv.option_id=po.id),
                'voted_by_me',exists(select 1 from public.poll_votes pv where pv.option_id=po.id and pv.user_id=auth.uid())
              ) order by po.position)
              from public.poll_options po where po.poll_id=p.id
            ),'[]'::jsonb)
          ) from public.polls p where p.message_id=m.id
        )
      )
    else m.metadata end,
    m.created_at,m.edited_at,m.deleted_at,
    coalesce((select jsonb_agg(jsonb_build_object('emoji',x.emoji,'user_ids',x.user_ids)) from (select mr.emoji,array_agg(mr.user_id) user_ids from public.message_reactions mr where mr.message_id=m.id group by mr.emoji)x),'[]'::jsonb),
    coalesce(mus.is_starred,false),coalesce(mus.is_pinned,false),coalesce(mus.is_hidden,false),
    exists(select 1 from public.message_receipts rec where rec.message_id=m.id and rec.user_id<>m.sender_id and rec.read_at is not null)
  from public.messages m left join public.message_user_state mus on mus.message_id=m.id and mus.user_id=auth.uid()
  where m.conversation_id=p_conversation_id and(p_before is null or m.created_at<p_before)
  order by m.created_at desc limit greatest(1,least(coalesce(p_limit,100),200));
end;
$$;
revoke all on function public.get_conversation_messages(uuid,integer,timestamptz) from public;
grant execute on function public.get_conversation_messages(uuid,integer,timestamptz) to authenticated;

-- Realtime reconciliation for poll vote changes.
do $$ begin
  begin alter publication supabase_realtime add table public.poll_votes; exception when duplicate_object then null; end;
end $$;
