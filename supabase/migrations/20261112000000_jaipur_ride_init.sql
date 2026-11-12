-- =====================================================================
-- JAIPUR RIDE - Complete Supabase schema
-- Target : a FRESH Supabase project (separate from backend/migrations/001-002,
--          which model the driver-only "Drive Partner" product and use a
--          different public.drivers / public.vehicles shape).
-- Run    : ONCE  ->  Supabase Dashboard -> SQL Editor -> Run
-- Idempotent: every object is created with IF NOT EXISTS / OR REPLACE, and
--          policies and triggers are dropped before being re-created, so a
--          re-run on the same database is safe (it will not delete data).
-- Sections:
--   1. Enums           5. Triggers           9. Storage
--   2. Tables          6. RPC functions     10. Realtime
--   3. Indexes         7. Row Level Security 11. Seed data
--   4. Helper fns      8. Grants            12. Bootstrap admin
-- =====================================================================

-- ---------- 1. ENUMS -------------------------------------------------
do $$ begin
  create type public.user_role as enum ('rider', 'driver', 'admin');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.verification_status as enum ('pending', 'approved', 'rejected');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.vehicle_type as enum ('bike', 'auto', 'mini', 'sedan', 'suv');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.ride_status as enum ('requested', 'accepted', 'arrived', 'in_progress', 'completed', 'cancelled');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.payment_method as enum ('cash', 'upi', 'card', 'wallet');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.payment_status as enum ('pending', 'paid', 'failed', 'refunded');
exception when duplicate_object then null; end $$;

-- ---------- 2. TABLES ------------------------------------------------
create table if not exists public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  role        public.user_role not null default 'rider',
  full_name   text,
  phone       text unique,
  email       text,
  avatar_url  text,
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create table if not exists public.drivers (
  id                  uuid primary key references public.profiles(id) on delete cascade,
  license_number      text,
  verification_status public.verification_status not null default 'pending',
  is_online           boolean not null default false,
  current_lat         double precision check (current_lat between -90 and 90),
  current_lng         double precision check (current_lng between -180 and 180),
  last_location_at    timestamptz,
  rating_avg          numeric(3,2) not null default 0,
  rating_count        integer not null default 0,
  total_rides         integer not null default 0,
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);

