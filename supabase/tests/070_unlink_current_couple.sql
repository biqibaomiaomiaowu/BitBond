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

select pg_temp.assert_text_eq(
  pg_get_function_result('public.unlink_current_couple()'::regprocedure),
  'jsonb',
  'unlink_current_couple returns jsonb (red if missing)'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'unlink_current_couple'
      and pg_get_function_arguments(rpc.oid) = ''
      and rpc.prorettype = 'jsonb'::regtype
      and rpc.prosecdef
      and rpc.proconfig @> array['search_path=public, auth']
  ),
  1,
  'unlink_current_couple is a security definer jsonb RPC with fixed search_path'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'authenticated',
    'public.unlink_current_couple()',
    'execute'
  ),
  true,
  'authenticated can execute unlink_current_couple'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'anon',
    'public.unlink_current_couple()',
    'execute'
  ),
  false,
  'anon cannot execute unlink_current_couple'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'unlink_current_couple'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute unlink_current_couple'
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
    perform public.unlink_current_couple();
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000704', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000704","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.unlink_current_couple();
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000701', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000701","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000702', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000702","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

create temp table pg_temp.unlink_current_couple_fixture (
  couple_id uuid not null,
  owner_user_id uuid not null,
  participant_user_id uuid not null
) on commit drop;

insert into pg_temp.unlink_current_couple_fixture (couple_id, owner_user_id, participant_user_id)
values (
  '20000000-0000-0000-0000-000000000701'::uuid,
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000701'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000702'
  )
);

insert into public.couples (id, user_a_id, user_b_id, status, unlinked_at, unlinked_by)
select
  fixture.couple_id,
  fixture.owner_user_id,
  fixture.participant_user_id,
  'active',
  null,
  null
from pg_temp.unlink_current_couple_fixture fixture;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000702', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000702","role":"authenticated"}', true);
set local role authenticated;

create temp table pg_temp.unlink_current_couple_first_call (
  payload jsonb not null
) on commit drop;

insert into pg_temp.unlink_current_couple_first_call (payload)
select public.unlink_current_couple();

reset role;

select pg_temp.assert_jsonb_eq(
  (
    select payload
    from pg_temp.unlink_current_couple_first_call
  ),
  '{"unlinked":true}'::jsonb,
  'unlink_current_couple first call returns exactly {unlinked:true}'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.couples couples
    join pg_temp.unlink_current_couple_fixture fixture on fixture.couple_id = couples.id
    where couples.status = 'unlinked'
      and couples.unlinked_at is not null
      and couples.unlinked_by = fixture.participant_user_id
  ),
  1,
  'unlink_current_couple first call marks active couple as unlinked by caller'
);

create temp table pg_temp.unlink_current_couple_snapshot (
  first_unlinked_at timestamptz not null
) on commit drop;

insert into pg_temp.unlink_current_couple_snapshot (first_unlinked_at)
select couples.unlinked_at
from public.couples couples
join pg_temp.unlink_current_couple_fixture fixture on fixture.couple_id = couples.id;

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_temp.unlink_current_couple_snapshot
  ),
  1,
  'captured first unlink timestamp for idempotency check'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000702', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000702","role":"authenticated"}', true);
set local role authenticated;

create temp table pg_temp.unlink_current_couple_second_call (
  payload jsonb not null
) on commit drop;

insert into pg_temp.unlink_current_couple_second_call (payload)
select public.unlink_current_couple();

reset role;

select pg_temp.assert_jsonb_eq(
  (
    select payload
    from pg_temp.unlink_current_couple_second_call
  ),
  '{"unlinked":true}'::jsonb,
  'unlink_current_couple second call returns exactly {unlinked:true}'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.couples couples
    join pg_temp.unlink_current_couple_fixture fixture
      on least(couples.user_a_id, couples.user_b_id) = least(fixture.owner_user_id, fixture.participant_user_id)
      and greatest(couples.user_a_id, couples.user_b_id) = greatest(fixture.owner_user_id, fixture.participant_user_id)
    where couples.status = 'active'
      and couples.unlinked_at is null
  ),
  0,
  'unlink_current_couple second call keeps active couple count at zero'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.couples couples
    join pg_temp.unlink_current_couple_fixture fixture
      on least(couples.user_a_id, couples.user_b_id) = least(fixture.owner_user_id, fixture.participant_user_id)
      and greatest(couples.user_a_id, couples.user_b_id) = greatest(fixture.owner_user_id, fixture.participant_user_id)
  ),
  1,
  'unlink_current_couple does not create additional couple rows'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.couples couples
    join pg_temp.unlink_current_couple_fixture fixture on fixture.couple_id = couples.id
    join pg_temp.unlink_current_couple_snapshot snapshot on true
    where couples.status = 'unlinked'
      and couples.unlinked_by = fixture.participant_user_id
      and couples.unlinked_at = snapshot.first_unlinked_at
  ),
  1,
  'unlink_current_couple second call leaves existing unlinked row unchanged'
);

rollback;
