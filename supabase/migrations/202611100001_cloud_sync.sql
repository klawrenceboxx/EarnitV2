-- Cloud Sync: one row per synced record, owned by the signed-in user.
-- Contract and merge rules: docs/CLOUD_SYNC.md.

create table public.sync_records (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  collection text not null check (collection in ('rules')),
  record_id text not null check (char_length(record_id) between 1 and 128),
  platform text not null check (platform in ('android', 'ios')),
  payload jsonb not null default '{}'::jsonb check (pg_column_size(payload) <= 32768),
  -- Client clock of the last write, in epoch milliseconds. Last writer wins.
  updated_at_ms bigint not null check (updated_at_ms > 0),
  deleted boolean not null default false,
  device_id text not null check (char_length(device_id) between 1 and 64),
  server_updated_at timestamptz not null default now(),
  primary key (user_id, collection, record_id)
);

alter table public.sync_records enable row level security;

revoke all on public.sync_records from anon;
grant select, insert, update on public.sync_records to authenticated;

-- A user can only ever see or write their own rows. Rows are never hard-deleted by clients:
-- deletion is a tombstone (deleted = true) so other devices learn about it.
create policy "sync_records_select_own" on public.sync_records
  for select to authenticated using ((select auth.uid()) = user_id);

create policy "sync_records_insert_own" on public.sync_records
  for insert to authenticated with check ((select auth.uid()) = user_id);

create policy "sync_records_update_own" on public.sync_records
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

-- Last-writer-wins is enforced here as well as in the client, so a device with a stale copy
-- can never overwrite a newer write that landed between its fetch and its push.
create or replace function public.sync_records_keep_newest()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.updated_at_ms < old.updated_at_ms then
    return null; -- skip the stale update, keep the stored row
  end if;
  new.user_id := old.user_id;
  new.server_updated_at := now();
  return new;
end;
$$;

create trigger sync_records_keep_newest
  before update on public.sync_records
  for each row execute function public.sync_records_keep_newest();

-- Abuse guard: a single account cannot grow without bound.
create or replace function public.sync_records_enforce_quota()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  -- Upserts of an existing record also pass through BEFORE INSERT; only count new records.
  if not exists (
    select 1 from public.sync_records
    where user_id = new.user_id and collection = new.collection and record_id = new.record_id
  ) and (select count(*) from public.sync_records where user_id = new.user_id) >= 500 then
    raise exception 'sync record limit reached' using errcode = 'check_violation';
  end if;
  return new;
end;
$$;

create trigger sync_records_enforce_quota
  before insert on public.sync_records
  for each row execute function public.sync_records_enforce_quota();
