drop index if exists public.idx_pair_invites_code_hash;

create unique index if not exists idx_pair_invites_unused_code_hash
  on public.pair_invites(code_hash)
  where used_at is null;

create or replace function public.create_pair_invite()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  current_user_id uuid;
  random_bytes bytea;
  raw_code text;
  invite_code_hash text;
  invite_expires_at timestamptz;
  inserted_invite_id uuid;
  attempt integer := 0;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into current_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null;

  if current_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  if exists (
    select 1
    from public.couples
    where couples.status = 'active'
      and couples.unlinked_at is null
      and current_user_id in (couples.user_a_id, couples.user_b_id)
  ) then
    raise exception 'already_paired';
  end if;

  delete from public.pair_invites
  where used_at is null
    and expires_at <= now();

  invite_expires_at := now() + interval '10 minutes';

  loop
    attempt := attempt + 1;
    random_bytes := extensions.gen_random_bytes(4);
    raw_code := lpad(
      (
        (
          get_byte(random_bytes, 0)::bigint * 16777216
          + get_byte(random_bytes, 1)::bigint * 65536
          + get_byte(random_bytes, 2)::bigint * 256
          + get_byte(random_bytes, 3)::bigint
        ) % 1000000
      )::text,
      6,
      '0'
    );
    invite_code_hash := encode(extensions.digest(raw_code, 'sha256'), 'hex');

    insert into public.pair_invites (code_hash, created_by, expires_at)
    values (invite_code_hash, current_user_id, invite_expires_at)
    on conflict (code_hash) where used_at is null do nothing
    returning pair_invites.id
    into inserted_invite_id;

    if inserted_invite_id is not null then
      exit;
    end if;

    if attempt >= 20 then
      raise exception 'invite_code_generation_failed';
    end if;
  end loop;

  return jsonb_build_object(
    'code', raw_code,
    'expiresAt', invite_expires_at
  );
end;
$$;

revoke execute on function public.create_pair_invite() from public;
revoke execute on function public.create_pair_invite() from anon;
grant execute on function public.create_pair_invite() to authenticated;