create table if not exists public.vehicles (
  id            uuid primary key default gen_random_uuid(),
  driver_id     uuid not null references public.drivers(id) on delete cascade,
  vehicle_type  public.vehicle_type not null,
  make          text,
  model         text,
  color         text,
  plate_number  text not null unique,
  year          integer check (year between 1990 and 2100),
  seats         integer not null default 4 check (seats between 1 and 10),
  is_active     boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

create table if not exists public.driver_documents (
  id           uuid primary key default gen_random_uuid(),
  driver_id    uuid not null references public.drivers(id) on delete cascade,
  doc_type     text not null check (doc_type in ('license', 'rc', 'insurance', 'id_proof', 'photo')),
  file_path    text not null,                       -- path inside storage bucket 'driver-documents'
  status       public.verification_status not null default 'pending',
  reviewed_by  uuid references public.profiles(id),
  reviewed_at  timestamptz,
  created_at   timestamptz not null default now()
);

create table if not exists public.saved_places (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles(id) on delete cascade,
  label       text not null,
  address     text not null,
  lat         double precision not null check (lat between -90 and 90),
  lng         double precision not null check (lng between -180 and 180),
  created_at  timestamptz not null default now()
);

create table if not exists public.fare_rules (
  vehicle_type  public.vehicle_type primary key,
  base_fare     numeric(10,2) not null check (base_fare >= 0),
  per_km        numeric(10,2) not null check (per_km >= 0),
  per_minute    numeric(10,2) not null default 0 check (per_minute >= 0),
  min_fare      numeric(10,2) not null default 0 check (min_fare >= 0),
  is_active     boolean not null default true,
  updated_at    timestamptz not null default now()
);

create table if not exists public.coupons (
  id              uuid primary key default gen_random_uuid(),
  code            text not null unique,
  discount_type   text not null check (discount_type in ('percent', 'flat')),
  discount_value  numeric(10,2) not null check (discount_value > 0),
  max_discount    numeric(10,2),
  min_fare        numeric(10,2) not null default 0,
  valid_from      timestamptz not null default now(),
  valid_until     timestamptz,
  usage_limit     integer,
  used_count      integer not null default 0,
  is_active       boolean not null default true,
  created_at      timestamptz not null default now()
);

create table if not exists public.rides (
  id              uuid primary key default gen_random_uuid(),
  rider_id        uuid not null references public.profiles(id),
  driver_id       uuid references public.drivers(id),
  vehicle_id      uuid references public.vehicles(id),
  vehicle_type    public.vehicle_type not null,
  status          public.ride_status not null default 'requested',
  pickup_address  text not null,
  pickup_lat      double precision not null check (pickup_lat between -90 and 90),
  pickup_lng      double precision not null check (pickup_lng between -180 and 180),
  drop_address    text not null,
  drop_lat        double precision not null check (drop_lat between -90 and 90),
  drop_lng        double precision not null check (drop_lng between -180 and 180),
  distance_km     numeric(8,2) check (distance_km >= 0),
  duration_min    numeric(8,2) check (duration_min >= 0),
  estimated_fare  numeric(10,2),
  final_fare      numeric(10,2),
  coupon_id       uuid references public.coupons(id),
  discount_amount numeric(10,2) not null default 0,
  payment_method  public.payment_method not null default 'cash',
  requested_at    timestamptz not null default now(),
  accepted_at     timestamptz,
  arrived_at      timestamptz,
  started_at      timestamptz,
  completed_at    timestamptz,
  cancelled_at    timestamptz,
  cancelled_by    uuid references public.profiles(id),
  cancel_reason   text,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

-- OTP is kept in its own table so the driver can never read it via rides
create table if not exists public.ride_otps (
  ride_id  uuid primary key references public.rides(id) on delete cascade,
  otp      text not null
);

create table if not exists public.ride_status_history (
  id          bigint generated always as identity primary key,
  ride_id     uuid not null references public.rides(id) on delete cascade,
  status      public.ride_status not null,
  changed_by  uuid,
  created_at  timestamptz not null default now()
);

create table if not exists public.payments (
  id           uuid primary key default gen_random_uuid(),
  ride_id      uuid not null unique references public.rides(id),
  amount       numeric(10,2) not null check (amount >= 0),
  method       public.payment_method not null,
  status       public.payment_status not null default 'pending',
  gateway_ref  text,
  paid_at      timestamptz,
  created_at   timestamptz not null default now()
);

create table if not exists public.ratings (
  id          uuid primary key default gen_random_uuid(),
  ride_id     uuid not null references public.rides(id) on delete cascade,
  rater_id    uuid not null references public.profiles(id),
  ratee_id    uuid not null references public.profiles(id),
  score       integer not null check (score between 1 and 5),
  comment     text,
  created_at  timestamptz not null default now(),
  unique (ride_id, rater_id)
);

create table if not exists public.notifications (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references public.profiles(id) on delete cascade,
  title       text not null,
  body        text,
  data        jsonb not null default '{}'::jsonb,
  is_read     boolean not null default false,
  created_at  timestamptz not null default now()
);

-- ---------- 3. INDEXES ----------------------------------------------
create index if not exists idx_vehicles_driver        on public.vehicles(driver_id);
create index if not exists idx_docs_driver            on public.driver_documents(driver_id);
create index if not exists idx_saved_places_user      on public.saved_places(user_id);
create index if not exists idx_drivers_online         on public.drivers(is_online) where is_online;
create index if not exists idx_rides_rider            on public.rides(rider_id, created_at desc);
create index if not exists idx_rides_driver           on public.rides(driver_id, created_at desc);
create index if not exists idx_rides_requested        on public.rides(vehicle_type) where status = 'requested';
create index if not exists idx_history_ride           on public.ride_status_history(ride_id);
create index if not exists idx_ratings_ratee          on public.ratings(ratee_id);
create index if not exists idx_notifications_user     on public.notifications(user_id, is_read, created_at desc);
create index if not exists idx_docs_status            on public.driver_documents(status) where status = 'pending';

-- one active ride at a time per rider / per driver
create unique index if not exists uq_rider_active_ride  on public.rides(rider_id)
  where status in ('requested', 'accepted', 'arrived', 'in_progress');
create unique index if not exists uq_driver_active_ride on public.rides(driver_id)
  where driver_id is not null and status in ('accepted', 'arrived', 'in_progress');

-- ---------- 4. HELPER FUNCTIONS -------------------------------------
create or replace function public.set_updated_at() returns trigger
language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end $$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin' and is_active
  );
$$;

create or replace function public.is_approved_driver() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.drivers d
    join public.profiles p on p.id = d.id
    where d.id = auth.uid()
      and d.verification_status = 'approved'
      and d.is_online
      and p.is_active
  );
$$;

