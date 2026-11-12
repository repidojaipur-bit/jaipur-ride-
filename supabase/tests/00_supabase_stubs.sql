-- =====================================================================
-- Test harness: the minimum of a real Supabase database needed to run the
-- Jaipur Ride migration on a plain PostgreSQL 15+ instance (or PGlite).
-- Mirrors the parts of the Supabase platform the schema depends on:
--   auth.users, auth.uid(), storage.buckets/objects, storage.foldername(),
--   the anon / authenticated roles and the supabase_realtime publication.
-- NEVER run this on a real Supabase project.
-- =====================================================================

create schema if not exists auth;
create schema if not exists storage;

-- auth.users (subset) ------------------------------------------------
create table if not exists auth.users (
  id                 uuid primary key,
  email              text,
  phone              text,
  raw_user_meta_data jsonb not null default '{}'::jsonb
);

-- auth.uid() reads a session GUC so tests can impersonate a user.
create or replace function auth.uid() returns uuid
language sql stable as $$
  select nullif(current_setting('test.uid', true), '')::uuid;
$$;

-- storage (subset) ---------------------------------------------------
create table if not exists storage.buckets (
  id     text primary key,
  name   text not null,
  public boolean not null default false
);

create table if not exists storage.objects (
  id        uuid primary key default gen_random_uuid(),
  bucket_id text not null,
  name      text not null
);

-- Supabase enables RLS on the storage tables; only platform policies apply.
alter table storage.buckets enable row level security;
alter table storage.objects enable row level security;

-- storage.foldername('uid/file.pdf') -> {uid}
create or replace function storage.foldername(name text) returns text[]
language sql immutable as $$
  select (string_to_array(name, '/'))[
           1 : greatest(array_length(string_to_array(name, '/'), 1) - 1, 0)
         ];
$$;

-- roles + realtime publication ---------------------------------------
do $$ begin create role anon nologin;         exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;
do $$ begin create role service_role nologin;  exception when duplicate_object then null; end $$;

do $$ begin
  create publication supabase_realtime;
exception when duplicate_object then null; end $$;

-- Supabase grants these to the API roles by default; RLS is then the only
-- gate on storage, exactly as in production.
grant usage on schema storage, auth to anon, authenticated;
grant all on storage.buckets, storage.objects to authenticated;
grant select on storage.buckets, storage.objects to anon;
