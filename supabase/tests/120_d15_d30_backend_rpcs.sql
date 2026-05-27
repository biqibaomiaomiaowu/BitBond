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

create or replace function pg_temp.assert_rpc(
  function_name text,
  identity_arguments text,
  label text
)
returns void
language plpgsql
as $$
declare
  matching_count bigint;
begin
  select count(*)
  into matching_count
  from pg_proc
  join pg_namespace on pg_namespace.oid = pg_proc.pronamespace
  where pg_namespace.nspname = 'public'
    and pg_proc.proname = function_name
    and pg_get_function_identity_arguments(pg_proc.oid) = identity_arguments
    and pg_proc.prorettype = 'jsonb'::regtype
    and pg_proc.prosecdef
    and pg_proc.proconfig @> array['search_path=public, auth'];

  if matching_count <> 1 then
    raise exception '%: expected one security definer jsonb RPC, got %', label, matching_count;
  end if;
end;
$$;

create or replace function pg_temp.assert_rpc_execute_grants(
  function_signature text,
  label text
)
returns void
language plpgsql
as $$
begin
  if not has_function_privilege('authenticated', function_signature, 'execute') then
    raise exception '%: authenticated cannot execute %', label, function_signature;
  end if;

  if has_function_privilege('anon', function_signature, 'execute') then
    raise exception '%: anon can execute %', label, function_signature;
  end if;

  if exists (
    select 1
    from pg_proc rpc
    cross join lateral aclexplode(coalesce(rpc.proacl, acldefault('f', rpc.proowner))) rpc_acl
    where rpc.oid = function_signature::regprocedure
      and rpc_acl.grantee = 0
      and rpc_acl.privilege_type = 'EXECUTE'
  ) then
    raise exception '%: public can execute %', label, function_signature;
  end if;
end;
$$;

create or replace function pg_temp.assert_raises(
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
    raise exception '%: expected %, got %', label, expected_message, message;
  end if;
end;
$$;

select pg_temp.assert_bool_eq(
  to_regclass('public.status_privacy_settings') is not null,
  true,
  'status_privacy_settings table exists'
);

select pg_temp.assert_bool_eq(
  to_regclass('public.interactions') is not null,
  true,
  'interactions table exists'
);

select pg_temp.assert_bool_eq(
  to_regclass('public.analytics_events') is not null,
  true,
  'analytics_events table exists'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from pg_class
    where oid in (
      'public.status_privacy_settings'::regclass,
      'public.interactions'::regclass,
      'public.analytics_events'::regclass
    )
      and relrowsecurity
  ),
  3,
  'new D15-D30 tables have RLS enabled'
);

select pg_temp.assert_rpc('set_sharing_paused', 'next_is_paused boolean', 'set_sharing_paused metadata');
select pg_temp.assert_rpc('get_sharing_state', '', 'get_sharing_state metadata');
select pg_temp.assert_rpc('get_status_privacy_settings', '', 'get_status_privacy_settings metadata');
select pg_temp.assert_rpc('update_status_privacy_settings', 'next_allowed_statuses text[]', 'update_status_privacy_settings metadata');
select pg_temp.assert_rpc('send_heart_interaction', '', 'send_heart_interaction metadata');
select pg_temp.assert_rpc('get_latest_interactions', '', 'get_latest_interactions metadata');
select pg_temp.assert_rpc('mark_interactions_seen', 'next_interaction_ids uuid[]', 'mark_interactions_seen metadata');
select pg_temp.assert_rpc('get_widget_status', '', 'get_widget_status metadata');
select pg_temp.assert_rpc('delete_account_data', '', 'delete_account_data metadata');
select pg_temp.assert_rpc('record_analytics_event', 'next_event_name text, next_properties jsonb', 'record_analytics_event metadata');

