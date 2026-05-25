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
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  if next_status_code is null
    or next_status_code not in (
      'short_video',
      'watching_show',
      'reading',
      'music',
      'gaming',
      'social',
      'online',
      'resting',
      'offline',
      'paused'
    )
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
    next_status_code,
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
  where excluded.status_updated_at >= current_status.status_updated_at
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
