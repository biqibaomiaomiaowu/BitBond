create or replace function public.get_current_couple()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  active_couple public.couples%rowtype;
  partner_user_id uuid;
  partner_nickname text;
  partner_avatar_id text;
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
  into active_couple
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id desc
  limit 1;

  if active_couple.id is null then
    return '{"paired":false}'::jsonb;
  end if;

  partner_user_id := case
    when active_couple.user_a_id = caller_user_id then active_couple.user_b_id
    else active_couple.user_a_id
  end;

  select users.nickname, users.avatar_id
  into partner_nickname, partner_avatar_id
  from public.users
  where users.id = partner_user_id
    and users.deleted_at is null
  limit 1;

  return jsonb_build_object(
    'paired', true,
    'coupleId', active_couple.id,
    'partner',
    jsonb_build_object(
      'nickname', partner_nickname,
      'avatarId', partner_avatar_id
    )
  );
end;
$$;

revoke execute on function public.get_current_couple() from public;
revoke execute on function public.get_current_couple() from anon;
grant execute on function public.get_current_couple() to authenticated;