select pg_temp.assert_rpc_execute_grants('public.set_sharing_paused(boolean)', 'set_sharing_paused grants');
select pg_temp.assert_rpc_execute_grants('public.get_sharing_state()', 'get_sharing_state grants');
select pg_temp.assert_rpc_execute_grants('public.get_status_privacy_settings()', 'get_status_privacy_settings grants');
select pg_temp.assert_rpc_execute_grants('public.update_status_privacy_settings(text[])', 'update_status_privacy_settings grants');
select pg_temp.assert_rpc_execute_grants('public.send_heart_interaction()', 'send_heart_interaction grants');
select pg_temp.assert_rpc_execute_grants('public.get_latest_interactions()', 'get_latest_interactions grants');
select pg_temp.assert_rpc_execute_grants('public.mark_interactions_seen(uuid[])', 'mark_interactions_seen grants');
select pg_temp.assert_rpc_execute_grants('public.get_widget_status()', 'get_widget_status grants');
select pg_temp.assert_rpc_execute_grants('public.delete_account_data()', 'delete_account_data grants');
select pg_temp.assert_rpc_execute_grants('public.record_analytics_event(text,jsonb)', 'record_analytics_event grants');

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{}', true);
set local role authenticated;

select pg_temp.assert_raises(
  'select public.set_sharing_paused(true)',
  'not_authenticated',
  'unauthenticated set_sharing_paused is rejected'
);

select pg_temp.assert_raises(
  'select public.get_sharing_state()',
  'not_authenticated',
  'unauthenticated get_sharing_state is rejected'
);

select pg_temp.assert_raises(
  'select public.send_heart_interaction()',
  'not_authenticated',
  'unauthenticated send_heart_interaction is rejected'
);

select pg_temp.assert_raises(
  'select public.delete_account_data()',
  'not_authenticated',
  'unauthenticated delete_account_data is rejected'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{}''::jsonb)',
  'not_authenticated',
  'unauthenticated record_analytics_event is rejected'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001201', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001201","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001202', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001202","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001203', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001203","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001204', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001204","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001205', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001205","role":"authenticated"}', true);
set local role authenticated;
select public.ensure_user_profile();
reset role;

update public.users
set
  nickname = 'd15-d30-a',
  avatar_id = 'cat',
  updated_at = now(),
  deleted_at = null
where auth_user_id = '00000000-0000-0000-0000-000000001201';

update public.users
set
  nickname = 'd15-d30-b',
  avatar_id = 'fox',
  updated_at = now(),
  deleted_at = null
where auth_user_id = '00000000-0000-0000-0000-000000001202';

insert into public.couples (id, user_a_id, user_b_id, status)
values (
  '20000000-0000-0000-0000-000000001201',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001201'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001202'
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
  '20000000-0000-0000-0000-000000001204',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001205'
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

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001201', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001201","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_status_privacy_settings() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'allowedStatuses,updatedAt',
  'privacy settings payload exposes only public keys'
);

select pg_temp.assert_bool_eq(
  public.get_status_privacy_settings()->'allowedStatuses' ? 'offline',
  false,
  'privacy settings does not expose offline as a category'
);

select pg_temp.assert_bool_eq(
  public.get_status_privacy_settings()->'allowedStatuses' ? 'paused',
  false,
  'privacy settings does not expose paused as a category'
);

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.update_status_privacy_settings(array['offline']);
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  if not raised then
    raise exception 'offline privacy category update succeeded, expected invalid_allowed_status';
  end if;

  if message not like '%invalid_allowed_status%' then
    raise exception 'offline privacy category raised %, expected invalid_allowed_status', message;
  end if;
end;
$$;

select pg_temp.assert_text_eq(
  public.upsert_current_status(
    'online',
    now() - interval '10 minutes'
  )->>'statusCode',
  'online',
  'upsert_current_status stores active online before privacy tightening'
);

select public.update_status_privacy_settings(array['music', 'reading']);

select pg_temp.assert_text_eq(
  public.get_sharing_state()->>'statusCode',
  'offline',
  'update_status_privacy_settings downgrades existing online when disabled'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001202', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001202","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  public.get_partner_status()->>'statusCode',
  'offline',
  'partner sees downgraded status after privacy tightening'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001201'
  ),
  0,
  'partner direct current_status select cannot read hidden partner row'
);

