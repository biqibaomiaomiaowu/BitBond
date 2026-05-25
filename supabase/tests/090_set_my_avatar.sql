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

create or replace function pg_temp.assert_raises_message(
  statement text,
  expected_message text,
  label text
)
returns void
language plpgsql
as $$
declare
  raised boolean := false;
  message text;
begin
  begin
    execute statement;
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  if not raised then
    raise exception '%: statement succeeded, expected %', label, expected_message;
  end if;

  if message not like '%' || expected_message || '%' then
    raise exception '%: raised %, expected %', label, message, expected_message;
  end if;
end;
$$;

insert into public.avatars (id, name, asset_key, is_active, sort_order)
values ('inactive_avatar', 'Inactive Avatar', 'avatars/inactive-avatar', false, 9090)
on conflict (id) do update
set
  name = excluded.name,
  asset_key = excluded.asset_key,
  is_active = excluded.is_active,
  sort_order = excluded.sort_order,
  updated_at = now();

select pg_temp.assert_bool_eq(
  has_function_privilege('authenticated', 'public.set_my_avatar(text)', 'EXECUTE'),
  true,
  'authenticated can execute set_my_avatar'
);

select pg_temp.assert_bool_eq(
  has_function_privilege('anon', 'public.set_my_avatar(text)', 'EXECUTE'),
  false,
  'anon cannot execute set_my_avatar'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
    where n.nspname = 'public'
      and p.proname = 'set_my_avatar'
      and p.proargtypes = '25'::oidvector
      and acl.grantee = 0
      and acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute set_my_avatar'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'set_my_avatar'
      and p.proargtypes = '25'::oidvector
      and p.prorettype = 'jsonb'::regtype
      and p.prosecdef
      and 'search_path=public, auth' = any(p.proconfig)
  ),
  1,
  'set_my_avatar is a security definer jsonb RPC with expected search_path'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000901', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000901","role":"authenticated"}', true);
set local role authenticated;

select public.ensure_user_profile();

reset role;

update public.users
set
  avatar_id = 'dog',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000000901';

update public.user_avatar ua
set
  avatar_id = 'dog',
  updated_at = now()
from public.users u
where u.auth_user_id = '00000000-0000-0000-0000-000000000901'
  and ua.user_id = u.id;

set local role authenticated;

select pg_temp.assert_jsonb_eq(
  public.set_my_avatar('cat'),
  '{"avatarId":"cat"}'::jsonb,
  'set_my_avatar returns exactly the selected avatar id'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.set_my_avatar('cat') as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'avatarId',
  'set_my_avatar returns only avatarId'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.set_my_avatar('cat') as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where lower(keys.key) in (
      'token',
      'access_token',
      'refresh_token',
      'auth',
      'email',
      'phone',
      'partner'
    )
  ),
  0,
  'set_my_avatar does not expose sensitive or partner fields'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.users u
    join public.user_avatar ua on ua.user_id = u.id
    where u.auth_user_id = '00000000-0000-0000-0000-000000000901'
      and u.avatar_id = 'cat'
      and ua.avatar_id = 'cat'
  ),
  1,
  'set_my_avatar updates users and user_avatar avatar ids'
);

set local role authenticated;

select pg_temp.assert_raises_message(
  'select public.set_my_avatar(''   '')',
  'invalid_avatar_id',
  'set_my_avatar rejects blank avatar ids'
);

select pg_temp.assert_raises_message(
  'select public.set_my_avatar(''missing_avatar'')',
  'avatar_not_found',
  'set_my_avatar rejects missing avatar ids'
);

select pg_temp.assert_raises_message(
  'select public.set_my_avatar(''inactive_avatar'')',
  'avatar_not_found',
  'set_my_avatar rejects inactive avatar ids'
);

reset role;

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);

select pg_temp.assert_raises_message(
  'select public.set_my_avatar(''cat'')',
  'not_authenticated',
  'set_my_avatar rejects calls without auth uid'
);

rollback;
