create table if not exists public.user_e2ee_keys (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  public_key text not null,
  created_at timestamptz not null default now(),
  revoked_at timestamptz
);
create index if not exists user_e2ee_keys_user_active_idx on public.user_e2ee_keys(user_id, created_at desc) where revoked_at is null;
alter table public.user_e2ee_keys enable row level security;

drop policy if exists "authenticated read e2ee public keys" on public.user_e2ee_keys;
create policy "authenticated read e2ee public keys" on public.user_e2ee_keys for select to authenticated using (true);
drop policy if exists "users create own e2ee keys" on public.user_e2ee_keys;
create policy "users create own e2ee keys" on public.user_e2ee_keys for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "users revoke own e2ee keys" on public.user_e2ee_keys;
create policy "users revoke own e2ee keys" on public.user_e2ee_keys for update to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.conversation_key_versions (
  id uuid primary key,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists conversation_key_versions_conversation_idx on public.conversation_key_versions(conversation_id, created_at desc);
alter table public.conversation_key_versions enable row level security;
drop policy if exists "members read e2ee key versions" on public.conversation_key_versions;
create policy "members read e2ee key versions" on public.conversation_key_versions for select to authenticated using (public.is_conversation_member(conversation_id));
drop policy if exists "members create e2ee key versions" on public.conversation_key_versions;
create policy "members create e2ee key versions" on public.conversation_key_versions for insert to authenticated with check (auth.uid() = created_by and public.is_conversation_member(conversation_id));

create table if not exists public.conversation_key_envelopes (
  key_version_id uuid not null references public.conversation_key_versions(id) on delete cascade,
  recipient_key_id uuid not null references public.user_e2ee_keys(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  ephemeral_public_key text not null,
  nonce text not null,
  mac text not null,
  encrypted_key text not null,
  created_at timestamptz not null default now(),
  primary key (key_version_id, recipient_key_id)
);
create index if not exists conversation_key_envelopes_user_idx on public.conversation_key_envelopes(user_id, key_version_id);
alter table public.conversation_key_envelopes enable row level security;
drop policy if exists "members read e2ee envelopes" on public.conversation_key_envelopes;
create policy "members read e2ee envelopes" on public.conversation_key_envelopes for select to authenticated using (
  exists (select 1 from public.conversation_key_versions v where v.id=key_version_id and public.is_conversation_member(v.conversation_id))
);
drop policy if exists "members create e2ee envelopes" on public.conversation_key_envelopes;
create policy "members create e2ee envelopes" on public.conversation_key_envelopes for insert to authenticated with check (
  exists (
    select 1 from public.conversation_key_versions v
    where v.id=key_version_id
      and public.is_conversation_member(v.conversation_id)
      and exists (select 1 from public.conversation_members cm where cm.conversation_id=v.conversation_id and cm.user_id=user_id)
      and exists (select 1 from public.user_e2ee_keys k where k.id=recipient_key_id and k.user_id=user_id and k.revoked_at is null)
  )
);

revoke all on public.user_e2ee_keys from anon;
revoke all on public.conversation_key_versions from anon;
revoke all on public.conversation_key_envelopes from anon;
grant select,insert,update on public.user_e2ee_keys to authenticated;
grant select,insert on public.conversation_key_versions to authenticated;
grant select,insert on public.conversation_key_envelopes to authenticated;
