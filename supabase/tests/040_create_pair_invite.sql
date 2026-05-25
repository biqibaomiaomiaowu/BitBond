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
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'pair_invites'
      and column_name = 'code_hash'
  ),
  1,
  'pair_invites stores code_hash'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'pair_invites'
      and column_name = 'code'
  ),
  0,
  'pair_invites does not store raw code column'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_class index_class
    join pg_namespace index_schema on index_schema.oid = index_class.relnamespace
    where index_schema.nspname = 'public'
      and index_class.relname = 'idx_pair_invites_code_hash'
  ),
  0,
  'pair_invites no longer has globally unique historical code_hash index'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_class index_class
    join pg_namespace index_schema on index_schema.oid = index_class.relnamespace
    join pg_index index_meta on index_meta.indexrelid = index_class.oid
    join pg_class table_class on table_class.oid = index_meta.indrelid
    where index_schema.nspname = 'public'
      and table_class.relname = 'pair_invites'
      and index_class.relname = 'idx_pair_invites_unused_code_hash'
      and index_meta.indisunique
      and pg_get_indexdef(index_meta.indexrelid) like '%(code_hash)%'
      and pg_get_expr(index_meta.indpred, index_meta.indrelid) = '(used_at IS NULL)'
  ),
  1,
  'pair_invites has unique code_hash index only for unused invites'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'create_pair_invite'
      and rpc.prorettype = 'jsonb'::regtype
      and rpc.prosecdef
      and 'search_path=public, auth' = any(rpc.proconfig)
  ),
  1,
  'create_pair_invite is a security definer jsonb RPC with expected search_path'
);

select pg_temp.assert_eq(
  has_function_privilege('authenticated', 'public.create_pair_invite()', 'EXECUTE')::integer::bigint,
  1,
  'authenticated can execute create_pair_invite'
);

select pg_temp.assert_eq(
  has_function_privilege('anon', 'public.create_pair_invite()', 'EXECUTE')::integer::bigint,
  0,
  'anon cannot execute create_pair_invite'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'create_pair_invite'
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ),
  0,
  'public cannot execute create_pair_invite'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'create_pair_invite'
      and rpc.prosrc like '%extensions.gen_random_bytes(4)%'
  ),
  1,
  'create_pair_invite uses pgcrypto random bytes for invite codes'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_proc rpc
    join pg_namespace rpc_schema on rpc_schema.oid = rpc.pronamespace
    where rpc_schema.nspname = 'public'
      and rpc.proname = 'create_pair_invite'
      and rpc.prosrc ilike '%exception when unique_violation%'
  ),
  0,
  'create_pair_invite does not broadly catch unique_violation'
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
    perform public.create_pair_invite();
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

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{"role":"anon"}', true);
set local role anon;

do $$
declare
  raised boolean := false;
begin
  begin
    perform public.create_pair_invite();
  exception when insufficient_privilege then
    raised := true;
  end;

  reset role;

  if not raised then
    raise exception 'anon call did not raise insufficient_privilege';
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000404', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000404","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.create_pair_invite();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'authenticated user without profile created invite, expected user_profile_not_found';
  end if;

  if message not like '%user_profile_not_found%' then
    raise exception 'authenticated user without profile raised %, expected user_profile_not_found', message;
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000401', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000401","role":"authenticated"}', true);
set local role authenticated;

select public.ensure_user_profile();

reset role;

insert into public.pair_invites (code_hash, created_by, expires_at)
select
  'expired-unused-cleanup-hash',
  users.id,
  now() - interval '1 minute'
from public.users
where users.auth_user_id = '00000000-0000-0000-0000-000000000401';

set local role authenticated;

do $$
declare
  payload jsonb;
  raw_code text;
  expires_at timestamptz;
  ttl_seconds numeric;
  current_user_id uuid;
  stored_code_hash text;
begin
  select users.id
  into current_user_id
  from public.users
  where users.auth_user_id = '00000000-0000-0000-0000-000000000401';

  payload := public.create_pair_invite();
  raw_code := payload->>'code';
  expires_at := (payload->>'expiresAt')::timestamptz;

  if (
    select string_agg(key, ',' order by key)
    from jsonb_object_keys(payload) as keys(key)
  ) is distinct from 'code,expiresAt' then
    raise exception 'create_pair_invite returned unexpected keys: %', payload;
  end if;

  if raw_code is null or raw_code !~ '^[0-9]{6}$' then
    raise exception 'create_pair_invite returned invalid code: %', raw_code;
  end if;

  if expires_at is null or expires_at <= now() then
    raise exception 'create_pair_invite returned non-future expiresAt: %', expires_at;
  end if;

  ttl_seconds := extract(epoch from (expires_at - now()));

  if ttl_seconds < 595 or ttl_seconds > 605 then
    raise exception 'create_pair_invite returned expiresAt %, expected about 10 minutes after now()', expires_at;
  end if;

  if exists (
    select 1
    from public.pair_invites
    where pair_invites.code_hash = 'expired-unused-cleanup-hash'
  ) then
    raise exception 'create_pair_invite did not clean expired unused invites';
  end if;

  select pair_invites.code_hash
  into stored_code_hash
  from public.pair_invites
  where pair_invites.created_by = current_user_id
  order by pair_invites.created_at desc
  limit 1;

  if stored_code_hash is null then
    raise exception 'create_pair_invite did not insert pair_invites row';
  end if;

  if stored_code_hash = raw_code then
    raise exception 'pair_invites stored raw code instead of hash';
  end if;

  if stored_code_hash <> encode(extensions.digest(raw_code, 'sha256'), 'hex') then
    raise exception 'pair_invites stored hash %, expected sha256 hash for returned code', stored_code_hash;
  end if;

  if exists (
    select 1
    from public.pair_invites
    where pair_invites.code_hash = raw_code
  ) then
    raise exception 'raw code was stored in pair_invites.code_hash';
  end if;
end;
$$;

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000402', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000402","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000403', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000403","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();

reset role;

insert into public.couples (user_a_id, user_b_id, status)
select user_a.id, user_b.id, 'active'
from public.users user_a
cross join public.users user_b
where user_a.auth_user_id = '00000000-0000-0000-0000-000000000402'
  and user_b.auth_user_id = '00000000-0000-0000-0000-000000000403';

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000402', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000402","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.create_pair_invite();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'paired user created invite, expected already_paired';
  end if;

  if message not like '%already_paired%' then
    raise exception 'paired user raised %, expected already_paired', message;
  end if;
end;
$$;

rollback;
