create or replace function public.ensure_user_profile()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  default_avatar_id text;
  ensured_user public.users%rowtype;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
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
    deleted_at = null,
    updated_at = now()
  returning *
  into ensured_user;

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
