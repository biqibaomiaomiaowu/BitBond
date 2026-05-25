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
