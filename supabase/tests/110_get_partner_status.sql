\set ON_ERROR_STOP on

begin;

create or replace function pg_temp.assert_eq(
  actual bigint,
  expected bigint,
  label text
)
returns void
language plpgsql
as $$
begin
  if actual is distinct from expected then
    raise exception '%: expected %, got %', label, expected, actual;
  end if;
end;
$$;

create or replace function pg_temp.assert_bool_eq(
  actual boolean,
  expected boolean,
  label text
)
returns void
language plpgsql
as $$
begin
  if actual is distinct from expected then
    raise exception '%: expected %, got %', label, expected, actual;
  end if;
end;
$$;

create or replace function pg_temp.assert_text_eq(
  actual text,
  expected text,
  label text
)
returns void
language plpgsql
as $$
begin
  if actual is distinct from expected then
    raise exception '%: expected %, got %', label, expected, actual;
  end if;
end;
$$;

create or replace function pg_temp.assert_jsonb_eq(
  actual jsonb,
  expected jsonb,
  label text
)
returns void
language plpgsql
as $$
begin
  if actual is distinct from expected then
    raise exception '%: expected %, got %', label, expected, actual;
  end if;
end;
$$;

create or replace function pg_temp.assert_timestamptz_eq(
  actual timestamptz,
  expected timestamptz,
  label text
)
returns void
language plpgsql
as $$
begin
  if actual is distinct from expected then
    raise exception '%: expected %, got %', label, expected, actual;
  end if;
end;
$$;

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'authenticated',
    'public.get_partner_status()',
    'execute'
  ),
  true,
  'authenticated can execute get_partner_status'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'anon',
    'public.get_partner_status()',
    'execute'
  ),
  false,
  'anon cannot execute get_partner_status'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'get_partner_status'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute get_partner_status'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc
    join pg_namespace on pg_namespace.oid = pg_proc.pronamespace
    where pg_namespace.nspname = 'public'
      and pg_proc.proname = 'get_partner_status'
      and pg_get_function_arguments(pg_proc.oid) = ''
      and pg_proc.prorettype = 'jsonb'::regtype
      and pg_proc.prosecdef
      and pg_proc.proconfig @> array['search_path=public, auth']
  ),
  1,
  'get_partner_status is a security definer jsonb RPC with fixed search_path'
);

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.get_partner_status();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'unauthenticated call succeeded, expected not_authenticated';
  end if;

  if message not like '%not_authenticated%' then
    raise exception 'unauthenticated call raised %, expected not_authenticated', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001109', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001109","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.get_partner_status();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'profile missing call succeeded, expected user_profile_not_found';
  end if;

  if message not like '%user_profile_not_found%' then
    raise exception 'profile missing call raised %, expected user_profile_not_found', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001101', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001101","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001102', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001102","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001103', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001103","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001104', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001104","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001105', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001105","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001106', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001106","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001107', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001107","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001108', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001108","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001110', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001110","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

update public.users
set
  nickname = 'partner-active',
  avatar_id = 'fox',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000001103';

update public.users
set
  nickname = 'partner-offline',
  avatar_id = 'dog',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000001105';

update public.users
set
  nickname = 'partner-expired',
  avatar_id = 'bear',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000001107';

update public.users
set
  nickname = 'partner-paused',
  avatar_id = 'rabbit',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000001110';

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001102',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001102'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001103'
  ),
  'active'
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = null,
  unlinked_by = null;

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001104',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001104'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001105'
  ),
  'active'
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = null,
  unlinked_by = null;

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001105',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001106'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001107'
  ),
  'active'
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = null,
  unlinked_by = null;

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001108',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001108'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001110'
  ),
  'active'
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = null,
  unlinked_by = null;

insert into public.current_status (
  user_id,
  couple_id,
  status_code,
  status_updated_at,
  expires_at,
  is_paused
)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001103'
  ),
  '20000000-0000-0000-0000-000000001102',
  'music',
  '2026-05-25 08:30:00+00'::timestamptz,
  now() + interval '10 minutes',
  false
);

insert into public.current_status (
  user_id,
  couple_id,
  status_code,
  status_updated_at,
  expires_at,
  is_paused
)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001107'
  ),
  '20000000-0000-0000-0000-000000001105',
  'social',
  '2026-05-25 09:00:00+00'::timestamptz,
  now() - interval '1 minute',
  false
);