select pg_temp.assert_text_eq(
  public.get_partner_status()->>'statusUpdatedAt',
  null,
  'partner does not see hidden status timestamp after privacy tightening'
);

select pg_temp.assert_text_eq(
  public.get_partner_status()->>'expiresAt',
  null,
  'partner does not see hidden status expiry after privacy tightening'
);

select pg_temp.assert_text_eq(
  public.get_widget_status()->>'statusCode',
  'offline',
  'widget sees downgraded status after privacy tightening'
);

select pg_temp.assert_text_eq(
  public.get_widget_status()->>'statusUpdatedAt',
  null,
  'widget does not see hidden status timestamp after privacy tightening'
);

select pg_temp.assert_text_eq(
  public.get_widget_status()->>'expiresAt',
  null,
  'widget does not see hidden status expiry after privacy tightening'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001201', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001201","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_raises(
  'select public.upsert_current_status(''offline'', now())',
  'invalid_status',
  'upsert_current_status rejects offline uploads'
);

select pg_temp.assert_raises(
  'select public.upsert_current_status(''paused'', now())',
  'invalid_status',
  'upsert_current_status rejects paused uploads'
);

select pg_temp.assert_text_eq(
  (
    select public.upsert_current_status(
      'gaming',
      now() - interval '10 minutes'
    )->>'statusCode'
  ),
  'offline',
  'upsert_current_status hides disabled categories without leaking online'
);

select pg_temp.assert_text_eq(
  (
    select public.upsert_current_status(
      'music',
      now() - interval '9 minutes'
    )->>'statusCode'
  ),
  'music',
  'upsert_current_status stores enabled categories'
);

select pg_temp.assert_text_eq(
  public.set_sharing_paused(true)->>'statusCode',
  'paused',
  'set_sharing_paused(true) returns paused'
);

select pg_temp.assert_bool_eq(
  (public.set_sharing_paused(true)->>'isPaused')::boolean,
  true,
  'set_sharing_paused(true) returns isPaused true'
);

select pg_temp.assert_bool_eq(
  (public.get_sharing_state()->>'sharing')::boolean,
  false,
  'get_sharing_state returns paused sharing after pause'
);

select pg_temp.assert_text_eq(
  public.upsert_current_status(
    'reading',
    now() + interval '1 minute'
  )->>'statusCode',
  'paused',
  'upsert_current_status does not resume paused sharing'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001201'
      and current_status.status_code = 'paused'
      and current_status.is_paused
  ),
  1,
  'paused sharing is persisted on current_status'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001202', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001202","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  public.get_partner_status()->>'statusCode',
  'paused',
  'partner sees paused sharing'
);

select pg_temp.assert_bool_eq(
  (public.get_partner_status()->>'isPaused')::boolean,
  true,
  'partner payload marks paused sharing'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001201', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001201","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  public.set_sharing_paused(false)->>'statusCode',
  'offline',
  'set_sharing_paused(false) respects disabled online privacy'
);

select pg_temp.assert_bool_eq(
  (public.get_sharing_state()->>'sharing')::boolean,
  true,
  'get_sharing_state returns sharing true after resume'
);

select pg_temp.assert_text_eq(
  public.upsert_current_status(
    'reading',
    now() + interval '2 minutes'
  )->>'statusCode',
  'reading',
  'upsert_current_status works normally after resume'
);

create temp table d15_d30_interaction_ids (id uuid);
create temp table d15_d30_foreign_interaction_ids (id uuid);
create temp table d15_d30_historical_interaction_ids (id uuid);

insert into d15_d30_interaction_ids (id)
select (public.send_heart_interaction()->'interaction'->>'id')::uuid;

