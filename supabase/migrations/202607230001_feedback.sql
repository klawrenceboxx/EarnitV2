create extension if not exists pgcrypto;

create type public.feedback_category as enum ('BUG', 'SUGGESTION', 'OTHER');
create type public.feedback_entry_source as enum ('SETTINGS', 'SHAKE', 'CRASH_FOLLOW_UP');
create type public.feedback_status as enum ('NEW', 'IN_REVIEW', 'FIXED', 'CLOSED');

create table public.feedback (
  id uuid primary key default gen_random_uuid(),
  public_reference_id text not null unique check (public_reference_id ~ '^FB-[A-Z0-9]{8}$'),
  created_at timestamptz not null default now(),
  category public.feedback_category not null,
  message text not null check (char_length(message) between 1 and 2000),
  contact_email text,
  screenshot_path text,
  entry_source public.feedback_entry_source not null,
  installation_id uuid not null,
  idempotency_key uuid not null unique,
  diagnostics_schema_version integer not null check (diagnostics_schema_version between 1 and 10),
  diagnostics_json jsonb not null,
  app_version text not null,
  build_number bigint not null,
  android_version text not null,
  device_manufacturer text not null,
  device_model text not null,
  current_screen text not null,
  strict_mode_enabled boolean not null,
  active_rule_count integer not null check (active_rule_count >= 0),
  usage_access_granted boolean not null,
  accessibility_service_enabled boolean not null,
  client_created_at timestamptz not null,
  status public.feedback_status not null default 'NEW'
);

alter table public.feedback enable row level security;
revoke all on public.feedback from anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('feedback-screenshots', 'feedback-screenshots', false, 4194304, array['image/jpeg'])
on conflict (id) do update set public = false, file_size_limit = 4194304, allowed_mime_types = array['image/jpeg'];

-- No client Storage policies are created. The Edge Function service role bypasses RLS.

create table public.feedback_rate_limits (
  rate_key text primary key,
  window_started_at timestamptz not null,
  request_count integer not null check (request_count >= 0)
);
alter table public.feedback_rate_limits enable row level security;
revoke all on public.feedback_rate_limits from anon, authenticated;

create or replace function public.consume_feedback_rate_limit(p_rate_key text, p_limit integer default 10)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare allowed boolean;
begin
  insert into public.feedback_rate_limits(rate_key, window_started_at, request_count)
  values (p_rate_key, now(), 1)
  on conflict (rate_key) do update
    set window_started_at = case
          when feedback_rate_limits.window_started_at < now() - interval '1 hour' then now()
          else feedback_rate_limits.window_started_at
        end,
        request_count = case
          when feedback_rate_limits.window_started_at < now() - interval '1 hour' then 1
          else feedback_rate_limits.request_count + 1
        end
  returning request_count <= p_limit into allowed;
  return allowed;
end;
$$;
revoke all on function public.consume_feedback_rate_limit(text, integer) from public, anon, authenticated;
grant execute on function public.consume_feedback_rate_limit(text, integer) to service_role;
