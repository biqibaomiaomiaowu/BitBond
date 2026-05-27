create extension if not exists pgcrypto;

create or replace function public.analytics_normalized_property_key(
  next_key text
)
returns text
language sql
immutable
as $$
  select regexp_replace(lower(coalesce(next_key, '')), '[^a-z0-9]', '', 'g');
$$;

revoke execute on function public.analytics_normalized_property_key(text) from public;
revoke execute on function public.analytics_normalized_property_key(text) from anon;
revoke execute on function public.analytics_normalized_property_key(text) from authenticated;

create or replace function public.analytics_properties_depth(
  next_value jsonb
)
returns integer
language plpgsql
immutable
as $$
declare
  current_value jsonb;
  child_depth integer;
  max_child_depth integer := 0;
begin
  if next_value is null then
    return 0;
  end if;

  if jsonb_typeof(next_value) = 'object' then
    for current_value in
      select value
      from jsonb_each(next_value)
    loop
      child_depth := public.analytics_properties_depth(current_value);
      if child_depth > max_child_depth then
        max_child_depth := child_depth;
      end if;
    end loop;

    return max_child_depth + 1;
  elsif jsonb_typeof(next_value) = 'array' then
    for current_value in
      select value
      from jsonb_array_elements(next_value)
    loop
      child_depth := public.analytics_properties_depth(current_value);
      if child_depth > max_child_depth then
        max_child_depth := child_depth;
      end if;
    end loop;

    return max_child_depth + 1;
  end if;

  return 1;
end;
$$;

revoke execute on function public.analytics_properties_depth(jsonb) from public;
revoke execute on function public.analytics_properties_depth(jsonb) from anon;
revoke execute on function public.analytics_properties_depth(jsonb) from authenticated;

create or replace function public.analytics_properties_have_forbidden_key(
  next_value jsonb
)
returns boolean
language plpgsql
immutable
as $$
declare
  forbidden_normalized_keys text[] := array[
    'package',
    'packagename',
    'packageid',
    'app',
    'appname',
    'apppackage',
    'appid',
    'duration',
    'usageduration',
    'content',
    'chat',
    'chattarget',
    'history',
    'statuscode',
    'partnerstatus',
    'token',
    'accesstoken',
    'refreshtoken',
    'email',
    'emailaddress',
    'phone',
    'phonenumber'
  ];
  current_key text;
  normalized_key text;
  current_value jsonb;
begin
  if next_value is null then
    return false;
  end if;

  if jsonb_typeof(next_value) = 'object' then
    for current_key, current_value in
      select key, value
      from jsonb_each(next_value)
    loop
      normalized_key := public.analytics_normalized_property_key(current_key);

      if normalized_key = any(forbidden_normalized_keys) then
        return true;
      end if;

      if normalized_key like '%token%'
        or normalized_key like '%secret%'
        or normalized_key like '%password%'
        or normalized_key like '%authorization%'
        or normalized_key like '%apikey%'
      then
        return true;
      end if;

      if public.analytics_properties_have_forbidden_key(current_value) then
        return true;
      end if;
    end loop;
  elsif jsonb_typeof(next_value) = 'array' then
    for current_value in
      select value
      from jsonb_array_elements(next_value)
    loop
      if public.analytics_properties_have_forbidden_key(current_value) then
        return true;
      end if;
    end loop;
  end if;

  return false;
end;
$$;

revoke execute on function public.analytics_properties_have_forbidden_key(jsonb) from public;
revoke execute on function public.analytics_properties_have_forbidden_key(jsonb) from anon;
revoke execute on function public.analytics_properties_have_forbidden_key(jsonb) from authenticated;

create table if not exists public.status_privacy_settings (
  user_id uuid primary key references public.users(id) on delete cascade,
  share_enabled boolean not null default true,
  allowed_statuses text[] not null default array[
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting'
  ]::text[],
  hide_unknown boolean not null default true,
  updated_at timestamptz not null default now(),
  constraint status_privacy_settings_allowed_statuses_check check (
    array_position(allowed_statuses, null) is null
    and allowed_statuses <@ array[
      'short_video',
      'watching_show',
      'reading',
      'music',
      'gaming',
      'social',
      'online',
      'resting'
    ]::text[]
  )
);