-- True when the signed-in user has (or had) a ride with p_user. SECURITY DEFINER
-- so the profiles/drivers/vehicles policies never re-enter public.rides RLS
-- (avoids policy recursion and repeated evaluation).
create or replace function public.shares_ride_with(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
      from public.rides r
     where (r.rider_id = auth.uid() and r.driver_id = p_user)
        or (r.driver_id = auth.uid() and r.rider_id = p_user)
  );
$$;

-- Same idea, one hop further: can the signed-in rider see this vehicle? A
-- rider may read a vehicle only if it was used on one of their own rides.
create or replace function public.owns_ride_vehicle(p_driver uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
      from public.rides r
      join public.vehicles v on v.id = r.vehicle_id
     where r.rider_id = auth.uid() and v.driver_id = p_driver
  );
$$;

create or replace function public.fare_calc(p_type public.vehicle_type, p_km numeric, p_min numeric)
returns numeric
language sql stable security definer set search_path = '' as $$
  select greatest(
           r.min_fare,
           round(r.base_fare + r.per_km * coalesce(p_km, 0) + r.per_minute * coalesce(p_min, 0), 2)
         )
  from public.fare_rules r
  where r.vehicle_type = p_type and r.is_active;
$$;

create or replace function public.coupon_discount(p_coupon uuid, p_fare numeric)
returns numeric
language sql stable security definer set search_path = '' as $$
  select coalesce((
    select case
             when c.discount_type = 'percent'
               then least(coalesce(c.max_discount, p_fare), round(p_fare * c.discount_value / 100, 2))
             else least(c.discount_value, p_fare)
           end
    from public.coupons c
    where c.id = p_coupon
      and c.is_active
      and now() >= c.valid_from
      and (c.valid_until is null or now() <= c.valid_until)
      and (c.usage_limit is null or c.used_count < c.usage_limit)
      and p_fare >= c.min_fare
  ), 0);
$$;

-- Rate a coupon BEFORE booking, so the app can show "₹40 off" without
-- exposing the whole coupons table (which is admin-only under RLS).
create or replace function public.validate_coupon(p_code text, p_vehicle_type public.vehicle_type, p_km numeric, p_min numeric)
returns numeric
language plpgsql stable security definer set search_path = '' as $$
declare
  v_fare numeric;
  v_id   uuid;
begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  v_fare := public.fare_calc(p_vehicle_type, p_km, p_min);
  if v_fare is null then raise exception 'No active fare rule for vehicle type %', p_vehicle_type; end if;
  select c.id into v_id from public.coupons c where c.code = upper(btrim(p_code)) limit 1;
  if v_id is null then return 0; end if;
  return public.coupon_discount(v_id, v_fare);
end $$;

-- Resolve a coupon code to its id at booking time. Keeps coupons admin-only
-- while still letting the rider attach coupon_id. Applying a coupon that the
-- fare does not qualify for is rejected by the booking trigger.
create or replace function public.coupon_id_for_code(p_code text)
returns uuid
language plpgsql stable security definer set search_path = '' as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  select c.id into v_id from public.coupons c
   where c.code = upper(btrim(p_code)) and c.is_active
   limit 1;
  -- Do not turn this into a coupon-code oracle: an unknown code and an
  -- inactive code look exactly the same to the caller.
  if v_id is null then raise exception 'Coupon code not found or inactive'; end if;
  return v_id;
end $$;

-- ---------- 5. TRIGGERS ---------------------------------------------
drop trigger if exists trg_profiles_updated on public.profiles;
create trigger trg_profiles_updated before update on public.profiles for each row execute function public.set_updated_at();

drop trigger if exists trg_drivers_updated on public.drivers;
create trigger trg_drivers_updated  before update on public.drivers  for each row execute function public.set_updated_at();

drop trigger if exists trg_vehicles_updated on public.vehicles;
create trigger trg_vehicles_updated before update on public.vehicles for each row execute function public.set_updated_at();

drop trigger if exists trg_rides_updated on public.rides;
create trigger trg_rides_updated    before update on public.rides    for each row execute function public.set_updated_at();

drop trigger if exists trg_fares_updated on public.fare_rules;
create trigger trg_fares_updated    before update on public.fare_rules for each row execute function public.set_updated_at();

-- 5b. new auth user -> profile (+ driver row). Role 'admin' can NEVER be self-assigned.
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_role  public.user_role;
  v_phone text;
