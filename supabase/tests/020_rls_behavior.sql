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

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_class
    where oid in (
      'public.users'::regclass,
      'public.avatars'::regclass,
      'public.user_avatar'::regclass,
      'public.current_status'::regclass,
      'public.couples'::regclass,
      'public.pair_invites'::regclass
    )
      and relrowsecurity
  ),
  6,
  'RLS is enabled on all Task 3 tables'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_policies
    where schemaname = 'public'
      and policyname in (
        'users_select_self',
        'users_update_self',
        'avatars_select_all',
        'user_avatar_select_self',
        'current_status_select_self_or_couple',
        'couples_select_participant',
        'pair_invites_select_creator'
      )
  ),
  7,
  'all Task 3 policies exist'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_policies
    where schemaname = 'public'
      and (
        (policyname = 'users_select_self' and tablename = 'users' and cmd = 'SELECT')
        or (policyname = 'users_update_self' and tablename = 'users' and cmd = 'UPDATE')
        or (policyname = 'avatars_select_all' and tablename = 'avatars' and cmd = 'SELECT')
        or (policyname = 'user_avatar_select_self' and tablename = 'user_avatar' and cmd = 'SELECT')
        or (policyname = 'current_status_select_self_or_couple' and tablename = 'current_status' and cmd = 'SELECT')
        or (policyname = 'couples_select_participant' and tablename = 'couples' and cmd = 'SELECT')
        or (policyname = 'pair_invites_select_creator' and tablename = 'pair_invites' and cmd = 'SELECT')
      )
  ),
  7,
  'Task 3 policies are attached to expected table and command'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_policies
    where schemaname = 'public'
      and tablename = 'avatars'
      and policyname = 'avatars_are_public_readable'
  ),
  0,
  'legacy avatar policy is removed'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_policies
    where schemaname = 'public'
      and tablename = 'avatars'
  ),
  1,
  'avatars has only the planned policy'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_policies
    where schemaname = 'public'
      and tablename in (
        'user_avatar',
        'current_status',
        'couples',
        'pair_invites'
      )
      and cmd <> 'SELECT'
  ),
  0,
  'select-only tables do not define direct write policies'
);

insert into public.avatars (id, name, asset_key, is_active, sort_order)
values
  ('rls_active', 'RLS Active', 'rls/active', true, 9001),
  ('rls_inactive', 'RLS Inactive', 'rls/inactive', false, 9002)
on conflict (id) do update
set
  name = excluded.name,
  asset_key = excluded.asset_key,
  is_active = excluded.is_active,
  sort_order = excluded.sort_order,
  updated_at = now();

insert into public.users (id, auth_user_id, nickname, avatar_id)
values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000101', 'RLS A', 'rls_active'),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000102', 'RLS B', 'rls_active'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000103', 'RLS C', 'rls_active'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000104', 'RLS D', 'rls_active'),
  ('10000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000105', 'RLS E', 'rls_active')
on conflict (id) do update
set
  auth_user_id = excluded.auth_user_id,
  nickname = excluded.nickname,
  avatar_id = excluded.avatar_id,
  updated_at = now(),
  deleted_at = null;

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000001',
  '10000000-0000-0000-0000-000000000002',
  'active'
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = null,
  unlinked_by = null;

insert into public.couples (id, user_a_id, user_b_id, status, unlinked_at, unlinked_by)
values
  (
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000003',
    'unlinked',
    now(),
    '10000000-0000-0000-0000-000000000001'
  ),
  (
    '20000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005',
    'active',
    null,
    null
  )
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = excluded.unlinked_at,
  unlinked_by = excluded.unlinked_by;

insert into public.pair_invites (id, code_hash, created_by, expires_at)
values
  ('30000000-0000-0000-0000-000000000001', 'rls-invite-a', '10000000-0000-0000-0000-000000000001', now() + interval '1 day'),
  ('30000000-0000-0000-0000-000000000003', 'rls-invite-c', '10000000-0000-0000-0000-000000000003', now() + interval '1 day')
on conflict (id) do update
set
  code_hash = excluded.code_hash,
  created_by = excluded.created_by,
  expires_at = excluded.expires_at,
  used_at = null,
  used_by = null;

insert into public.user_avatar (user_id, avatar_id, variant_id)
values
  ('10000000-0000-0000-0000-000000000001', 'rls_active', 'a'),
  ('10000000-0000-0000-0000-000000000002', 'rls_active', 'b'),
  ('10000000-0000-0000-0000-000000000003', 'rls_active', 'c'),
  ('10000000-0000-0000-0000-000000000004', 'rls_active', 'd'),
  ('10000000-0000-0000-0000-000000000005', 'rls_active', 'e')
on conflict (user_id) do update
set
  avatar_id = excluded.avatar_id,
  variant_id = excluded.variant_id,
  updated_at = now();

insert into public.current_status (
  user_id,
  couple_id,
  status_code,
  status_updated_at,
  expires_at
)
values
  (
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'reading',
    now(),
    now() + interval '1 hour'
  ),
  (
    '10000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    'music',
    now(),
    now() + interval '1 hour'
  ),
  (
    '10000000-0000-0000-0000-000000000003',
    '20000000-0000-0000-0000-000000000002',
    'online',
    now(),
    now() + interval '1 hour'
  ),
  (
    '10000000-0000-0000-0000-000000000004',
    '20000000-0000-0000-0000-000000000003',
    'gaming',
    now(),
    now() + interval '1 hour'
  )
on conflict (user_id) do update
set
  couple_id = excluded.couple_id,
  status_code = excluded.status_code,
  status_updated_at = excluded.status_updated_at,
  expires_at = excluded.expires_at,
  is_paused = false,
  updated_at = now();

set local role anon;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.avatars
    where id in ('rls_active', 'rls_inactive')
  ),
  2,
  'anon can select all avatars including inactive fixtures'
);

reset role;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000101', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000101","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_eq(
  (select count(*) from public.users),
  1,
  'user A can select only their own user row'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.user_avatar
    where user_id = '10000000-0000-0000-0000-000000000001'
  ),
  1,
  'user A can select their own avatar mapping'
);

select pg_temp.assert_eq(
  (select count(*) from public.user_avatar),
  1,
  'user A cannot select other avatar mappings'
);

select pg_temp.assert_eq(
  (select count(*) from public.couples),
  2,
  'user A can select couples they participated in'
);

select pg_temp.assert_eq(
  (select count(*) from public.pair_invites),
  1,
  'user A can select only invites they created'
);

select pg_temp.assert_eq(
  (select count(*) from public.current_status),
  2,
  'user A can select self and coupled partner current status'
);

update public.users
set nickname = 'RLS A Updated'
where id = '10000000-0000-0000-0000-000000000001';

reset role;

select pg_temp.assert_text_eq(
  (
    select nickname
    from public.users
    where id = '10000000-0000-0000-0000-000000000001'
  ),
  'RLS A Updated',
  'user A can update their own user row'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000103', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000103","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_eq(
  (select count(*) from public.users),
  1,
  'user C can select only their own user row'
);

select pg_temp.assert_eq(
  (select count(*) from public.couples),
  1,
  'user C can select their own unlinked couple only'
);

select pg_temp.assert_eq(
  (select count(*) from public.pair_invites),
  1,
  'user C can select only their own invite'
);

select pg_temp.assert_eq(
  (select count(*) from public.current_status),
  1,
  'user C can select only their own uncoupled current status'
);

rollback;
