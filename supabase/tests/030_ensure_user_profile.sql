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

set local role anon;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.ensure_user_profile();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'anonymous call succeeded, expected not_authenticated';
  end if;

  if message not like '%not_authenticated%' then
    raise exception 'anonymous call raised %, expected not_authenticated', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000301', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000301","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.ensure_user_profile() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'user',
  'ensure_user_profile returns only top-level user key'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.ensure_user_profile() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload->'user') as keys(key)
  ),
  'avatarId,id,nickname',
  'ensure_user_profile user payload exposes only public profile fields'
);

select pg_temp.assert_eq(
  (
    with result as (
      select public.ensure_user_profile() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload) as top_keys(key)
    where top_keys.key in ('access_token', 'refresh_token', 'email', 'phone', 'service')
  ) +
  (
    with result as (
      select public.ensure_user_profile() as payload
    )
    select count(*)
    from result,
      lateral jsonb_object_keys(payload->'user') as user_keys(key)
    where user_keys.key in ('access_token', 'refresh_token', 'email', 'phone', 'service')
  ),
  0,
  'ensure_user_profile does not expose auth sensitive fields'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000301'
  ),
  1,
  'ensure_user_profile creates one public.users row'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.users u
    join public.user_avatar ua on ua.user_id = u.id
    where u.auth_user_id = '00000000-0000-0000-0000-000000000301'
      and u.avatar_id = 'cat'
      and ua.avatar_id = u.avatar_id
  ),
  1,
  'ensure_user_profile creates matching public.user_avatar row with default cat avatar'
);

rollback;
