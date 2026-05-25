create or replace function public.list_avatars()
returns jsonb
language sql
stable
security definer
set search_path = public, auth
as $$
  select jsonb_build_object(
    'avatars',
    coalesce(
      jsonb_agg(
        jsonb_build_object(
          'id', active_avatars.id,
          'name', active_avatars.name,
          'assetKey', active_avatars.asset_key
        )
        order by active_avatars.sort_order
      ),
      '[]'::jsonb
    )
  )
  from public.avatars active_avatars
  where active_avatars.is_active;
$$;

revoke execute on function public.list_avatars() from public;
grant execute on function public.list_avatars() to anon;
grant execute on function public.list_avatars() to authenticated;