begin
  v_role := case when new.raw_user_meta_data ->> 'role' = 'driver'
                 then 'driver'::public.user_role
                 else 'rider'::public.user_role end;

  v_phone := nullif(coalesce(new.phone, new.raw_user_meta_data ->> 'phone'), '');

  -- phone is UNIQUE. A colliding phone must never make sign-up fail (the user
  -- would be half-created: auth row yes, profile no), so drop it and let the
  -- app claim it later through the profile update path.
  if v_phone is not null and exists (select 1 from public.profiles where phone = v_phone) then
    v_phone := null;
  end if;

  insert into public.profiles (id, role, full_name, phone, email)
  values (new.id, v_role, new.raw_user_meta_data ->> 'full_name', v_phone, new.email)
  on conflict (id) do nothing;

  if v_role = 'driver' then
    insert into public.drivers (id) values (new.id) on conflict do nothing;
  end if;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- 5c. ride insert: force safe values, compute fare server-side, apply coupon
create or replace function public.rides_before_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_fare numeric;
  v_disc numeric := 0;
begin
  new.rider_id     := coalesce(auth.uid(), new.rider_id);
  new.status       := 'requested';
  new.driver_id    := null;
  new.vehicle_id   := null;
  new.final_fare   := null;
  new.accepted_at  := null;
  new.arrived_at   := null;
  new.started_at   := null;
  new.completed_at := null;
  new.cancelled_at := null;
  new.cancelled_by := null;
  new.cancel_reason := null;
  new.requested_at := now();
  new.created_at   := now();
  new.updated_at   := now();

  v_fare := public.fare_calc(new.vehicle_type, new.distance_km, new.duration_min);
  if v_fare is null then
    raise exception 'No active fare rule for vehicle type %', new.vehicle_type;
  end if;
  new.estimated_fare := v_fare;

  if new.coupon_id is not null then
    v_disc := public.coupon_discount(new.coupon_id, v_fare);
    if v_disc <= 0 then
      raise exception 'Coupon is invalid, expired or not applicable';
    end if;
    update public.coupons set used_count = used_count + 1 where id = new.coupon_id;
  end if;
  new.discount_amount := v_disc;
  return new;
end $$;

drop trigger if exists trg_rides_before_insert on public.rides;
create trigger trg_rides_before_insert
  before insert on public.rides
  for each row execute function public.rides_before_insert();

-- 5d. after ride insert/update: OTP, status history, notifications
create or replace function public.rides_after_change() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    insert into public.ride_otps (ride_id, otp)
    values (new.id, lpad(floor(random() * 10000)::int::text, 4, '0'))
    on conflict (ride_id) do nothing;

    insert into public.ride_status_history (ride_id, status, changed_by)
    values (new.id, new.status, auth.uid());

  elsif new.status is distinct from old.status then
    insert into public.ride_status_history (ride_id, status, changed_by)
    values (new.id, new.status, auth.uid());

    insert into public.notifications (user_id, title, body, data)
    values (new.rider_id, 'Ride update', 'Your ride is now ' || replace(new.status::text, '_', ' '),
            jsonb_build_object('ride_id', new.id, 'status', new.status));

    -- keep the driver informed about cancellations; ride updates otherwise
    -- reach the driver through the dispatcher UI / realtime subscription
    if new.driver_id is not null and new.status = 'cancelled' then
      insert into public.notifications (user_id, title, body, data)
      values (new.driver_id, 'Ride cancelled', 'The ride was cancelled',
              jsonb_build_object('ride_id', new.id, 'status', new.status));
    end if;

    if new.driver_id is not null and new.status = 'completed' then
      insert into public.notifications (user_id, title, body, data)
      values (new.driver_id, 'Ride completed', 'Trip finished - fare Rs. ' || coalesce(new.final_fare, 0)::text,
              jsonb_build_object('ride_id', new.id, 'status', new.status));
    end if;
  end if;
  return null;
end $$;

drop trigger if exists trg_rides_after_change on public.rides;
create trigger trg_rides_after_change
  after insert or update of status on public.rides
  for each row execute function public.rides_after_change();

-- 5e. ratings: validate participants, derive ratee, then refresh driver average
create or replace function public.ratings_before_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_ride public.rides;
begin
  new.rater_id := coalesce(auth.uid(), new.rater_id);

  select * into v_ride from public.rides where id = new.ride_id;
  if v_ride.id is null or v_ride.status <> 'completed' then
    raise exception 'Only completed rides can be rated';
  end if;

  if new.rater_id = v_ride.rider_id then
    new.ratee_id := v_ride.driver_id;
  elsif new.rater_id = v_ride.driver_id then
    new.ratee_id := v_ride.rider_id;
  else
    raise exception 'You did not take part in this ride';
  end if;

  if new.ratee_id is null then
    raise exception 'Ride has no counterpart to rate';
  end if;
  return new;