create table if not exists public.interactions (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.couples(id) on delete cascade,
  from_user_id uuid not null references public.users(id) on delete cascade,
  to_user_id uuid not null references public.users(id) on delete cascade,
  type text not null default 'heart',
  created_at timestamptz not null default now(),
  seen_at timestamptz,
  expires_at timestamptz not null default now() + interval '30 days',
  constraint interactions_type_check check (type in ('heart')),
  constraint interactions_distinct_users_check check (from_user_id <> to_user_id)
);

create table if not exists public.analytics_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.users(id) on delete cascade,
  event_name text not null,
  properties jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint analytics_events_event_name_check check (
    event_name in (
      'app_opened',
      'auth_created',
      'pair_invite_created',
      'pair_invite_accepted',
      'pair_success',
      'usage_access_prompt_viewed',
      'usage_access_granted',
      'usage_access_denied',
      'avatar_selected',
      'status_detected',
      'status_uploaded',
      'partner_status_viewed',
      'share_paused',
      'share_resumed',
      'heart_sent',
      'heart_seen',
      'widget_added',
      'widget_refreshed',
      'unlink_done',
      'account_deleted'
    )
  ),
  constraint analytics_events_properties_object_check check (
    jsonb_typeof(properties) = 'object'
  ),
  constraint analytics_events_properties_size_check check (
    octet_length(properties::text) <= 4096
      and public.analytics_properties_depth(properties) <= 4
  ),
  constraint analytics_events_properties_forbidden_check check (
    not public.analytics_properties_have_forbidden_key(properties)
  )
);

alter table public.analytics_events
  drop constraint if exists analytics_events_event_name_check;

alter table public.analytics_events
  add constraint analytics_events_event_name_check check (
    event_name in (
      'app_opened',
      'auth_created',
      'pair_invite_created',
      'pair_invite_accepted',
      'pair_success',
      'usage_access_prompt_viewed',
      'usage_access_granted',
      'usage_access_denied',
      'avatar_selected',
      'status_detected',
      'status_uploaded',
      'partner_status_viewed',
      'share_paused',
      'share_resumed',
      'heart_sent',
      'heart_seen',
      'widget_added',
      'widget_refreshed',
      'unlink_done',
      'account_deleted'
    )
  );

alter table public.analytics_events
  drop constraint if exists analytics_events_properties_size_check;

alter table public.analytics_events
  add constraint analytics_events_properties_size_check check (
    octet_length(properties::text) <= 4096
      and public.analytics_properties_depth(properties) <= 4
  );

alter table public.analytics_events
  drop constraint if exists analytics_events_properties_forbidden_check;

alter table public.analytics_events
  add constraint analytics_events_properties_forbidden_check check (
    not public.analytics_properties_have_forbidden_key(properties)
  );

alter table public.status_privacy_settings enable row level security;
alter table public.interactions enable row level security;
alter table public.analytics_events enable row level security;

drop policy if exists current_status_select_self_or_couple on public.current_status;
drop policy if exists current_status_select_self on public.current_status;

create policy current_status_select_self
  on public.current_status
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.users current_user_profile
      where current_user_profile.auth_user_id = auth.uid()
        and current_user_profile.id = current_status.user_id
    )
  );

drop policy if exists status_privacy_settings_select_self on public.status_privacy_settings;

create policy status_privacy_settings_select_self
  on public.status_privacy_settings
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.users current_user_profile
      where current_user_profile.auth_user_id = auth.uid()
        and current_user_profile.id = status_privacy_settings.user_id
    )
  );

create index if not exists idx_status_privacy_settings_updated_at
  on public.status_privacy_settings(updated_at);

create index if not exists idx_interactions_to_user
  on public.interactions(to_user_id);

create index if not exists idx_interactions_to_user_seen
  on public.interactions(to_user_id, seen_at, created_at desc);

