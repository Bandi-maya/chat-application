-- Status view events: the server-side record backing "Notify when a contact
-- views your story" (notification.notifyStatusViewed) and the hide-your-view
-- privacy controls (privacy.hideViewStatus / yoHideStatViewV2).
-- Mirrors the production schema used by the Flutter phase and is intentionally
-- idempotent so a restored/self-hosted Chaty database can apply it.

create table if not exists public.status_view_events (
  id uuid not null default gen_random_uuid(),
  status_id uuid not null references public.status_updates(id) on delete cascade,
  viewer_id uuid not null references public.profiles(id) on delete cascade,
  status_owner_id uuid not null references public.profiles(id) on delete cascade,
  -- When the VIEWER hides their visit, owners must neither list nor be
  -- notified about this row (RLS below excludes it from owner reads and the
  -- realtime publication respects RLS).
  hidden boolean not null default false,
  viewed_at timestamptz not null default now(),
  primary key(id),
  unique(status_id,viewer_id),
  constraint status_view_not_self check(viewer_id<>status_owner_id)
);

create index if not exists status_view_events_owner_idx on public.status_view_events(status_owner_id,viewed_at desc);
create index if not exists status_view_events_status_idx on public.status_view_events(status_id);

alter table public.status_view_events enable row level security;

drop policy if exists status_view_events_owner_select on public.status_view_events;
create policy status_view_events_owner_select on public.status_view_events for select to authenticated
using(status_owner_id=(select auth.uid()) and not hidden);

revoke all on public.status_view_events from anon,authenticated;

-- Same signature the Flutter client already calls (p_status_id/p_hide_view).
create or replace function public.mark_status_viewed(p_status_id uuid,p_hide_view boolean default false)
returns void language plpgsql security definer set search_path to 'public' as $$
declare v_owner uuid;
begin
  if auth.uid() is null then raise exception 'authentication required'; end if;
  select user_id into v_owner from public.status_updates where id=p_status_id;
  if v_owner is null or v_owner=auth.uid() then return; end if;
  insert into public.status_view_events(status_id,viewer_id,status_owner_id,hidden)
  values(p_status_id,auth.uid(),v_owner,coalesce(p_hide_view,false))
  on conflict(status_id,viewer_id) do update set
    hidden=excluded.hidden,
    viewed_at=now();
end; $$;
revoke all on function public.mark_status_viewed(uuid,boolean) from public,anon;
grant execute on function public.mark_status_viewed(uuid,boolean) to authenticated;

do $$ begin
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='status_view_events') then
    alter publication supabase_realtime add table public.status_view_events;
  end if;
end $$;
