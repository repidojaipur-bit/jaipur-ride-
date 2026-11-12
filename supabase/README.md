# Jaipur Ride — Supabase backend

This directory holds the **rider + driver marketplace** database: tables, RLS,
RPCs, storage buckets and realtime setup for the product described in
[`docs/05_SCHEMA (1).md`](../docs/05_SCHEMA%20(1).md) and
[`docs/06_RULES (1).md`](../docs/06_RULES%20(1).md).

> **Do not confuse it with `backend/migrations/001`–`002`.** Those migrations
> belong to the older driver-only *Drive Partner* service (Firebase ids,
> `service_category`, ledger tables) and define their own `public.drivers` /
> `public.vehicles` with a different shape. The two schemas cannot coexist in
> one `public` schema. Pick one per Supabase project.

## 1. Apply

```bash
# fresh Supabase project -> SQL Editor -> paste the file -> Run
supabase/migrations/20261112000000_jaipur_ride_init.sql
```

then make yourself an admin (sign up first):

```sql
update public.profiles set role = 'admin' where email = 'you@example.com';
```

The migration is **idempotent**: every object uses `if not exists` /
`or replace`, and policies and triggers are dropped before being re-created.
Re-running it will not delete data.

## 2. Security model at a glance

| Concern | Mechanism |
| --- | --- |
| Role escalation | `handle_new_user()` only honours `role = 'driver'`; `'admin'` self-assignment is always downgraded to `rider`. `profiles.role` has **no column grant** for clients. |
| Driver verification | `drivers.verification_status` has no column grant. Admin-only RPC `admin_set_driver_status()`. |
| Fares | `estimate_*`/`fare_calc()` compute on the server in a `before insert` trigger; the client's `estimated_fare` and `status` are overwritten. |
| Ride state machine | **No client `UPDATE` policy on `rides` at all.** Transitions only via `accept_ride / arrive_ride / start_ride / complete_ride / cancel_ride`, each of which re-checks status, ownership and role. |
| Start OTP | Stored in `ride_otps`, readable by the rider only — a driver holding the app cannot read it, they must be told the code. |
| Ratings | `ratee_id` and `total_rides` never settable by clients; participant check in a trigger. |
| Coupons | `coupons` is admin-only; riders price a code through `validate_coupon()` without seeing other codes. |
| Payments | No `INSERT`/`UPDATE` grant; rows are written by `complete_ride()`. |

## 3. Calling it from the app

```ts
// book
const { data: quote } = await supabase.rpc('validate_coupon', {
  p_code: 'JAIPUR50', p_vehicle_type: 'bike', p_km: 10, p_min: 20,
});

const { data: ride } = await supabase
  .from('rides')
  .insert({
    vehicle_type: 'bike',
    pickup_address, pickup_lat, pickup_lng,
    drop_address, drop_lat, drop_lng,
    distance_km, duration_min,
    payment_method: 'cash',
  })
  .select()
  .single();

// driver side
await supabase.rpc('accept_ride', { p_ride_id: ride.id });
await supabase.rpc('arrive_ride', { p_ride_id: ride.id });
await supabase.rpc('start_ride',  { p_ride_id: ride.id, p_otp: otpFromRider });
await supabase.rpc('complete_ride', { p_ride_id: ride.id, p_distance_km: 12, p_duration_min: 25 });

// presence
await supabase.rpc('update_driver_location', { p_lat, p_lng });
const { data: nearby } = await supabase.rpc('nearby_drivers', {
  p_lat, p_lng, p_radius_km: 5, p_type: 'bike',
});
```

Realtime: `supabase.channel('rides').on('postgres_changes', …)` on `rides`,
`drivers` and `notifications`. All three use `REPLICA IDENTITY FULL` so
`UPDATE` payloads carry the previous row.

Storage: `avatars/<uid>/…` (public bucket) and
`driver-documents/<uid>/<doc>.*` (private; the folder name **must** be the
user id or the policy rejects the upload).

## 4. Tests

`tests/schema.test.mjs` boots an embedded PostgreSQL (PGlite), stubs the parts
of the Supabase platform the schema needs (`auth.users`, `auth.uid()`,
`storage.*`, the `anon`/`authenticated` roles), applies the migration twice,
then asserts the security properties above as real `authenticated` DB clients.

```bash
npm install --no-save @electric-sql/pglite
npm run test:supabase      # or: node supabase/tests/schema.test.mjs
```

The suite fails if RLS is dropped from any table, if a client can update a
ride directly, if a driver can read the OTP, if a rider can read another
rider's ride, or if any fare/rating/role value can be forged.
