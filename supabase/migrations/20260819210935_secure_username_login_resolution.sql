create or replace function public.resolve_login_email(p_identifier text, p_password text)
returns text
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
  v_email text;
begin
  if p_identifier is null or length(trim(p_identifier)) < 3 or p_password is null or length(p_password) < 1 then
    return null;
  end if;

  select u.email
    into v_email
  from public.profiles p
  join auth.users u on u.id = p.id
  where lower(p.username) = lower(trim(leading '@' from trim(p_identifier)))
    and u.encrypted_password is not null
    and u.encrypted_password = extensions.crypt(p_password, u.encrypted_password)
  limit 1;

  return v_email;
end;
$$;

revoke all on function public.resolve_login_email(text, text) from public;
grant execute on function public.resolve_login_email(text, text) to anon, authenticated;