create index if not exists idx_interactions_couple_created
  on public.interactions(couple_id, created_at desc);

create index if not exists idx_analytics_event_name
  on public.analytics_events(event_name);

create index if not exists idx_analytics_user_id
  on public.analytics_events(user_id);

create index if not exists idx_analytics_created_at
  on public.analytics_events(created_at);

create or replace function public.ensure_user_profile()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  default_avatar_id text;
  existing_user public.users%rowtype;
  ensured_user public.users%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select *
  into existing_user
  from public.users
  where users.auth_user_id = current_auth_user_id
  limit 1;

  if existing_user.id is not null
    and existing_user.deleted_at is not null
  then
    raise exception 'account_deleted';
  end if;

  select avatars.id
  into default_avatar_id
  from public.avatars
  where avatars.is_active
  order by
    case when avatars.id = 'cat' then 0 else 1 end,
    avatars.sort_order,
    avatars.id
  limit 1;

  if default_avatar_id is null then
    raise exception 'active_avatar_not_found';
  end if;

  insert into public.users (auth_user_id, avatar_id)
  values (current_auth_user_id, default_avatar_id)
  on conflict (auth_user_id) do update
  set
    avatar_id = coalesce(public.users.avatar_id, excluded.avatar_id),
    updated_at = now()
  where public.users.deleted_at is null
  returning *
  into ensured_user;

  if ensured_user.id is null then
    raise exception 'account_deleted';
  end if;

  insert into public.user_avatar (user_id, avatar_id)
  values (ensured_user.id, ensured_user.avatar_id)
  on conflict (user_id) do update
  set
    avatar_id = excluded.avatar_id,
    updated_at = now();

  return jsonb_build_object(
    'user',
    jsonb_build_object(
      'id', ensured_user.id,
      'nickname', ensured_user.nickname,
      'avatarId', ensured_user.avatar_id
    )
  );
end;
$$;

revoke execute on function public.ensure_user_profile() from public;
grant execute on function public.ensure_user_profile() to anon;
grant execute on function public.ensure_user_profile() to authenticated;

create or replace function public.get_status_privacy_settings()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  settings public.status_privacy_settings%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  insert into public.status_privacy_settings (user_id)
  values (caller_user_id)
  on conflict (user_id) do nothing;

  select *
  into settings
  from public.status_privacy_settings
  where user_id = caller_user_id;

  return jsonb_build_object(
    'allowedStatuses', to_jsonb(settings.allowed_statuses),
    'updatedAt', settings.updated_at
  );
end;
$$;

revoke execute on function public.get_status_privacy_settings() from public;
revoke execute on function public.get_status_privacy_settings() from anon;
grant execute on function public.get_status_privacy_settings() to authenticated;

