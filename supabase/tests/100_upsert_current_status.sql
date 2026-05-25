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
    'public.upsert_current_status(text,timestamptz)',
    'execute'
  ),
  true,
  'authenticated can execute upsert_current_status'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'anon',
    'public.upsert_current_status(text,timestamptz)',
    'execute'
  ),
  false,
  'anon cannot execute upsert_current_status'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'upsert_current_status'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute upsert_current_status'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc
    join pg_namespace on pg_namespace.oid = pg_proc.pronamespace
    where pg_namespace.nspname = 'public'
      and pg_proc.proname = 'upsert_current_status'
      and pg_get_function_arguments(pg_proc.oid) = 'next_status_code text, next_status_updated_at timestamp with time zone'
      and pg_proc.prorettype = 'jsonb'::regtype
      and pg_proc.prosecdef
      and pg_proc.proconfig @> array['search_path=public, auth']
  ),
  1,
  'upsert_current_status is a security definer jsonb RPC with fixed search_path'
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
    perform public.upsert_current_status(
      'online',
      '2026-05-25 08:00:00+00'::timestamptz
    );
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001004', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001004","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.upsert_current_status(
      'online',
      '2026-05-25 08:10:00+00'::timestamptz
    );
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'authenticated user without profile wrote status, expected profile_not_found';
  end if;

  if message not like '%profile_not_found%' then
    raise exception 'authenticated user without profile raised %, expected profile_not_found', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001002', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001002","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001003', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001003","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001001',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001001'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001002'
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.upsert_current_status(
        'music',
        '2026-05-25 08:30:00+00'::timestamptz
      ) as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,statusCode,statusUpdatedAt',
  'upsert_current_status returns exactly the public status payload keys'
);

select pg_temp.assert_text_eq(
  (
    select public.upsert_current_status(
      'music',
      '2026-05-25 08:30:00+00'::timestamptz
    )->>'statusCode'
  ),
  'music',
  'upsert_current_status returns the requested status code'
);

select pg_temp.assert_timestamptz_eq(
  (
    select (
      public.upsert_current_status(
        'music',
        '2026-05-25 08:30:00+00'::timestamptz
      )->>'statusUpdatedAt'
    )::timestamptz
  ),
  '2026-05-25 08:30:00+00'::timestamptz,
  'upsert_current_status returns the requested update time'
);

select pg_temp.assert_timestamptz_eq(
  (
    select (
      public.upsert_current_status(
        'music',
        '2026-05-25 08:30:00+00'::timestamptz
      )->>'expiresAt'
    )::timestamptz
  ),
  '2026-05-25 08:45:00+00'::timestamptz,
  'upsert_current_status returns expiresAt fifteen minutes after statusUpdatedAt'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001001'
      and current_status.couple_id = '20000000-0000-0000-0000-000000001001'
      and current_status.status_code = 'music'
      and current_status.status_updated_at = '2026-05-25 08:30:00+00'::timestamptz
      and current_status.expires_at = '2026-05-25 08:45:00+00'::timestamptz
      and current_status.is_paused = false
  ),
  1,
  'upsert_current_status inserts caller status with the active couple'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;
select public.upsert_current_status(
  'reading',
  '2026-05-25 09:10:00+00'::timestamptz
);
reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001001'
  ),
  1,
  'upsert_current_status keeps one current status row per caller'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001001'
      and current_status.couple_id = '20000000-0000-0000-0000-000000001001'
      and current_status.status_code = 'reading'
      and current_status.status_updated_at = '2026-05-25 09:10:00+00'::timestamptz
      and current_status.expires_at = '2026-05-25 09:25:00+00'::timestamptz
      and current_status.is_paused = false
  ),
  1,
  'upsert_current_status updates the caller status row'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    select public.upsert_current_status(
      'gaming',
      '2026-05-25 09:00:00+00'::timestamptz
    )->>'statusCode'
  ),
  'reading',
  'upsert_current_status returns stored status when older events are ignored'
);

select pg_temp.assert_timestamptz_eq(
  (
    select (
      public.upsert_current_status(
        'gaming',
        '2026-05-25 09:00:00+00'::timestamptz
      )->>'statusUpdatedAt'
    )::timestamptz
  ),
  '2026-05-25 09:10:00+00'::timestamptz,
  'upsert_current_status returns stored statusUpdatedAt when older events are ignored'
);
reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001001'
      and current_status.status_code = 'reading'
      and current_status.status_updated_at = '2026-05-25 09:10:00+00'::timestamptz
      and current_status.expires_at = '2026-05-25 09:25:00+00'::timestamptz
  ),
  1,
  'upsert_current_status does not let older events overwrite newer status'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001003', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001003","role":"authenticated"}', true);
set local role authenticated;
select public.upsert_current_status(
  'online',
  '2026-05-25 10:00:00+00'::timestamptz
);
reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001003'
      and current_status.couple_id is null
      and current_status.status_code = 'online'
  ),
  1,
  'upsert_current_status allows callers without an active couple'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001003', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001003","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_eq(
  (
    with result as (
      select public.upsert_current_status(
        'online',
        '2026-05-25 10:00:00+00'::timestamptz
      ) as payload
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
      'history'
    )
  ),
  0,
  'upsert_current_status output omits event and history fields'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'current_status'
      and column_name in (
        'package',
        'app',
        'duration',
        'content',
        'chat',
        'history'
      )
  ),
  0,
  'current_status table omits event and history fields'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.upsert_current_status(
      'invalid',
      '2026-05-25 11:00:00+00'::timestamptz
    );
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'invalid status call succeeded, expected invalid_status';
  end if;

  if message not like '%invalid_status%' then
    raise exception 'invalid status call raised %, expected invalid_status', message;
  end if;
end;
$$;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001001', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001001","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.upsert_current_status('online', null);
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'null status_updated_at call succeeded, expected invalid_status_updated_at';
  end if;

  if message not like '%invalid_status_updated_at%' then
    raise exception 'null status_updated_at call raised %, expected invalid_status_updated_at', message;
  end if;
end;
$$;

rollback;