select pg_temp.assert_text_eq(
  (
    select public.send_heart_interaction()->'interaction'->>'type'
  ),
  'heart',
  'send_heart_interaction returns a heart interaction'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.get_latest_interactions() as latest(payload),
      lateral jsonb_array_elements(payload->'interactions') as item
  ),
  0,
  'sender does not receive their own heart as unread'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001204', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001204","role":"authenticated"}', true);
set local role authenticated;

insert into d15_d30_foreign_interaction_ids (id)
select (public.send_heart_interaction()->'interaction'->>'id')::uuid;

reset role;

insert into public.couples (
  id,
  user_a_id,
  user_b_id,
  status,
  unlinked_at,
  unlinked_by
)
values (
  '20000000-0000-0000-0000-000000001203',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001203'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001202'
  ),
  'unlinked',
  now(),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001203'
  )
)
on conflict (id) do update
set
  user_a_id = excluded.user_a_id,
  user_b_id = excluded.user_b_id,
  status = excluded.status,
  unlinked_at = excluded.unlinked_at,
  unlinked_by = excluded.unlinked_by;

with inserted as (
  insert into public.interactions (
    couple_id,
    from_user_id,
    to_user_id,
    type
  )
  values (
    '20000000-0000-0000-0000-000000001203',
    (
      select id
      from public.users
      where auth_user_id = '00000000-0000-0000-0000-000000001203'
    ),
    (
      select id
      from public.users
      where auth_user_id = '00000000-0000-0000-0000-000000001202'
    ),
    'heart'
  )
  returning id
)
insert into d15_d30_historical_interaction_ids (id)
select id
from inserted;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001202', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001202","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_eq(
  jsonb_array_length(public.get_latest_interactions()->'interactions'),
  2,
  'receiver sees unread heart interactions'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_latest_interactions()->'interactions'->0 as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'createdAt,id,type',
  'latest interaction exposes only minimal public keys'
);

select pg_temp.assert_jsonb_eq(
  public.mark_interactions_seen(array(select id from d15_d30_foreign_interaction_ids)),
  jsonb_build_object('markedCount', 0),
  'mark_interactions_seen does not mark interactions for another receiver'
);

select pg_temp.assert_jsonb_eq(
  public.mark_interactions_seen(array(select id from d15_d30_historical_interaction_ids)),
  jsonb_build_object('markedCount', 0),
  'mark_interactions_seen does not mark interactions outside the current active couple'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.interactions
    join d15_d30_foreign_interaction_ids
      on d15_d30_foreign_interaction_ids.id = interactions.id
    where interactions.seen_at is null
  ),
  1,
  'foreign interaction remains unseen after cross-user mark attempt'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.interactions
    join d15_d30_historical_interaction_ids
      on d15_d30_historical_interaction_ids.id = interactions.id
    where interactions.seen_at is null
  ),
  1,
  'historical interaction remains unseen after current user mark attempt'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001202', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001202","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_jsonb_eq(
  public.mark_interactions_seen(array(select id from d15_d30_interaction_ids)),
  jsonb_build_object('markedCount', 1),
  'mark_interactions_seen returns the number of receiver-owned rows marked'
);

select pg_temp.assert_eq(
  jsonb_array_length(public.get_latest_interactions()->'interactions'),
  1,
  'mark_interactions_seen hides marked interactions from latest'
);

select pg_temp.assert_text_eq(
  public.get_widget_status()->>'statusCode',
  'reading',
  'get_widget_status returns partner current status'
);

select pg_temp.assert_text_eq(
  (
    with result as (
      select public.get_widget_status() as payload
    )
    select string_agg(key, ',' order by key)
    from result,
      lateral jsonb_object_keys(payload) as keys(key)
  ),
  'expiresAt,generatedAt,isPaused,paired,partner,statusCode,statusUpdatedAt',
  'get_widget_status returns a cacheable public payload'
);

reset role;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001203', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001203","role":"authenticated"}', true);
set local role authenticated;

do $$
declare
  raised boolean := false;
  message text;
