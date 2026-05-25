create or replace function public.unlink_current_couple()
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_auth_user_id uuid;
  caller_user_id uuid;
  active_couple_id uuid;
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
  into active_couple_id
  from public.couples
  where couples.status = 'active'
    and couples.unlinked_at is null
    and caller_user_id in (couples.user_a_id, couples.user_b_id)
  order by couples.created_at desc, couples.id
  for update
  limit 1;

  if active_couple_id is not null then
    update public.couples
    set
      status = 'unlinked',
      unlinked_at = now(),
      unlinked_by = caller_user_id
    where couples.id = active_couple_id
      and couples.status = 'active'
      and couples.unlinked_at is null;
  end if;

  return '{"unlinked":true}'::jsonb;
end;
$$;

revoke execute on function public.unlink_current_couple() from public;
revoke execute on function public.unlink_current_couple() from anon;
grant execute on function public.unlink_current_couple() to authenticated;
