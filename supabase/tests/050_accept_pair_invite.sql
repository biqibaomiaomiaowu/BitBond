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

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'accept_pair_invite'
      and pg_get_function_arguments(rpc.oid) = 'invite_code text'
      and rpc.prorettype = 'jsonb'::regtype
      and rpc.prosecdef
      and rpc.proconfig @> array['search_path=public, auth']
  ),
  1,
  'accept_pair_invite is a security definer jsonb RPC with fixed search_path'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'authenticated',
    'public.accept_pair_invite(text)',
    'execute'
  ),
  true,
  'authenticated can execute accept_pair_invite'
);

select pg_temp.assert_bool_eq(
  has_function_privilege(
    'anon',
    'public.accept_pair_invite(text)',
    'execute'
  ),
  false,
  'anon cannot execute accept_pair_invite'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'accept_pair_invite'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute accept_pair_invite'
);

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{"role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('123456');
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000501', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000501","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000503', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000503","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000504', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000504","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000505', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000505","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000506', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000506","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

update public.users
set
  nickname = 'inviter-main',
  avatar_id = 'fox',
  updated_at = now()
where auth_user_id = '00000000-0000-0000-0000-000000000501';

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000507', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000507","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('123456');
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('   ');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'blank invite code succeeded, expected invalid_invite_code';
  end if;

  if message not like '%invalid_invite_code%' then
    raise exception 'blank invite code raised %, expected invalid_invite_code', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite(null);
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'null invite code succeeded, expected invalid_invite_code';
  end if;

  if message not like '%invalid_invite_code%' then
    raise exception 'null invite code raised %, expected invalid_invite_code', message;
  end if;
end;
$$;

reset role;

insert into public.pair_invites (code_hash, created_by, expires_at)
values (
  encode(extensions.digest('111111', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000501'
  ),
  now() - interval '1 minute'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite(' 111111 ');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'expired invite succeeded, expected invite_expired';
  end if;

  if message not like '%invite_expired%' then
    raise exception 'expired invite raised %, expected invite_expired', message;
  end if;
end;
$$;

reset role;

insert into public.pair_invites (code_hash, created_by, expires_at, used_at, used_by)
values (
  encode(extensions.digest('222222', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000501'
  ),
  now() + interval '10 minutes',
  now() - interval '1 minute',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000502'
  )
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('222222');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'used invite succeeded, expected invite_used';
  end if;

  if message not like '%invite_used%' then
    raise exception 'used invite raised %, expected invite_used', message;
  end if;
end;
$$;

reset role;

insert into public.pair_invites (code_hash, created_by, expires_at)
values (
  encode(extensions.digest('333333', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000501'
  ),
  now() + interval '10 minutes'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000501', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000501","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('333333');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'self invite succeeded, expected cannot_pair_self';
  end if;

  if message not like '%cannot_pair_self%' then
    raise exception 'self invite raised %, expected cannot_pair_self', message;
  end if;
end;
$$;

reset role;

insert into public.couples (user_a_id, user_b_id, status)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000503'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000504'
  ),
  'active'
);

insert into public.pair_invites (code_hash, created_by, expires_at)
values (
  encode(extensions.digest('444444', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000503'
  ),
  now() + interval '10 minutes'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('444444');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'inviter already paired call succeeded, expected already_paired';
  end if;

  if message not like '%already_paired%' then
    raise exception 'inviter already paired call raised %, expected already_paired', message;
  end if;
end;
$$;

reset role;

insert into public.couples (user_a_id, user_b_id, status)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000505'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000506'
  ),
  'active'
);

insert into public.pair_invites (code_hash, created_by, expires_at)
values (
  encode(extensions.digest('555555', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000501'
  ),
  now() + interval '10 minutes'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000505', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000505","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.accept_pair_invite('555555');
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'accepter already paired call succeeded, expected already_paired';
  end if;

  if message not like '%already_paired%' then
    raise exception 'accepter already paired call raised %, expected already_paired', message;
  end if;
end;
$$;

reset role;

insert into public.pair_invites (code_hash, created_by, expires_at)
values (
  encode(extensions.digest('666666', 'sha256'), 'hex'),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000000501'
  ),
  now() + interval '10 minutes'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000502', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000502","role":"authenticated"}', true);
set local role authenticated;

create temp table pg_temp.accept_pair_invite_valid_payload (
  payload jsonb not null
) on commit drop;

insert into pg_temp.accept_pair_invite_valid_payload (payload)
select public.accept_pair_invite(' 666666 ');

reset role;

do $$
declare
  payload jsonb;
  partner jsonb;
  inviter_user_id uuid;
  accepter_user_id uuid;
  payload_couple_id uuid;
  used_invite_used_at timestamptz;
  used_invite_used_by uuid;
begin
  select accept_pair_invite_valid_payload.payload
  into payload
  from pg_temp.accept_pair_invite_valid_payload;

  select id
  into inviter_user_id
  from public.users
  where auth_user_id = '00000000-0000-0000-0000-000000000501';

  select id
  into accepter_user_id
  from public.users
  where auth_user_id = '00000000-0000-0000-0000-000000000502';

  partner := payload->'partner';
  payload_couple_id := (payload->>'coupleId')::uuid;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(payload) as keys(key)
  ) is distinct from 'coupleId,partner' then
    raise exception 'accept_pair_invite returned unexpected top-level keys: %', payload;
  end if;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(partner) as keys(key)
  ) is distinct from 'avatarId,nickname' then
    raise exception 'accept_pair_invite returned unexpected partner keys: %', partner;
  end if;

  if (
    select count(*)
    from jsonb_object_keys(payload) as keys(key)
    where keys.key in (
      'access_token',
      'refresh_token',
      'token',
      'auth',
      'authUserId',
      'email',
      'phone',
      'code',
      'inviteCode',
      'status',
      'package',
      'history'
    )
  ) > 0 then
    raise exception 'accept_pair_invite leaked sensitive or status fields in top-level payload: %', payload;
  end if;

  if (
    select count(*)
    from jsonb_object_keys(partner) as keys(key)
    where keys.key in (
      'id',
      'auth_user_id',
      'authUserId',
      'access_token',
      'refresh_token',
      'token',
      'email',
      'phone',
      'code',
      'status',
      'package',
      'history'
    )
  ) > 0 then
    raise exception 'accept_pair_invite leaked sensitive partner fields: %', partner;
  end if;

  if payload_couple_id is null then
    raise exception 'accept_pair_invite returned null coupleId: %', payload;
  end if;

  if (
    select count(*)
    from public.couples
    where couples.id = payload_couple_id
      and couples.status = 'active'
      and couples.unlinked_at is null
      and couples.user_a_id = inviter_user_id
      and couples.user_b_id = accepter_user_id
  ) <> 1 then
    raise exception 'accept_pair_invite did not create expected active couple row for inviter/accepter. payload=%, inviter=%, accepter=%, rows=%',
      payload,
      inviter_user_id,
      accepter_user_id,
      (
        select coalesce(
          string_agg(
            couples.id::text || ':a=' || couples.user_a_id::text || ':b=' || couples.user_b_id::text || ':status=' || couples.status || ':unlinked=' || coalesce(couples.unlinked_at::text, 'null'),
            ';'
            order by couples.created_at, couples.id
          ),
          ''
        )
        from public.couples
        where couples.id = payload_couple_id
          or couples.user_a_id in (inviter_user_id, accepter_user_id)
          or couples.user_b_id in (inviter_user_id, accepter_user_id)
      );
  end if;

  if (
    select count(*)
    from public.couples
    where couples.status = 'active'
      and couples.unlinked_at is null
      and couples.user_a_id = inviter_user_id
      and couples.user_b_id = accepter_user_id
  ) <> 1 then
    raise exception 'accept_pair_invite created duplicate active couples for inviter/accepter';
  end if;

  select pair_invites.used_at, pair_invites.used_by
  into used_invite_used_at, used_invite_used_by
  from public.pair_invites
  where pair_invites.code_hash = encode(extensions.digest('666666', 'sha256'), 'hex')
  order by pair_invites.created_at desc, pair_invites.id desc
  limit 1;

  if used_invite_used_at is null then
    raise exception 'accept_pair_invite did not mark invite used_at';
  end if;

  if used_invite_used_by is distinct from accepter_user_id then
    raise exception 'accept_pair_invite used_by mismatch: expected %, got %', accepter_user_id, used_invite_used_by;
  end if;

  if partner->>'nickname' is distinct from (
    select users.nickname
    from public.users
    where users.id = inviter_user_id
  ) then
    raise exception 'accept_pair_invite partner.nickname mismatch: %', partner;
  end if;

  if partner->>'avatarId' is distinct from (
    select users.avatar_id
    from public.users
    where users.id = inviter_user_id
  ) then
    raise exception 'accept_pair_invite partner.avatarId mismatch: %', partner;
  end if;
end;
$$;

rollback;
