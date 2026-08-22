create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  message_id uuid references public.messages(id) on delete set null,
  reason text not null default 'user_report',
  created_at timestamptz not null default now()
);
alter table public.reports enable row level security;
drop policy if exists reports_insert_self_member on public.reports;
create policy reports_insert_self_member on public.reports for insert to authenticated with check (
  reporter_id=auth.uid() and public.is_conversation_member(conversation_id)
);
grant insert on public.reports to authenticated;