create or replace function public.update_status_privacy_settings(
  next_allowed_statuses text[]
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  allowed_codes text[] := array[
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting'
  ];
  normalized_allowed_statuses text[];
  settings public.status_privacy_settings%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_allowed_statuses is null then
    raise exception 'invalid_allowed_statuses';
  end if;

  if exists (
    select 1
    from unnest(next_allowed_statuses) as requested(status_code)
    where requested.status_code is null
      or not (requested.status_code = any(allowed_codes))
  ) then
    raise exception 'invalid_allowed_status';
  end if;

  select coalesce(array_agg(allowed.status_code order by allowed.ordinality), array[]::text[])
  into normalized_allowed_statuses
  from unnest(allowed_codes) with ordinality as allowed(status_code, ordinality)
  where allowed.status_code = any(next_allowed_statuses);

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  insert into public.status_privacy_settings (
    user_id,
    allowed_statuses,
    updated_at
  )
  values (
    caller_user_id,
    normalized_allowed_statuses,
    now()
  )
  on conflict (user_id) do update
  set
    allowed_statuses = excluded.allowed_statuses,
    updated_at = now()
  returning *
  into settings;

  update public.current_status
  set
    status_code = case
      when 'online' = any(normalized_allowed_statuses) then 'online'
      else 'offline'
    end,
    updated_at = now()
  where current_status.user_id = caller_user_id
    and coalesce(current_status.is_paused, false) = false
    and current_status.status_code <> 'offline'
    and not (current_status.status_code = any(normalized_allowed_statuses));

  return jsonb_build_object(
    'allowedStatuses', to_jsonb(settings.allowed_statuses),
    'updatedAt', settings.updated_at
  );
end;
$$;

revoke execute on function public.update_status_privacy_settings(text[]) from public;
revoke execute on function public.update_status_privacy_settings(text[]) from anon;
grant execute on function public.update_status_privacy_settings(text[]) to authenticated;

create or replace function public.upsert_current_status(
  next_status_code text,
  next_status_updated_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple_id uuid;
  stored_status public.current_status%rowtype;
  allowed_codes text[] := array[
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting'
  ];
  caller_allowed_statuses text[];
  effective_status_code text;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_status_code is null
    or not (next_status_code = any(allowed_codes))
  then
    raise exception 'invalid_status';
  end if;

  if next_status_updated_at is null then
    raise exception 'invalid_status_updated_at';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'profile_not_found';
  end if;

  select *
  into stored_status
  from public.current_status
  where current_status.user_id = caller_user_id
  for update;

  if stored_status.user_id is not null
    and coalesce(stored_status.is_paused, false)
  then
    return jsonb_build_object(
      'statusCode', 'paused',
      'statusUpdatedAt', stored_status.status_updated_at,
      'expiresAt', stored_status.expires_at
    );
  end if;

  select coalesce(status_privacy_settings.allowed_statuses, allowed_codes)
  into caller_allowed_statuses
  from public.users
  left join public.status_privacy_settings
    on status_privacy_settings.user_id = users.id
  where users.id = caller_user_id;

  if not (next_status_code = any(caller_allowed_statuses)) then
    effective_status_code := case
      when 'online' = any(caller_allowed_statuses) then 'online'
      else 'offline'
    end;
  else
    effective_status_code := next_status_code;
  end if;

  select couples.id
  into current_couple_id
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  insert into public.current_status (
    user_id,
    couple_id,
    status_code,
    status_updated_at,
    expires_at,
    is_paused
  )
  values (
    caller_user_id,
    current_couple_id,
    effective_status_code,
    next_status_updated_at,
    next_status_updated_at + interval '15 minutes',
    false
  )
  on conflict (user_id) do update
  set
    couple_id = excluded.couple_id,
    status_code = excluded.status_code,
    status_updated_at = excluded.status_updated_at,
    expires_at = excluded.expires_at,
    is_paused = false,
    updated_at = now()
  where coalesce(current_status.is_paused, false) = false
    and excluded.status_updated_at >= current_status.status_updated_at
  returning *
  into stored_status;

  if stored_status.user_id is null then
    select *
    into stored_status
    from public.current_status
    where current_status.user_id = caller_user_id;
  end if;

  return jsonb_build_object(
    'statusCode', stored_status.status_code,
    'statusUpdatedAt', stored_status.status_updated_at,
    'expiresAt', stored_status.expires_at
  );
end;
$$;

revoke execute on function public.upsert_current_status(text, timestamptz) from public;
revoke execute on function public.upsert_current_status(text, timestamptz) from anon;
grant execute on function public.upsert_current_status(text, timestamptz) to authenticated;

create or replace function public.set_sharing_paused(
  next_is_paused boolean
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple_id uuid;
  stored_status public.current_status%rowtype;
  allowed_codes text[] := array[
    'short_video',
    'watching_show',
    'reading',
    'music',
    'gaming',
    'social',
    'online',
    'resting'
  ];
  caller_allowed_statuses text[];
  resume_status_code text;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_is_paused is null then
    raise exception 'invalid_pause_state';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select coalesce(status_privacy_settings.allowed_statuses, allowed_codes)
  into caller_allowed_statuses
  from public.users
  left join public.status_privacy_settings
    on status_privacy_settings.user_id = users.id
  where users.id = caller_user_id;

  resume_status_code := case
    when 'online' = any(caller_allowed_statuses) then 'online'
    else 'offline'
  end;

  select couples.id
  into current_couple_id
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  select *
  into stored_status
  from public.current_status
  where current_status.user_id = caller_user_id
  for update;

  if next_is_paused then
    insert into public.current_status (
      user_id,
      couple_id,
      status_code,
      status_updated_at,
      expires_at,
      is_paused
    )
    values (
      caller_user_id,
      current_couple_id,
      'paused',
      now(),
      now() + interval '15 minutes',
      true
    )
    on conflict (user_id) do update
    set
      couple_id = excluded.couple_id,
      status_code = 'paused',
      status_updated_at = excluded.status_updated_at,
      expires_at = excluded.expires_at,
      is_paused = true,
      updated_at = now()
    returning *
    into stored_status;
  elsif stored_status.user_id is not null
    and coalesce(stored_status.is_paused, false) = false
    and stored_status.status_code <> 'paused'
  then
    update public.current_status
    set
      couple_id = current_couple_id,
      is_paused = false,
      updated_at = now()
    where current_status.user_id = caller_user_id
    returning *
    into stored_status;
  else
    insert into public.current_status (
      user_id,
      couple_id,
      status_code,
      status_updated_at,
      expires_at,
      is_paused
    )
    values (
      caller_user_id,
      current_couple_id,
      resume_status_code,
      now(),
      now() + interval '15 minutes',
      false
    )
    on conflict (user_id) do update
    set
      couple_id = excluded.couple_id,
      status_code = resume_status_code,
      status_updated_at = excluded.status_updated_at,
      expires_at = excluded.expires_at,
      is_paused = false,
      updated_at = now()
    returning *
    into stored_status;
  end if;

  return jsonb_build_object(
    'statusCode', stored_status.status_code,
    'statusUpdatedAt', stored_status.status_updated_at,
    'expiresAt', stored_status.expires_at,
    'isPaused', stored_status.is_paused
  );
end;
$$;

revoke execute on function public.set_sharing_paused(boolean) from public;
revoke execute on function public.set_sharing_paused(boolean) from anon;
grant execute on function public.set_sharing_paused(boolean) to authenticated;

create or replace function public.get_sharing_state()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  stored_status public.current_status%rowtype;
  sharing_paused boolean;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select *
  into stored_status
  from public.current_status
  where current_status.user_id = caller_user_id
  limit 1;

  if stored_status.user_id is null then
    return jsonb_build_object(
      'sharing', true,
      'statusCode', 'online',
      'statusUpdatedAt', null,
      'expiresAt', null,
      'isPaused', false
    );
  end if;

  sharing_paused := coalesce(stored_status.is_paused, false)
    or stored_status.status_code = 'paused';

  return jsonb_build_object(
    'sharing', not sharing_paused,
    'statusCode', case when sharing_paused then 'paused' else stored_status.status_code end,
    'statusUpdatedAt', stored_status.status_updated_at,
    'expiresAt', stored_status.expires_at,
    'isPaused', sharing_paused
  );
end;
$$;

revoke execute on function public.get_sharing_state() from public;
revoke execute on function public.get_sharing_state() from anon;
grant execute on function public.get_sharing_state() to authenticated;

create or replace function public.send_heart_interaction()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple public.couples%rowtype;
  partner_user_id uuid;
  created_interaction public.interactions%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select *
  into current_couple
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  if current_couple.id is null then
    raise exception 'active_couple_not_found';
  end if;

  partner_user_id := case
    when current_couple.user_a_id = caller_user_id then current_couple.user_b_id
    else current_couple.user_a_id
  end;

  insert into public.interactions (
    couple_id,
    from_user_id,
    to_user_id,
    type
  )
  values (
    current_couple.id,
    caller_user_id,
    partner_user_id,
    'heart'
  )
  returning *
  into created_interaction;

  return jsonb_build_object(
    'interaction',
    jsonb_build_object(
      'id', created_interaction.id,
      'type', created_interaction.type,
      'createdAt', created_interaction.created_at
    )
  );
end;
$$;

revoke execute on function public.send_heart_interaction() from public;
revoke execute on function public.send_heart_interaction() from anon;
grant execute on function public.send_heart_interaction() to authenticated;

create or replace function public.get_latest_interactions()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple_id uuid;
  unread_interactions jsonb;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select couples.id
  into current_couple_id
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  if current_couple_id is null then
    return jsonb_build_object('interactions', '[]'::jsonb);
  end if;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', interaction_rows.id,
        'type', interaction_rows.type,
        'createdAt', interaction_rows.created_at
      )
      order by interaction_rows.created_at desc, interaction_rows.id desc
    ),
    '[]'::jsonb
  )
  into unread_interactions
  from (
    select interactions.id, interactions.type, interactions.created_at
    from public.interactions
    where interactions.couple_id = current_couple_id
      and interactions.to_user_id = caller_user_id
      and interactions.type = 'heart'
      and interactions.seen_at is null
      and interactions.expires_at > now()
    order by interactions.created_at desc, interactions.id desc
    limit 20
  ) interaction_rows;

  return jsonb_build_object('interactions', unread_interactions);