end $$;

drop trigger if exists trg_ratings_before_insert on public.ratings;
create trigger trg_ratings_before_insert
  before insert on public.ratings
  for each row execute function public.ratings_before_insert();

create or replace function public.ratings_after_insert() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public.drivers d
     set rating_count = s.c,
         rating_avg   = s.a
    from (select count(*)::int as c, round(avg(score)::numeric, 2) as a
            from public.ratings where ratee_id = new.ratee_id) s
   where d.id = new.ratee_id;
  return null;
end $$;

drop trigger if exists trg_ratings_after_insert on public.ratings;
create trigger trg_ratings_after_insert
  after insert on public.ratings
  for each row execute function public.ratings_after_insert();

-- ---------- 6. RPC FUNCTIONS (all ride state changes go through these) ----
create or replace function public.become_driver() returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_role public.user_role;
begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  if not exists (select 1 from public.profiles where id = auth.uid() and is_active) then
    raise exception 'Account is not active';
  end if;

  update public.profiles set role = 'driver' where id = auth.uid() and role = 'rider';

  select role into v_role from public.profiles where id = auth.uid();
  -- only a driver profile gets a drivers row; an admin promoting someone must
  -- go through admin_set_role() so the two tables cannot drift apart
  if v_role = 'driver' then
    insert into public.drivers (id) values (auth.uid()) on conflict do nothing;
  end if;
end $$;

create or replace function public.accept_ride(p_ride_id uuid) returns public.rides
language plpgsql security definer set search_path = '' as $$
declare
  v_ride    public.rides;
  v_vehicle uuid;
begin
  if not public.is_approved_driver() then
    raise exception 'You must be an approved, online driver';
  end if;

  select v.id into v_vehicle
    from public.rides r
    join public.vehicles v
      on v.driver_id = auth.uid() and v.is_active and v.vehicle_type = r.vehicle_type
   where r.id = p_ride_id
   order by v.created_at
   limit 1;
  if v_vehicle is null then
    raise exception 'No active vehicle matching this ride type';
  end if;

  update public.rides
     set driver_id = auth.uid(), vehicle_id = v_vehicle,
         status = 'accepted', accepted_at = now()
   where id = p_ride_id and status = 'requested'
  returning * into v_ride;

  if v_ride.id is null then
    raise exception 'Ride is no longer available';
  end if;
  return v_ride;
end $$;

create or replace function public.arrive_ride(p_ride_id uuid) returns public.rides
language plpgsql security definer set search_path = '' as $$
declare v_ride public.rides;
begin
  update public.rides set status = 'arrived', arrived_at = now()
   where id = p_ride_id and driver_id = auth.uid() and status = 'accepted'
  returning * into v_ride;
  if v_ride.id is null then raise exception 'Cannot mark arrival for this ride'; end if;
  return v_ride;
end $$;

create or replace function public.start_ride(p_ride_id uuid, p_otp text) returns public.rides
language plpgsql security definer set search_path = '' as $$
declare v_ride public.rides;
begin
  if not exists (select 1 from public.ride_otps where ride_id = p_ride_id and otp = btrim(p_otp)) then
    raise exception 'Invalid OTP';
  end if;
  update public.rides set status = 'in_progress', started_at = now()
   where id = p_ride_id and driver_id = auth.uid() and status = 'arrived'
  returning * into v_ride;
  if v_ride.id is null then raise exception 'Cannot start this ride'; end if;
  return v_ride;
end $$;

create or replace function public.complete_ride(p_ride_id uuid, p_distance_km numeric, p_duration_min numeric)
returns public.rides
language plpgsql security definer set search_path = '' as $$
declare
  v_ride  public.rides;
  v_fare  numeric;
  v_final numeric;
begin
  if coalesce(p_distance_km, -1) < 0 or coalesce(p_duration_min, -1) < 0 then
    raise exception 'Distance and duration must be non-negative';
  end if;

  select * into v_ride from public.rides
   where id = p_ride_id and driver_id = auth.uid() and status = 'in_progress'
   for update;
  if v_ride.id is null then raise exception 'Cannot complete this ride'; end if;

  v_fare := public.fare_calc(v_ride.vehicle_type, p_distance_km, p_duration_min);
  if v_fare is null then raise exception 'No active fare rule'; end if;
  v_final := greatest(v_fare - v_ride.discount_amount, 0);

  update public.rides
     set status = 'completed', completed_at = now(),
         distance_km = p_distance_km, duration_min = p_duration_min,
         final_fare = v_final
   where id = p_ride_id
  returning * into v_ride;

  insert into public.payments (ride_id, amount, method, status, paid_at)
  values (v_ride.id, v_final, v_ride.payment_method,
          case when v_ride.payment_method = 'cash' then 'paid'::public.payment_status
               else 'pending'::public.payment_status end,
          case when v_ride.payment_method = 'cash' then now() end)
  on conflict (ride_id) do nothing;

  update public.drivers set total_rides = total_rides + 1 where id = auth.uid();
  return v_ride;
