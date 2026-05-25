create or replace function public.accept_pair_invite(invite_code text)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  accepter_user_id uuid;
  normalized_invite_code text;
  invite_code_hash text;
  locked_invite public.pair_invites%rowtype;
  latest_invite public.pair_invites%rowtype;
  new_couple_id uuid;
  partner_nickname text;
  partner_avatar_id text;
  invite_used_at timestamptz;
begin
  current_auth_user_id := auth.uid();

  if current_auth_user_id is null then
    raise exception 'not_authenticated';
  end if;

  normalized_invite_code := btrim(coalesce(invite_code, ''));

  if normalized_invite_code = '' then
    raise exception 'invalid_invite_code';
  end if;

  select users.id
  into accepter_user_id
  from public.users
  where users.auth_user_id = current_auth_user_id
    and users.deleted_at is null
  limit 1;

  if accepter_user_id is null then
    raise exception 'user_profile_not_found';
  end if;

  invite_code_hash := encode(extensions.digest(normalized_invite_code, 'sha256'), 'hex');

  select *
  into locked_invite
  from public.pair_invites
  where pair_invites.code_hash = invite_code_hash
    and pair_invites.used_at is null
    and pair_invites.expires_at > now()
  order by pair_invites.created_at desc, pair_invites.id desc
  for update
  limit 1;

  if locked_invite.id is null then
    select *
    into latest_invite
    from public.pair_invites
    where pair_invites.code_hash = invite_code_hash
    order by pair_invites.created_at desc, pair_invites.id desc
    limit 1;

    if latest_invite.id is null then
      raise exception 'invalid_invite_code';
    end if;

    if latest_invite.used_at is not null then
      raise exception 'invite_used';
    end if;

    if latest_invite.expires_at <= now() then
      raise exception 'invite_expired';
    end if;

    raise exception 'invalid_invite_code';
  end if;

  if locked_invite.created_by = accepter_user_id then
    raise exception 'cannot_pair_self';
  end if;

  perform 1
  from public.users
  where users.id in (locked_invite.created_by, accepter_user_id)
  order by users.id
  for update;

  if exists (
    select 1
    from public.couples
    where couples.status = 'active'
      and couples.unlinked_at is null
      and (
        locked_invite.created_by in (couples.user_a_id, couples.user_b_id)
        or accepter_user_id in (couples.user_a_id, couples.user_b_id)
      )
  ) then
    raise exception 'already_paired';
  end if;

  insert into public.couples (user_a_id, user_b_id, status)
  values (locked_invite.created_by, accepter_user_id, 'active')
  returning couples.id
  into new_couple_id;

  update public.pair_invites
  set
    used_at = now(),
    used_by = accepter_user_id
  where pair_invites.id = locked_invite.id
    and pair_invites.used_at is null
  returning pair_invites.used_at
  into invite_used_at;

  if invite_used_at is null then
    raise exception 'invite_used';
  end if;

  select users.nickname, users.avatar_id
  into partner_nickname, partner_avatar_id
  from public.users
  where users.id = locked_invite.created_by
  limit 1;

  return jsonb_build_object(
    'coupleId', new_couple_id,
    'partner',
    jsonb_build_object(
      'nickname', partner_nickname,
      'avatarId', partner_avatar_id
    )
  );
end;
$$;

revoke execute on function public.accept_pair_invite(text) from public;
revoke execute on function public.accept_pair_invite(text) from anon;
grant execute on function public.accept_pair_invite(text) to authenticated;