end;
$$;

revoke execute on function public.get_latest_interactions() from public;
revoke execute on function public.get_latest_interactions() from anon;
grant execute on function public.get_latest_interactions() to authenticated;

create or replace function public.mark_interactions_seen(
  next_interaction_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple_id uuid;
  marked_count bigint;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_interaction_ids is null then
    raise exception 'invalid_interaction_ids';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select couples.id
  into current_couple_id
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  if current_couple_id is null then
    return jsonb_build_object('markedCount', 0);
  end if;

  with marked as (
    update public.interactions
    set
      seen_at = now()
    where interactions.to_user_id = caller_user_id
      and interactions.couple_id = current_couple_id
      and interactions.type = 'heart'
      and interactions.id = any(next_interaction_ids)
      and interactions.seen_at is null
    returning interactions.id
  )
  select count(*)
  into marked_count
  from marked;

  return jsonb_build_object('markedCount', marked_count);
end;
$$;

revoke execute on function public.mark_interactions_seen(uuid[]) from public;
revoke execute on function public.mark_interactions_seen(uuid[]) from anon;
grant execute on function public.mark_interactions_seen(uuid[]) to authenticated;

create or replace function public.get_partner_status()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  current_couple public.couples%rowtype;
  partner_user_id uuid;
  partner_nickname text;
  partner_avatar_id text;
  partner_status public.current_status%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  select *
  into current_couple
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  limit 1;

  if current_couple.id is null then
    return jsonb_build_object('paired', false);
  end if;

  partner_user_id := case
    when current_couple.user_a_id = caller_user_id then current_couple.user_b_id
    else current_couple.user_a_id
  end;

  select users.nickname, users.avatar_id
  into partner_nickname, partner_avatar_id
  from public.users
  where users.id = partner_user_id
    and users.deleted_at is null
  limit 1;

  select *
  into partner_status
  from public.current_status
  where current_status.user_id = partner_user_id
    and current_status.couple_id = current_couple.id
  limit 1;

  if partner_status.user_id is not null and partner_status.is_paused then
    return jsonb_build_object(
      'paired', true,
      'partner',
      jsonb_build_object(
        'nickname', partner_nickname,
        'avatarId', partner_avatar_id
      ),
      'statusCode', 'paused',
      'statusUpdatedAt', null,
      'expiresAt', null,
      'isPaused', true
    );
  end if;

  if partner_status.user_id is not null
    and coalesce(partner_status.is_paused, false) = false
    and partner_status.status_code <> 'offline'
    and partner_status.expires_at > now()
  then
    return jsonb_build_object(
      'paired', true,
      'partner',
      jsonb_build_object(
        'nickname', partner_nickname,
        'avatarId', partner_avatar_id
      ),
      'statusCode', partner_status.status_code,
      'statusUpdatedAt', partner_status.status_updated_at,
      'expiresAt', partner_status.expires_at,
      'isPaused', false
    );
  end if;

  return jsonb_build_object(
    'paired', true,
    'partner',
    jsonb_build_object(
      'nickname', partner_nickname,
      'avatarId', partner_avatar_id
    ),
    'statusCode', 'offline',
    'statusUpdatedAt', null,
    'expiresAt', null,
    'isPaused', false
  );
end;
$$;

revoke execute on function public.get_partner_status() from public;
revoke execute on function public.get_partner_status() from anon;
grant execute on function public.get_partner_status() to authenticated;

create or replace function public.get_widget_status()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
begin
  return public.get_partner_status() || jsonb_build_object('generatedAt', now());
end;
$$;

revoke execute on function public.get_widget_status() from public;
revoke execute on function public.get_widget_status() from anon;
grant execute on function public.get_widget_status() to authenticated;

create or replace function public.delete_account_data()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  update public.couples
  set
    status = 'unlinked',
    unlinked_at = coalesce(couples.unlinked_at, now()),
    unlinked_by = coalesce(couples.unlinked_by, caller_user_id)
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id);

  delete from public.interactions
  where interactions.from_user_id = caller_user_id
     or interactions.to_user_id = caller_user_id;

  delete from public.analytics_events
  where analytics_events.user_id = caller_user_id;

  delete from public.current_status
  where current_status.user_id = caller_user_id;

  delete from public.status_privacy_settings
  where status_privacy_settings.user_id = caller_user_id;

  delete from public.user_avatar
  where user_avatar.user_id = caller_user_id;

  delete from public.pair_invites
  where pair_invites.created_by = caller_user_id
     or pair_invites.used_by = caller_user_id;

  update public.users
  set
    deleted_at = coalesce(users.deleted_at, now()),
    updated_at = now()
  where users.id = caller_user_id;

  return '{"deleted":true}'::jsonb;