begin
  begin
    perform public.send_heart_interaction();
  exception when others then
    raised := true;
    message := sqlerrm;
  end;

  reset role;

  if not raised then
    raise exception 'unpaired send_heart_interaction succeeded, expected active_couple_not_found';
  end if;

  if message not like '%active_couple_not_found%' then
    raise exception 'unpaired send_heart_interaction raised %, expected active_couple_not_found', message;
  end if;
end;
$$;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001201', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001201","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_text_eq(
  public.record_analytics_event(
    'app_opened',
    '{"success":true,"errorCode":"none"}'::jsonb
  )->>'eventName',
  'app_opened',
  'record_analytics_event accepts allowed event names and safe properties'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''unknown_event'', ''{}''::jsonb)',
  'invalid_analytics_event',
  'record_analytics_event rejects unknown event names'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''status_uploaded'', ''{"statusCode":"music"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects statusCode'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''status_uploaded'', ''{"status_code":"music"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects status_code'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''partner_status_viewed'', ''{"partner_status":"music"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects partner_status'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''status_uploaded'', ''{"nested":{"token":"secret"}}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects nested token'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"accessToken":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects camelCase accessToken'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"sessionToken":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects contained token keys'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"refresh-token":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects kebab-case refresh-token'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"password":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects password'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"clientSecret":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects contained secret keys'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"authorization":"bearer"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects authorization'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"apiKey":"secret"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects apiKey'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"phoneNumber":"123"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects phoneNumber'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"emailAddress":"a@example.test"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects emailAddress'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"package_name":"com.example.app"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects snake_case package_name'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"app_name":"Example"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects app_name'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"app_package":"com.example.app"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects app_package'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"usage_duration":"15m"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects usage_duration'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"chat_target":"user"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects chat_target'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"APPName":"Example"}''::jsonb)',
  'forbidden_analytics_property',
  'record_analytics_event rejects case-insensitive APPName'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', ''{"a":{"b":{"c":{"d":{"e":true}}}}}''::jsonb)',
  'analytics_properties_too_deep',
  'record_analytics_event rejects overly deep properties'
);

select pg_temp.assert_raises(
  'select public.record_analytics_event(''app_opened'', jsonb_build_object(''debug'', repeat(''x'', 4100)))',
  'analytics_properties_too_large',
  'record_analytics_event rejects oversized properties'
);

reset role;

select pg_temp.assert_raises(
  'insert into public.analytics_events (user_id, event_name, properties) values ((select id from public.users where auth_user_id = ''00000000-0000-0000-0000-000000001201''), ''unknown_event'', ''{}''::jsonb)',
  'analytics_events_event_name_check',
  'analytics_events table rejects unknown event names outside the RPC'
);

select pg_temp.assert_raises(
  'insert into public.analytics_events (user_id, event_name, properties) values ((select id from public.users where auth_user_id = ''00000000-0000-0000-0000-000000001201''), ''app_opened'', jsonb_build_object(''debug'', repeat(''x'', 4100)))',
  'analytics_events_properties_size_check',
  'analytics_events table rejects oversized properties outside the RPC'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.analytics_events,
      lateral jsonb_object_keys(analytics_events.properties) as keys(key)
    where keys.key in (
      'package',
      'packageName',
      'app',
      'appName',
      'duration',
      'usageDuration',
      'content',
      'chat',
      'chatTarget',
      'history',
      'statusCode',
      'partnerStatus',
      'token',
      'access_token',
      'refresh_token',
      'email',
      'phone'
    )
  ),
  0,
  'analytics_events stores no forbidden top-level properties'
);

insert into public.status_privacy_settings (user_id, allowed_statuses)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  array['music']
)
on conflict (user_id) do update
set
  allowed_statuses = excluded.allowed_statuses,
  updated_at = now();

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
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  '20000000-0000-0000-0000-000000001204',
  'music',
  now(),
  now() + interval '15 minutes',
  false
)
on conflict (user_id) do update
set
  couple_id = excluded.couple_id,
  status_code = excluded.status_code,
  status_updated_at = excluded.status_updated_at,
  expires_at = excluded.expires_at,
  is_paused = excluded.is_paused,
  updated_at = now();

