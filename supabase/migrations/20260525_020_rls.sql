alter table public.avatars enable row level security;
alter table public.users enable row level security;
alter table public.couples enable row level security;
alter table public.pair_invites enable row level security;
alter table public.user_avatar enable row level security;
alter table public.current_status enable row level security;

drop policy if exists users_select_self on public.users;
drop policy if exists users_update_self on public.users;
drop policy if exists avatars_are_public_readable on public.avatars;
drop policy if exists avatars_select_all on public.avatars;
drop policy if exists user_avatar_select_self on public.user_avatar;
drop policy if exists current_status_select_self_or_couple on public.current_status;
drop policy if exists couples_select_participant on public.couples;
drop policy if exists pair_invites_select_creator on public.pair_invites;

create policy users_select_self
  on public.users
  for select
  to authenticated
  using (auth_user_id = auth.uid());

create policy users_update_self
  on public.users
  for update
  to authenticated
  using (auth_user_id = auth.uid())
  with check (auth_user_id = auth.uid());

create policy avatars_select_all
  on public.avatars
  for select
  to anon, authenticated
  using (true);

create policy user_avatar_select_self
  on public.user_avatar
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.users current_user_profile
      where current_user_profile.auth_user_id = auth.uid()
        and current_user_profile.id = user_avatar.user_id
    )
  );

create policy current_status_select_self_or_couple
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
    or exists (
      select 1
      from public.couples visible_couple
      join public.users current_user_profile
        on current_user_profile.auth_user_id = auth.uid()
       and current_user_profile.id in (
          visible_couple.user_a_id,
          visible_couple.user_b_id
        )
      where visible_couple.id = current_status.couple_id
        and visible_couple.status = 'active'
        and visible_couple.unlinked_at is null
        and current_status.user_id in (
          visible_couple.user_a_id,
          visible_couple.user_b_id
        )
    )
  );

create policy couples_select_participant
  on public.couples
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.users current_user_profile
      where current_user_profile.auth_user_id = auth.uid()
        and current_user_profile.id in (
          couples.user_a_id,
          couples.user_b_id
        )
    )
  );

create policy pair_invites_select_creator
  on public.pair_invites
  for select
  to authenticated
  using (
    exists (
      select 1
      from public.users current_user_profile
      where current_user_profile.auth_user_id = auth.uid()
        and current_user_profile.id = pair_invites.created_by
    )
  );
