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

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'get_current_couple'
      and pg_get_function_arguments(rpc.oid) = ''
      and rpc.prorettype = 'jsonb'::regtype
      and rpc.prosecdef
      and rpc.proconfig @> array['search_path=public, auth']
  ),
  1,
  'get_current_couple is a security definer jsonb RPC with fixed search_path'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'authenticated',
    'public.get_current_couple()',
    'execute'
  ),
  true,
  'authenticated can execute get_current_couple'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'anon',
    'public.get_current_couple()',
    'execute'
  ),
  false,
  'anon cannot execute get_current_couple'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'get_current_couple'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute get_current_couple'
);

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_raises_message(
  'select public.get_current_couple()',
  'not_authenticated',
  'get_current_couple rejects calls without auth uid'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000601', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000601","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_raises_message(
  'select public.get_current_couple()',
  'user_profile_not_found',
  'get_current_couple rejects caller without profile'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000602', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000602","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();

select pg_temp.assert_jsonb_eq(
  public.get_current_couple(),
  '{"paired":false}'::jsonb,
  'get_current_couple returns exactly {"paired":false} for unpaired caller'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_current_couple() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'paired',
  'unpaired payload contains only paired key'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.get_current_couple() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
    where lower(keys.key) in (
      'package',
      'status',
      'invitecode',
      'invite_code',
      'code',
      'email',
      'phone',
      'token',
      'access_token',
      'refresh_token',
      'auth',
      'authid',
      'auth_user_id',
      'authuserid',
      'history'
    )
  ),
  0,
  'unpaired payload omits sensitive/status/invite fields'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000603', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000603","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000604', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000604","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

update public.users
set
  nickname = 'partner-603',
  avatar_id = 'fox',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000000603';

update public.users
set
  nickname = 'caller-604',
  avatar_id = 'dog',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000000604';

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000000601',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000603'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000604'
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000604', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000604","role":"authenticated"}', true);
set local role authenticated;

create temp table pg_temp.get_current_couple_paired_payload (
  payload jsonb not null
) on commit drop;

insert into pg_temp.get_current_couple_paired_payload (payload)
select public.get_current_couple();

reset role;

do $$
declare
  payload jsonb;
  partner jsonb;
  payload_couple_id uuid;
  expected_partner_nickname text;
  expected_partner_avatar_id text;
begin
  select get_current_couple_paired_payload.payload
  into payload
  from pg_temp.get_current_couple_paired_payload;

  partner := payload->'partner';
  payload_couple_id := (payload->>'coupleId')::uuid;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(payload) as keys(key)
  ) is distinct from 'coupleId,paired,partner' then
    raise exception 'get_current_couple returned unexpected top-level keys: %', payload;
  end if;

  if jsonb_typeof(partner) is distinct from 'object' then
    raise exception 'get_current_couple partner is not an object: %', payload;
  end if;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(partner) as keys(key)
  ) is distinct from 'avatarId,nickname' then
    raise exception 'get_current_couple returned unexpected partner keys: %', partner;
  end if;

  if payload->>'paired' is distinct from 'true' then
    raise exception 'get_current_couple paired should be true for active couple payload: %', payload;
  end if;

  if payload_couple_id is distinct from '20000000-0000-0000-0000-000000000601'::uuid then
    raise exception 'get_current_couple returned unexpected coupleId: %', payload;
  end if;

  select users.nickname, users.avatar_id
  into expected_partner_nickname, expected_partner_avatar_id
  from public.users
  where users.auth_user_id = '00000000-0000-0000-0000-000000000603'
  limit 1;

  if partner->>'nickname' is distinct from expected_partner_nickname then
    raise exception 'get_current_couple partner.nickname mismatch: expected %, got %',
      expected_partner_nickname,
      partner->>'nickname';
  end if;

  if partner->>'avatarId' is distinct from expected_partner_avatar_id then
    raise exception 'get_current_couple partner.avatarId mismatch: expected %, got %',
      expected_partner_avatar_id,
      partner->>'avatarId';
  end if;

  if (
    select count(*)
    from jsonb_object_keys(payload) as keys(key)
    where lower(keys.key) in (
      'package',
      'status',
      'invitecode',
      'invite_code',
      'code',
      'email',
      'phone',
      'token',
      'access_token',
      'refresh_token',
      'auth',
      'authid',
      'auth_user_id',
      'authuserid',
      'history'
    )
  ) > 0 then
    raise exception 'get_current_couple leaked sensitive/status/invite fields in payload: %', payload;
  end if;

  if (
    select count(*)
    from jsonb_object_keys(partner) as keys(key)
    where lower(keys.key) in (
      'id',
      'package',
      'status',
      'invitecode',
      'invite_code',
      'code',
      'email',
      'phone',
      'token',
      'access_token',
      'refresh_token',
      'auth',
      'authid',
      'auth_user_id',
      'authuserid',
      'history'
    )
  ) > 0 then
    raise exception 'get_current_couple leaked sensitive/status/invite fields in partner: %', partner;
  end if;
end;
$$;

update public.users
set
  deleted_at = now(),
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000000603';

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000604', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000604","role":"authenticated"}', true);
set local role authenticated;

create temp table pg_temp.get_current_couple_deleted_partner_payload (
  payload jsonb not null
) on commit drop;

insert into pg_temp.get_current_couple_deleted_partner_payload (payload)
select public.get_current_couple();

reset role;

do $$
declare
  payload jsonb;
  partner jsonb;
begin
  select get_current_couple_deleted_partner_payload.payload
  into payload
  from pg_temp.get_current_couple_deleted_partner_payload;

  partner := payload->'partner';

  if payload->>'paired' is distinct from 'true' then
    raise exception 'get_current_couple should keep paired=true when active couple exists with soft-deleted partner: %', payload;
  end if;

  if payload->>'coupleId' is distinct from '20000000-0000-0000-0000-000000000601' then
    raise exception 'get_current_couple should keep coupleId when partner is soft-deleted: %', payload;
  end if;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(payload) as keys(key)
  ) is distinct from 'coupleId,paired,partner' then
    raise exception 'get_current_couple returned unexpected soft-deleted partner payload keys: %', payload;
  end if;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(partner) as keys(key)
  ) is distinct from 'avatarId,nickname' then
    raise exception 'get_current_couple returned unexpected soft-deleted partner keys: %', partner;
  end if;

  if partner->>'nickname' is not null or partner->>'avatarId' is not null then
    raise exception 'get_current_couple leaked soft-deleted partner profile fields: %', payload;
  end if;
end;
$$;

rollback;