insert into public.pair_invites (id, code_hash, created_by, used_by, used_at, expires_at)
values
  (
    '30000000-0000-0000-0000-000000001204',
    'd15-d30-delete-created',
    (
      select id
      from public.users
      where auth_user_id = '00000000-0000-0000-0000-000000001204'
    ),
    null,
    null,
    now() + interval '1 day'
  ),
  (
    '30000000-0000-0000-0000-000000001205',
    'd15-d30-delete-used',
    (
      select id
      from public.users
      where auth_user_id = '00000000-0000-0000-0000-000000001205'
    ),
    (
      select id
      from public.users
      where auth_user_id = '00000000-0000-0000-0000-000000001204'
    ),
    now(),
    now() + interval '1 day'
  )
on conflict (id) do update
set
  code_hash = excluded.code_hash,
  created_by = excluded.created_by,
  used_by = excluded.used_by,
  used_at = excluded.used_at,
  expires_at = excluded.expires_at;

insert into public.interactions (couple_id, from_user_id, to_user_id, type)
values (
  '20000000-0000-0000-0000-000000001204',
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001205'
  ),
  'heart'
);

insert into public.analytics_events (user_id, event_name, properties)
values (
  (
    select id
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  'app_opened',
  '{}'::jsonb
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001204', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001204","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_jsonb_eq(
  public.delete_account_data(),
  '{"deleted":true}'::jsonb,
  'delete_account_data returns deleted true'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
      and deleted_at is not null
  ),
  1,
  'delete_account_data marks the caller deleted'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.couples
    where id = '20000000-0000-0000-0000-000000001204'
      and status = 'unlinked'
      and unlinked_at is not null
  ),
  1,
  'delete_account_data unlinks active couples'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.current_status
    join public.users on users.id = current_status.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  0,
  'delete_account_data clears caller current_status'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.user_avatar
    join public.users on users.id = user_avatar.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  0,
  'delete_account_data clears caller user_avatar'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.status_privacy_settings
    join public.users on users.id = status_privacy_settings.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  0,
  'delete_account_data clears caller privacy settings'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.pair_invites
    join public.users creator on creator.id = pair_invites.created_by
    where creator.auth_user_id = '00000000-0000-0000-0000-000000001204'
       or pair_invites.used_by = (
         select id
         from public.users
         where auth_user_id = '00000000-0000-0000-0000-000000001204'
       )
  ),
  0,
  'delete_account_data clears caller pair_invites'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.interactions
    where from_user_id = (
         select id
         from public.users
         where auth_user_id = '00000000-0000-0000-0000-000000001204'
       )
       or to_user_id = (
         select id
         from public.users
         where auth_user_id = '00000000-0000-0000-0000-000000001204'
       )
  ),
  0,
  'delete_account_data clears caller interactions'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.analytics_events
    join public.users on users.id = analytics_events.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  0,
  'delete_account_data clears caller analytics'
);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000001204', true);
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000001204","role":"authenticated"}', true);
set local role authenticated;

select pg_temp.assert_raises(
  'select public.ensure_user_profile()',
  'account_deleted',
  'ensure_user_profile rejects a deleted auth user'
);

reset role;

select pg_temp.assert_eq(
  (
    select count(*)
    from public.users
    where auth_user_id = '00000000-0000-0000-0000-000000001204'
      and deleted_at is not null
  ),
  1,
  'ensure_user_profile does not clear deleted_at after account deletion'
);

select pg_temp.assert_eq(
  (
    select count(*)
    from public.user_avatar
    join public.users on users.id = user_avatar.user_id
    where users.auth_user_id = '00000000-0000-0000-0000-000000001204'
  ),
  0,
  'ensure_user_profile does not recreate user_avatar after account deletion'
);

rollback;