insert into public.current_status (
  user_id,
  couple_id,
  status_code,
  status_updated_at,
  expires_at,
  is_paused
)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001110'
  ),
  '20000000-0000-0000-0000-000000001108',
  'paused',
  '2026-05-25 09:20:00+00'::timestamptz,
  now() + interval '10 minutes',
  true
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001101', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001101","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_jsonb_eq(
  public.get_partner_status(),
  '{"paired":false}'::jsonb,
  'get_partner_status returns exactly {paired:false} for unpaired users'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001102', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001102","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,isPaused,paired,partner,statusCode,statusUpdatedAt',
  'active partner payload returns expected top-level keys'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
  ),
  'avatarId,nickname',
  'active partner payload returns expected partner keys'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'active partner payload omits private and history fields at top level'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'active partner payload omits private and history fields in partner object'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'nickname'
  ),
  'partner-active',
  'active partner nickname is returned'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'avatarId'
  ),
  'fox',
  'active partner avatarId is returned'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->>'statusCode'
  ),
  'music',
  'active partner status code is returned'
);

select pg_temp.assert_timestamptz_eq(
  (
    select (
      public.get_partner_status()->>'statusUpdatedAt'
    )::timestamptz
  ),
  '2026-05-25 08:30:00+00'::timestamptz,
  'active partner statusUpdatedAt is returned'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'isPaused')::boolean
  ),
  false,
  'active partner isPaused is false'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'expiresAt') is not null
  ),
  true,
  'active partner expiresAt is returned'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001104', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001104","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,isPaused,paired,partner,statusCode,statusUpdatedAt',
  'missing partner status payload returns expected top-level keys'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
  ),
  'avatarId,nickname',
  'missing partner status payload returns expected partner keys'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'missing partner status payload omits private and history fields at top level'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'missing partner status payload omits private and history fields in partner object'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'nickname'
  ),
  'partner-offline',
  'missing partner status still returns partner nickname'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'avatarId'
  ),
  'dog',
  'missing partner status still returns partner avatarId'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->>'statusCode'
  ),
  'offline',
  'missing partner status maps to offline'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'isPaused')::boolean
  ),
  false,
  'missing partner status returns isPaused false'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'statusUpdatedAt') is null
  ),
  true,
  'missing partner status returns null statusUpdatedAt'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'expiresAt') is null
  ),
  true,
  'missing partner status returns null expiresAt'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001106', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001106","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,isPaused,paired,partner,statusCode,statusUpdatedAt',
  'expired partner status payload returns expected top-level keys'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
  ),
  'avatarId,nickname',
  'expired partner status payload returns expected partner keys'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'expired partner status payload omits private and history fields at top level'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'expired partner status payload omits private and history fields in partner object'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'nickname'
  ),
  'partner-expired',
  'expired partner status still returns partner nickname'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'avatarId'
  ),
  'bear',
  'expired partner status still returns partner avatarId'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->>'statusCode'
  ),
  'offline',
  'expired partner status maps to offline'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'isPaused')::boolean
  ),
  false,
  'expired partner status returns isPaused false'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'statusUpdatedAt') is null
  ),
  true,
  'expired partner status returns null statusUpdatedAt'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'expiresAt') is null
  ),
  true,
  'expired partner status returns null expiresAt'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001108', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001108","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,isPaused,paired,partner,statusCode,statusUpdatedAt',
  'paused partner payload returns expected top-level keys'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
  ),
  'avatarId,nickname',
  'paused partner payload returns expected partner keys'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'paused partner payload omits private and history fields at top level'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_partner_status() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload->'partner') as keys(key)
    where keys.key in (
      'package',
      'app',
      'duration',
      'content',
      'chat',
      'history',
      'auth',
      'id',
      'email',
      'phone',
      'token'
    )
  ),
  0,
  'paused partner payload omits private and history fields in partner object'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'nickname'
  ),
  'partner-paused',
  'paused partner nickname is returned'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->'partner'->>'avatarId'
  ),
  'rabbit',
  'paused partner avatarId is returned'
);

select pg_temp.assert_text_eq(
  (
    select public.get_partner_status()->>'statusCode'
  ),
  'paused',
  'paused partner status maps to paused'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'isPaused')::boolean
  ),
  true,
  'paused partner returns isPaused true'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'statusUpdatedAt') is null
  ),
  true,
  'paused partner returns null statusUpdatedAt'
);

select pg_temp.assert_bool_eq(
  (
    select (public.get_partner_status()->>'expiresAt') is null
  ),
  true,
  'paused partner returns null expiresAt'
);

reset role;

rollback;
