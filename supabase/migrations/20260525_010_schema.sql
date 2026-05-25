create extension if not exists pgcrypto;

create table if not exists public.avatars (
  id text primary key,
  name text not null,
  asset_key text not null unique,
  is_active boolean not null default true,
  sort_order integer not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint avatars_id_not_blank check (length(btrim(id)) > 0),
  constraint avatars_name_not_blank check (length(btrim(name)) > 0),
  constraint avatars_asset_key_not_blank check (length(btrim(asset_key)) > 0)
);

create table if not exists public.users (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid unique not null,
  nickname text not null default '用户',
  avatar_id text references public.avatars(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table if not exists public.couples (
  id uuid primary key default gen_random_uuid(),
  user_a_id uuid not null references public.users(id) on delete cascade,
  user_b_id uuid not null references public.users(id) on delete cascade,
  status text not null default 'active',
  created_at timestamptz not null default now(),
  unlinked_at timestamptz,
  unlinked_by uuid references public.users(id) on delete set null,
  constraint couples_distinct_users_check check (user_a_id <> user_b_id),
  constraint couples_status_check check (status in ('active', 'unlinked'))
);

create table if not exists public.pair_invites (
  id uuid primary key default gen_random_uuid(),
  code_hash text not null,
  created_by uuid not null references public.users(id) on delete cascade,
  expires_at timestamptz not null,
  used_at timestamptz,
  used_by uuid references public.users(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint pair_invites_code_hash_not_blank check (length(btrim(code_hash)) > 0)
);

create table if not exists public.user_avatar (
  user_id uuid primary key references public.users(id) on delete cascade,
  avatar_id text not null references public.avatars(id) on delete restrict,
  variant_id text,
  updated_at timestamptz not null default now()
);

create table if not exists public.current_status (
  user_id uuid primary key references public.users(id) on delete cascade,
  couple_id uuid references public.couples(id) on delete set null,
  status_code text not null,
  source text not null default 'usage_events',
  status_updated_at timestamptz not null default now(),
  expires_at timestamptz not null,
  is_paused boolean not null default false,
  updated_at timestamptz not null default now(),
  constraint current_status_code_check check (
    status_code in (
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
  )
);

alter table public.avatars enable row level security;
alter table public.users enable row level security;
alter table public.couples enable row level security;
alter table public.pair_invites enable row level security;
alter table public.user_avatar enable row level security;
alter table public.current_status enable row level security;

drop policy if exists avatars_are_public_readable on public.avatars;

create policy avatars_are_public_readable
  on public.avatars
  for select
  to anon, authenticated
  using (is_active);

create unique index if not exists idx_couples_active_pair
  on public.couples (least(user_a_id, user_b_id), greatest(user_a_id, user_b_id))
  where status = 'active';

create index if not exists idx_couples_user_a
  on public.couples(user_a_id);

create index if not exists idx_couples_user_b
  on public.couples(user_b_id);

create index if not exists idx_couples_status
  on public.couples(status);

create unique index if not exists idx_pair_invites_code_hash
  on public.pair_invites(code_hash);

create index if not exists idx_pair_invites_created_by
  on public.pair_invites(created_by);

create index if not exists idx_pair_invites_expires_at
  on public.pair_invites(expires_at);

create index if not exists idx_user_avatar_avatar_id
  on public.user_avatar(avatar_id);

create index if not exists idx_current_status_couple_id
  on public.current_status(couple_id);

create index if not exists idx_current_status_expires_at
  on public.current_status(expires_at);

create or replace function public.enforce_single_active_couple()
returns trigger
language plpgsql
as $$
begin
  if new.status = 'active' then
    perform 1
    from public.users
    where id in (new.user_a_id, new.user_b_id)
    order by id
    for update;

    if exists (
      select 1
      from public.couples existing
      where existing.status = 'active'
        and existing.id <> new.id
        and (
          existing.user_a_id = new.user_a_id
          or existing.user_b_id = new.user_a_id
          or existing.user_a_id = new.user_b_id
          or existing.user_b_id = new.user_b_id
        )
    ) then
      raise exception 'user already has an active couple'
        using errcode = 'unique_violation';
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_enforce_single_active_couple on public.couples;

create trigger trg_enforce_single_active_couple
  before insert or update of user_a_id, user_b_id, status
  on public.couples
  for each row
  execute function public.enforce_single_active_couple();

insert into public.avatars (id, name, asset_key, sort_order)
values
  ('cat','小猫','avatars/cat',10),
  ('dog','小狗','avatars/dog',20),
  ('rabbit','小兔','avatars/rabbit',30),
  ('bear','小熊','avatars/bear',40),
  ('fox','小狐','avatars/fox',50),
  ('panda','熊猫','avatars/panda',60),
  ('penguin','企鹅','avatars/penguin',70),
  ('duck','小鸭','avatars/duck',80)
on conflict (id) do update
set
  name = excluded.name,
  asset_key = excluded.asset_key,
  sort_order = excluded.sort_order,
  updated_at = now();