end;
$$;

revoke execute on function public.delete_account_data() from public;
revoke execute on function public.delete_account_data() from anon;
grant execute on function public.delete_account_data() to authenticated;

create or replace function public.record_analytics_event(
  next_event_name text,
  next_properties jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  allowed_event_names text[] := array[
    'app_opened',
    'auth_created',
    'pair_invite_created',
    'pair_invite_accepted',
    'pair_success',
    'usage_access_prompt_viewed',
    'usage_access_granted',
    'usage_access_denied',
    'avatar_selected',
    'status_detected',
    'status_uploaded',
    'partner_status_viewed',
    'share_paused',
    'share_resumed',
    'heart_sent',
    'heart_seen',
    'widget_added',
    'widget_refreshed',
    'unlink_done',
    'account_deleted'
  ];
  stored_event public.analytics_events%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_event_name is null
    or not (next_event_name = any(allowed_event_names))
  then
    raise exception 'invalid_analytics_event';
  end if;

  if next_properties is null
    or jsonb_typeof(next_properties) <> 'object'
  then
    raise exception 'invalid_analytics_properties';
  end if;

  if octet_length(next_properties::text) > 4096 then
    raise exception 'analytics_properties_too_large';
  end if;

  if public.analytics_properties_depth(next_properties) > 4 then
    raise exception 'analytics_properties_too_deep';
  end if;

  if public.analytics_properties_have_forbidden_key(next_properties) then
    raise exception 'forbidden_analytics_property';
  end if;

  select users.id
  into caller_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if caller_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  insert into public.analytics_events (
    user_id,
    event_name,
    properties
  )
  values (
    caller_user_id,
    next_event_name,
    next_properties
  )
  returning *
  into stored_event;

  return jsonb_build_object(
    'recorded', true,
    'eventId', stored_event.id,
    'eventName', stored_event.event_name,
    'createdAt', stored_event.created_at
  );
end;
$$;

revoke execute on function public.record_analytics_event(text, jsonb) from public;
revoke execute on function public.record_analytics_event(text, jsonb) from anon;
grant execute on function public.record_analytics_event(text, jsonb) to authenticated;
