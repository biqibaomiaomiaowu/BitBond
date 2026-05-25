create or replace function public.set_my_avatar(next_avatar_id text)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  normalized_avatar_id text;
  ensured_profile jsonb;
  current_user_id uuid;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  normalized_avatar_id := btrim(coalesce(next_avatar_id, ''));

  if length(normalized_avatar_id) = 0 then
    raise exception 'invalid_avatar_id';
  end if;

  if not exists (
    select 1
    from public.avatars
    where avatars.id = normalized_avatar_id
      and avatars.is_active
  ) then
    raise exception 'avatar_not_found';
  end if;

  ensured_profile := public.ensure_user_profile();
  current_user_id := (ensured_profile -> 'user' ->> 'id')::uuid;

  update public.users
  set
    avatar_id = normalized_avatar_id,
    deleted_at = null,
    updated_at = now()
  where users.id = current_user_id
    and users.auth_user_id = current_auth_user_id
  returning users.id
  into current_user_id;

  if current_user_id is null then
    raise exception 'not_authenticated';
  end if;

  insert into public.user_avatar (user_id, avatar_id)
  values (current_user_id, normalized_avatar_id)
  on conflict (user_id) do update
  set
    avatar_id = excluded.avatar_id,
    updated_at = now();

  return jsonb_build_object('avatarId', normalized_avatar_id);
end;
$$;

revoke execute on function public.set_my_avatar(text) from public;
revoke execute on function public.set_my_avatar(text) from anon;
grant execute on function public.set_my_avatar(text) to authenticated;