end $$;

create or replace function public.cancel_ride(p_ride_id uuid, p_reason text default null)
returns public.rides
language plpgsql security definer set search_path = '' as $$
declare v_ride public.rides;
begin
  update public.rides
     set status = 'cancelled', cancelled_at = now(),
         cancelled_by = auth.uid(), cancel_reason = p_reason
   where id = p_ride_id
     and status in ('requested', 'accepted', 'arrived')
     and (rider_id = auth.uid() or driver_id = auth.uid())
  returning * into v_ride;
  if v_ride.id is null then raise exception 'Ride cannot be cancelled'; end if;
  return v_ride;
end $$;

-- Driver presence heartbeat. Kept separate from a plain UPDATE so the
-- freshness rule (nearby_drivers' 5 minute window) has one authority.
create or replace function public.update_driver_location(p_lat double precision, p_lng double precision)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Not authenticated'; end if;
  if p_lat is null or p_lng is null
     or p_lat < -90 or p_lat > 90 or p_lng < -180 or p_lng > 180 then
    raise exception 'Invalid coordinates';
  end if;
  update public.drivers
     set current_lat = p_lat, current_lng = p_lng, last_location_at = now()
   where id = auth.uid();
  if not found then raise exception 'Not a driver account'; end if;
end $$;

create or replace function public.nearby_drivers(
  p_lat double precision, p_lng double precision,
  p_radius_km double precision default 5,
  p_type public.vehicle_type default null
)
returns table (driver_id uuid, lat double precision, lng double precision,
               vehicle_type public.vehicle_type, distance_km double precision)
language sql stable security definer set search_path = '' as $$
  select * from (
    select d.id, d.current_lat, d.current_lng, v.vehicle_type,
           6371 * 2 * asin(sqrt(
             power(sin(radians(d.current_lat - p_lat) / 2), 2) +
             cos(radians(p_lat)) * cos(radians(d.current_lat)) *
             power(sin(radians(d.current_lng - p_lng) / 2), 2)
           )) as dist
      from public.drivers d
      join public.vehicles v on v.driver_id = d.id and v.is_active
     where auth.uid() is not null
       and d.is_online
       and d.verification_status = 'approved'
       and d.current_lat is not null and d.current_lng is not null
       and d.last_location_at > now() - interval '5 minutes'
       and (p_type is null or v.vehicle_type = p_type)
  ) t
  where t.dist <= p_radius_km
  order by t.dist
  limit 50;
$$;

-- admin RPCs (column grants stop admins editing these via plain UPDATE)
create or replace function public.admin_set_driver_status(p_driver uuid, p_status public.verification_status)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Admin only'; end if;
  update public.drivers set verification_status = p_status where id = p_driver;
end $$;

create or replace function public.admin_set_role(p_user uuid, p_role public.user_role)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Admin only'; end if;
  if p_user = auth.uid() and p_role <> 'admin' then
    raise exception 'You cannot remove your own admin role';
  end if;
  update public.profiles set role = p_role where id = p_user;
  if p_role = 'driver' then
    insert into public.drivers (id) values (p_user) on conflict do nothing;
  end if;
end $$;

create or replace function public.admin_review_document(p_document uuid, p_status public.verification_status)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Admin only'; end if;
  update public.driver_documents
     set status = p_status, reviewed_by = auth.uid(), reviewed_at = now()
   where id = p_document;
  if not found then raise exception 'Document not found'; end if;
end $$;

-- ---------- 7. ROW LEVEL SECURITY ------------------------------------
alter table public.profiles            enable row level security;
alter table public.drivers             enable row level security;
alter table public.vehicles            enable row level security;
alter table public.driver_documents    enable row level security;
alter table public.saved_places        enable row level security;
alter table public.fare_rules          enable row level security;
alter table public.coupons             enable row level security;
alter table public.rides               enable row level security;
alter table public.ride_otps           enable row level security;
alter table public.ride_status_history enable row level security;
alter table public.payments            enable row level security;
alter table public.ratings             enable row level security;
alter table public.notifications       enable row level security;

-- profiles
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated using (
  id = auth.uid() or public.is_admin() or public.shares_ride_with(id)
);
drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- drivers
drop policy if exists drivers_select on public.drivers;
create policy drivers_select on public.drivers for select to authenticated using (
  id = auth.uid() or public.is_admin() or public.shares_ride_with(id)
);
drop policy if exists drivers_update_own on public.drivers;
create policy drivers_update_own on public.drivers for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- vehicles
drop policy if exists vehicles_select on public.vehicles;
create policy vehicles_select on public.vehicles for select to authenticated using (
  driver_id = auth.uid() or public.is_admin() or public.owns_ride_vehicle(driver_id)
);
drop policy if exists vehicles_insert_own on public.vehicles;
create policy vehicles_insert_own on public.vehicles for insert to authenticated with check (driver_id = auth.uid());
drop policy if exists vehicles_update_own on public.vehicles;
create policy vehicles_update_own on public.vehicles for update to authenticated
  using (driver_id = auth.uid()) with check (driver_id = auth.uid());
drop policy if exists vehicles_delete_own on public.vehicles;
create policy vehicles_delete_own on public.vehicles for delete to authenticated using (driver_id = auth.uid());

-- driver documents
drop policy if exists docs_select on public.driver_documents;
create policy docs_select on public.driver_documents for select to authenticated
  using (driver_id = auth.uid() or public.is_admin());
drop policy if exists docs_insert_own on public.driver_documents;
create policy docs_insert_own on public.driver_documents for insert to authenticated
  with check (driver_id = auth.uid() and status = 'pending');
drop policy if exists docs_admin_update on public.driver_documents;
create policy docs_admin_update on public.driver_documents for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- saved places
drop policy if exists places_all_own on public.saved_places;
create policy places_all_own on public.saved_places for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- fare rules: everyone signed-in reads, admin writes
drop policy if exists fares_select on public.fare_rules;
create policy fares_select on public.fare_rules for select to authenticated using (true);
drop policy if exists fares_admin_write on public.fare_rules;
create policy fares_admin_write on public.fare_rules for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- coupons: admin only (riders pass coupon_id; validation happens in trigger)
drop policy if exists coupons_admin on public.coupons;
create policy coupons_admin on public.coupons for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- rides: no client UPDATE/DELETE policy on purpose - use the RPCs
drop policy if exists rides_select on public.rides;
create policy rides_select on public.rides for select to authenticated using (
  rider_id = auth.uid() or driver_id = auth.uid() or public.is_admin()
  or (status = 'requested' and public.is_approved_driver())
);
drop policy if exists rides_insert_rider on public.rides;
create policy rides_insert_rider on public.rides for insert to authenticated
  with check (rider_id = auth.uid());

-- ride OTP: only the rider sees it
drop policy if exists otp_select_rider on public.ride_otps;
create policy otp_select_rider on public.ride_otps for select to authenticated
  using (exists (select 1 from public.rides r where r.id = ride_id and r.rider_id = auth.uid()));

-- status history
drop policy if exists history_select on public.ride_status_history;
create policy history_select on public.ride_status_history for select to authenticated using (
  public.is_admin()
  or exists (select 1 from public.rides r
              where r.id = ride_id and (r.rider_id = auth.uid() or r.driver_id = auth.uid()))
);

-- payments
drop policy if exists payments_select on public.payments;
create policy payments_select on public.payments for select to authenticated using (
  public.is_admin()
  or exists (select 1 from public.rides r
              where r.id = ride_id and (r.rider_id = auth.uid() or r.driver_id = auth.uid()))
);

-- ratings
drop policy if exists ratings_select on public.ratings;
create policy ratings_select on public.ratings for select to authenticated
  using (rater_id = auth.uid() or ratee_id = auth.uid() or public.is_admin());
drop policy if exists ratings_insert on public.ratings;
create policy ratings_insert on public.ratings for insert to authenticated
  with check (rater_id = auth.uid());

-- notifications
drop policy if exists notif_select_own on public.notifications;
create policy notif_select_own on public.notifications for select to authenticated using (user_id = auth.uid());
drop policy if exists notif_update_own on public.notifications;
create policy notif_update_own on public.notifications for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists notif_delete_own on public.notifications;
create policy notif_delete_own on public.notifications for delete to authenticated using (user_id = auth.uid());

-- ---------- 8. GRANTS (column-level = role/verification/rating can't be tampered with) ----
revoke all on all tables    in schema public from anon, authenticated;
revoke all on all functions in schema public from public, anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;

grant select on public.profiles, public.drivers, public.fare_rules, public.ride_otps,
                public.ride_status_history, public.payments, public.rides to authenticated;
grant select, insert, update, delete on public.vehicles, public.saved_places to authenticated;
grant select, insert on public.ratings, public.driver_documents to authenticated;
-- a driver writes only their own (pending) upload metadata; review columns are
-- reachable by admins through admin_review_document() only
grant insert (driver_id, doc_type, file_path) on public.driver_documents to authenticated;
grant update (status, reviewed_by, reviewed_at) on public.driver_documents to authenticated;  -- admin-only via RLS
grant insert on public.rides to authenticated;
grant select, delete on public.notifications to authenticated;
grant update (is_read) on public.notifications to authenticated;
grant all on public.fare_rules, public.coupons to authenticated;                              -- admin-only via RLS
grant update (full_name, phone, avatar_url) on public.profiles to authenticated;
grant update (license_number, is_online, current_lat, current_lng, last_location_at)
  on public.drivers to authenticated;

-- The RLS policies call these helpers, so clients need EXECUTE on them even
-- though they are only ever used inside a policy expression.
grant execute on function
  public.is_admin(), public.is_approved_driver(),
  public.shares_ride_with(uuid), public.owns_ride_vehicle(uuid),
  public.become_driver(), public.validate_coupon(text, public.vehicle_type, numeric, numeric),
  public.coupon_id_for_code(text),
  public.accept_ride(uuid), public.arrive_ride(uuid), public.start_ride(uuid, text),
  public.complete_ride(uuid, numeric, numeric), public.cancel_ride(uuid, text),
  public.update_driver_location(double precision, double precision),
  public.nearby_drivers(double precision, double precision, double precision, public.vehicle_type),
  public.admin_set_driver_status(uuid, public.verification_status),
  public.admin_set_role(uuid, public.user_role),
  public.admin_review_document(uuid, public.verification_status)
  to authenticated;
-- fare_calc / coupon_discount are only called from triggers & RPCs, so no client grant needed.

-- ---------- 9. STORAGE ----------------------------------------------
insert into storage.buckets (id, name, public) values
  ('avatars', 'avatars', true),
  ('driver-documents', 'driver-documents', false)
on conflict (id) do nothing;

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own on storage.objects for update to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists avatars_select_all on storage.objects;
create policy avatars_select_all on storage.objects for select to authenticated, anon
  using (bucket_id = 'avatars');
drop policy if exists docs_storage_insert_own on storage.objects;
create policy docs_storage_insert_own on storage.objects for insert to authenticated
  with check (bucket_id = 'driver-documents' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists docs_storage_select on storage.objects;
create policy docs_storage_select on storage.objects for select to authenticated
  using (bucket_id = 'driver-documents'
         and ((storage.foldername(name))[1] = auth.uid()::text or public.is_admin()));
drop policy if exists docs_storage_delete_own on storage.objects;
create policy docs_storage_delete_own on storage.objects for delete to authenticated
  using (bucket_id = 'driver-documents' and (storage.foldername(name))[1] = auth.uid()::text);

-- ---------- 10. REALTIME --------------------------------------------
-- rides / drivers / notifications are streamed to the app. REPLICA IDENTITY FULL
-- is required for UPDATE/DELETE payloads to carry the old row (needed by the
-- clients' RealtimeEventDeduplicator to resolve status transitions).
alter table public.rides         replica identity full;
alter table public.drivers       replica identity full;
alter table public.notifications replica identity full;

do $$
declare
  t text;
begin
  foreach t in array array['rides', 'drivers', 'notifications'] loop
    begin
      execute format('alter publication supabase_realtime add table public.%I', t);
    exception when duplicate_object then null;
    end;
  end loop;
end $$;

-- ---------- 11. SEED DATA (edit fares as you like, INR) --------------
insert into public.fare_rules (vehicle_type, base_fare, per_km, per_minute, min_fare) values
  ('bike',  20,  7, 0.50,  30),
  ('auto',  30, 10, 1.00,  50),
  ('mini',  50, 12, 1.50,  80),
  ('sedan', 70, 15, 2.00, 100),
  ('suv',  100, 20, 2.50, 150)
on conflict (vehicle_type) do nothing;

-- =====================================================================
-- 12. AFTER RUNNING: make yourself admin (sign up first, then run this):
--   update public.profiles set role = 'admin' where email = 'YOUR_EMAIL';
-- =====================================================================
