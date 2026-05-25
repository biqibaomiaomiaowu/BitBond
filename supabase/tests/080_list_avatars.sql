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

insert into public.avatars (id, name, asset_key, is_active, sort_order)
values ('list_inactive', 'List Inactive', 'avatars/list-inactive', false, -10)
on conflict (id) do update
set
  name = excluded.name,
  asset_key = excluded.asset_key,
  is_active = excluded.is_active,
  sort_order = excluded.sort_order,
  updated_at = now();

select pg_temp.assert_text_eq(
  pg_get_function_result('public.list_avatars()'::regprocedure),
  'jsonb',
  'list_avatars returns jsonb'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'list_avatars'
      and p.prosecdef
      and p.proconfig @> array['search_path=public, auth']
  ),
  1,
  'list_avatars is security definer with fixed search_path'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
    where n.nspname = 'public'
      and p.proname = 'list_avatars'
      and acl.privilege_type = 'EXECUTE'
      and acl.grantee = 0
  ),
  0,
  'list_avatars execute privilege is revoked from public'
);

select pg_temp.assert_eq(
  (
    select count(distinct r.rolname)
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    cross join lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) acl
    join pg_roles r on r.oid = acl.grantee
    where n.nspname = 'public'
      and p.proname = 'list_avatars'
      and acl.privilege_type = 'EXECUTE'
      and r.rolname in ('anon', 'authenticated')
  ),
  2,
  'list_avatars grants execute to anon and authenticated'
);

set local role anon;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.list_avatars() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'avatars',
  'list_avatars returns only top-level avatars key'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.list_avatars() as payload
    )
    select jsonb_array_length(payload->'avatars')
    from result
  ),
  8,
  'list_avatars returns exactly eight active avatars'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.list_avatars() as payload
    ),
    item_keys as (
      select string_agg(keys.key, ',' order by keys.key) as key_list
      from result,
        lateral jsonb_array_elements(payload->'avatars') with ordinality as avatar(item, ordinal),
        lateral jsonb_object_keys(avatar.item) as keys(key)
      group by avatar.ordinal
    )
    select count(*)
    from item_keys
    where key_list <> 'assetKey,id,name'
  ),
  0,
  'list_avatars avatar items expose exactly id, name, and assetKey'
);

select pg_temp.assert_jsonb_eq(
  (
    with result as (
      select public.list_avatars() as payload
    )
    select payload->'avatars'
    from result
  ),
  (
    select jsonb_agg(
      jsonb_build_object(
        'id', avatars.id,
        'name', avatars.name,
        'assetKey', avatars.asset_key
      )
      order by avatars.sort_order
    )
    from public.avatars
    where avatars.is_active
  ),
  'list_avatars returns public avatar fields ordered by sort_order'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.list_avatars() as payload
    )
    select count(*)
    from result,
      lateral jsonb_array_elements(payload->'avatars') as avatar(item),
      lateral jsonb_object_keys(avatar.item) as keys(key)
    where keys.key in (
      'sort_order',
      'is_active',
      'created_at',
      'updated_at',
      'auth_user_id',
      'user_id',
      'asset_key'
    )
  ),
  0,
  'list_avatars does not expose internal avatar fields'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000801', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000801","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_eq(
  (
    with result as (
      select public.list_avatars() as payload
    )
    select jsonb_array_length(payload->'avatars')
    from result
  ),
  8,
  'authenticated users can execute list_avatars'
);

rollback;
