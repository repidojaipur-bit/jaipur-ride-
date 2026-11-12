/**
 * Runs the real Jaipur Ride Supabase migration against an embedded PostgreSQL
 * (PGlite) instance with Supabase's auth/storage schemas stubbed out, then
 * asserts the security model actually holds.
 *
 *   npm install --no-save @electric-sql/pglite
 *   node supabase/tests/schema.test.mjs
 */
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PGlite } from '@electric-sql/pglite';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');

let pass = 0;
const failures = [];

function check(label, condition, detail) {
  if (condition) {
    pass += 1;
    console.log(`  ok   ${label}`);
  } else {
    failures.push(label);
    console.log(`  FAIL ${label}${detail ? ` -> ${detail}` : ''}`);
  }
}

function throws(label, fn) {
  try {
    const value = fn();
    check(label, false, `did not throw (returned ${JSON.stringify(value)})`);
  } catch (error) {
    check(label, true);
  }
}

async function rejects(label, promise, expectedCode) {
  try {
    await promise;
    check(label, false, 'did not reject');
  } catch (error) {
    if (expectedCode && error.code !== expectedCode) {
      check(label, false, `rejected with ${error.code} (${error.message}); expected ${expectedCode}`);
      return;
    }
    check(label, true);
  }
}

// RLS/grant enforcement must fail as insufficient_privilege (42501), never as
// some incidental constraint error - otherwise the test could pass for the
// wrong reason.
const DENIED = '42501';

async function file(name, label) {
  console.log(`\n${label}`);
  const sql = await readFile(path.join(root, 'migrations', name), 'utf8');
  return sql;
}

async function run(db, sql, params = []) {
  const result = await db.query(sql, params);
  return result.rows;
}

// `select (f()).*` expands the composite and re-plans f() once per output
// column, i.e. it would call these non-idempotent RPCs two or three times.
// Call the function exactly once and read the needed columns from the
// returned row value instead.
async function callRpc(db, sql, params = []) {
  // to_jsonb(...) converts the composite to a JS object in a single call.
  const wrapped = sql.replace(/^select (public\.[\s\S]+?) as r$/, 'select to_jsonb($1) as r');
  const rows = await run(db, wrapped, params);
  if (rows.length !== 1 || rows[0].r === null) {
    throw new Error(`RPC returned no row: ${sql}`);
  }
  return rows[0].r;
}

async function asUser(db, uid) {
  await run(db, `select set_config('test.uid', $1, false)`, [uid]);
}

async function resetToOwner(db) {
  await run(db, `select set_config('test.uid', '', false)`);
  await run(db, 'reset role');
}

// Impersonate a signed-in API client: JWT subject + the `authenticated` role
// PostgREST would assume. Always go through here for RLS assertions - running
// as the table owner would bypass RLS and make every test pass.
async function asClient(db, uid) {
  await resetToOwner(db);
  await asUser(db, uid);
  await run(db, 'set role authenticated');
}

async function main() {
  const db = await PGlite.create();

  console.log('\n== harness + migration ==');
  await db.exec(await readFile(path.join(here, '00_supabase_stubs.sql'), 'utf8'));
  const migration = await file(
    '20261112000000_jaipur_ride_init.sql',
    '== applying migration (1st time) =='
  );
  await db.exec(migration);
  check('migration applies cleanly on a fresh database', true);

  console.log('\n== migration is re-runnable (idempotent) ==');
  try {
    await db.exec(migration);
    check('migration re-runs without error', true);
  } catch (error) {
    check('migration re-runs without error', false, error.message);
  }

  console.log('\n== seed data ==');
  const fares = await run(db, 'select vehicle_type, base_fare, per_km from public.fare_rules order by 1');
  check('5 fare rules seeded', fares.length === 5, `got ${fares.length}`);

  // ------------------------------------------------------------------
  console.log('\n== signup trigger: profile + driver row, admin never self-assigned ==');
  await db.exec(`
    insert into auth.users (id, email, phone, raw_user_meta_data) values
      ('00000000-0000-0000-0000-000000000001', 'rider@jaipur.test', null,
       '{"full_name":"Rider One","phone":"+919000000001"}'),
      ('00000000-0000-0000-0000-000000000002', 'driver@jaipur.test', null,
       '{"full_name":"Driver One","role":"driver"}'),
      ('00000000-0000-0000-0000-000000000003', 'attacker@jaipur.test', null,
       '{"full_name":"Attacker","role":"admin"}'),
      ('00000000-0000-0000-0000-000000000004', 'rider2@jaipur.test', null,
       '{"full_name":"Rider Two"}'),
      ('00000000-0000-0000-0000-000000000009', 'admin@jaipur.test', null,
       '{"full_name":"Admin"}');
  `);

  let rows = await run(db, 'select id, role, phone from public.profiles order by id');
  check('a profile is created for every new auth user', rows.length === 5, `got ${rows.length}`);
  check('rider role defaults to rider', rows[0].role === 'rider');
  check('driver metadata sets role=driver', rows[1].role === 'driver');
  check(
    "metadata role='admin' is downgraded to rider",
    rows[2].role === 'rider',
    `got ${rows[2].role}`
  );
  check('phone falls back to metadata', rows[0].phone === '+919000000001');

  rows = await run(db, 'select id from public.drivers');
  check('only the driver gets a drivers row', rows.length === 1 && rows[0].id.endsWith('0002'));

  await run(db, `update public.profiles set role = 'admin' where id = '00000000-0000-0000-0000-000000000009'`);

  console.log('\n== signup: duplicate phone does not break sign-up ==');
  try {
    await db.exec(`
      insert into auth.users (id, raw_user_meta_data)
      values ('00000000-0000-0000-0000-000000000005', '{"full_name":"Phone Thief","phone":"+919000000001"}');
    `);
  } catch (error) {
    check('a duplicate phone does not abort sign-up', false, error.message);
  }
  rows = await run(db, `select phone from public.profiles where id = '00000000-0000-0000-0000-000000000005'`);
  check('the colliding phone is dropped, profile still created', rows.length === 1 && rows[0].phone === null);

  // ------------------------------------------------------------------
  console.log('\n== fare_calc + coupon_discount ==');
  rows = await run(db, `select public.fare_calc('bike', 10, 20) as v`);
  // 20 + 7*10 + 0.5*20 = 100
  check('bike fare for 10km/20min = 100', Number(rows[0].v) === 100, rows[0].v);
  rows = await run(db, `select public.fare_calc('bike', 0, 0) as v`);
  check('min_fare floor applies', Number(rows[0].v) === 30, rows[0].v);

  await db.exec(`
    insert into public.coupons (id, code, discount_type, discount_value, max_discount, min_fare)
    values ('00000000-0000-0000-0000-0000000000c1', 'JAIPUR50', 'percent', 50, 40, 60);
    insert into public.coupons (id, code, discount_type, discount_value, valid_until, is_active)
    values ('00000000-0000-0000-0000-0000000000c2', 'OLD50', 'flat', 50, now() - interval '1 day', true);
    insert into public.coupons (id, code, discount_type, discount_value, usage_limit, used_count)
    values ('00000000-0000-0000-0000-0000000000c3', 'USEDUP', 'flat', 50, 5, 5);
  `);
  rows = await run(db, `select public.coupon_discount('00000000-0000-0000-0000-0000000000c1', 100) as v`);
  check('percent coupon capped at max_discount', Number(rows[0].v) === 40, rows[0].v);
  rows = await run(db, `select public.coupon_discount('00000000-0000-0000-0000-0000000000c1', 50) as v`);
  check('coupon below min_fare is rejected (0)', Number(rows[0].v) === 0, rows[0].v);
  rows = await run(db, `select public.coupon_discount('00000000-0000-0000-0000-0000000000c2', 500) as v`);
  check('expired coupon is rejected (0)', Number(rows[0].v) === 0, rows[0].v);
  rows = await run(db, `select public.coupon_discount('00000000-0000-0000-0000-0000000000c3', 500) as v`);
  check('exhausted coupon is rejected (0)', Number(rows[0].v) === 0, rows[0].v);

  // ------------------------------------------------------------------
  console.log('\n== ride insert: server-computed fare, sanitised fields, OTP, history ==');
  const rider = '00000000-0000-0000-0000-000000000001';
  await asUser(db, rider);
  await run(db, `
    insert into public.rides
      (id, rider_id, vehicle_type, status, driver_id,
       pickup_address, pickup_lat, pickup_lng,
       drop_address, drop_lat, drop_lng,
       distance_km, duration_min, estimated_fare, payment_method)
    values
      ('00000000-0000-0000-0000-0000000000d1', $1, 'bike', 'completed', $2,
       'Hawa Mahal', 26.9239, 75.8267,
       'Jal Mahal', 26.9535, 75.8463,
       10, 20, 1, 'cash')
  `, [rider, '00000000-0000-0000-0000-000000000002']);

  rows = await run(db, `select * from public.rides where id = '00000000-0000-0000-0000-0000000000d1'`);
  const ride = rows[0];
  check('status forced to requested on insert', ride.status === 'requested', ride.status);
  check('driver_id forced to null on insert', ride.driver_id === null);
  check('client-supplied estimated_fare ignored', Number(ride.estimated_fare) === 100, ride.estimated_fare);
  check('rider_id forced to auth.uid()', ride.rider_id === rider);

  rows = await run(db, `select otp from public.ride_otps where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  check('a 4-digit OTP is generated', /^\d{4}$/.test(rows[0].otp), rows[0].otp);

  rows = await run(db, `select status from public.ride_status_history where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  check('initial status history row written', rows.length === 1 && rows[0].status === 'requested');

  console.log('\n== one active ride per rider ==');
  await rejects('second active ride for the same rider is blocked', run(db, `
    insert into public.rides (rider_id, vehicle_type, pickup_address, pickup_lat, pickup_lng,
                              drop_address, drop_lat, drop_lng, distance_km, duration_min)
    values ($1, 'bike', 'A', 26.9, 75.8, 'B', 26.95, 75.85, 3, 5)
  `, [rider]));

  // ------------------------------------------------------------------
  console.log('\n== ride lifecycle RPCs ==');
  const driver = '00000000-0000-0000-0000-000000000002';
  await resetToOwner(db);
  await db.exec(`
    insert into public.vehicles (id, driver_id, vehicle_type, plate_number, seats)
    values ('00000000-0000-0000-0000-0000000000e1', '${driver}', 'bike', 'RJ14AB1234', 2);
  `);

  await asUser(db, driver);
  await rejects('unverified driver cannot accept', run(db, `select public.accept_ride('00000000-0000-0000-0000-0000000000d1')`));

  await resetToOwner(db);
  await run(db, `update public.drivers set verification_status = 'approved', is_online = true where id = $1`, [driver]);

  await asUser(db, driver);
  rows = [await callRpc(db, `select public.accept_ride('00000000-0000-0000-0000-0000000000d1') as r`)];
  check('approved online driver accepts', rows[0].status === 'accepted', rows[0].status);
  check('vehicle auto-attached on accept', rows[0].vehicle_id === '00000000-0000-0000-0000-0000000000e1');

  await rejects('the same ride cannot be accepted twice', run(db, `select public.accept_ride('00000000-0000-0000-0000-0000000000d1')`));

  rows = [await callRpc(db, `select public.arrive_ride('00000000-0000-0000-0000-0000000000d1') as r`)];
  check('driver marks arrival', rows[0].status === 'arrived');

  await rejects('wrong OTP cannot start the ride', run(db, `select public.start_ride('00000000-0000-0000-0000-0000000000d1', 'abcd')`));
  rows = await run(db, `select otp from public.ride_otps where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  rows = [await callRpc(db, `select public.start_ride('00000000-0000-0000-0000-0000000000d1', $1) as r`, [rows[0].otp])];
  check('correct OTP starts the ride', rows[0].status === 'in_progress');

  await rejects('skipping states (start -> cancelled by rider) is impossible', run(db, `
    select public.cancel_ride('00000000-0000-0000-0000-0000000000d1', 'nope')
  `).then(async () => {
    // cancel_ride blocks in_progress; the driver is not the rider either.
    throw new Error('unexpected success');
  }));

  rows = [await callRpc(db, `select public.complete_ride('00000000-0000-0000-0000-0000000000d1', 12, 25) as r`)];
  check('ride completes', rows[0].status === 'completed');
  // 20 + 7*12 + 0.5*25 = 116.5
  check('final fare recomputed from actuals', Number(rows[0].final_fare) === 116.5, rows[0].final_fare);

  rows = await run(db, `select count(*)::int as c from public.payments where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  check('a payment row is created on completion', rows[0].c === 1);
  rows = await run(db, `select status from public.payments where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  check('cash payment is recorded as paid', rows[0].status === 'paid');

  rows = await run(db, `select total_rides from public.drivers where id = $1`, [driver]);
  check('driver total_rides incremented', rows[0].total_rides === 1);

  rows = await run(db, `select status from public.ride_status_history where ride_id = '00000000-0000-0000-0000-0000000000d1' order by id`);
  check(
    'every transition is recorded in history',
    rows.map((r) => r.status).join(',') === 'requested,accepted,arrived,in_progress,completed',
    rows.map((r) => r.status).join(',')
  );

  // ------------------------------------------------------------------
  console.log('\n== ratings ==');
  await asUser(db, rider);
  await run(db, `
    insert into public.ratings (ride_id, rater_id, ratee_id, score, comment)
    values ('00000000-0000-0000-0000-0000000000d1', $1,
            '00000000-0000-0000-0000-000000000009', 5, 'Great trip')
  `, [rider]);
  rows = await run(db, `select ratee_id from public.ratings where ride_id = '00000000-0000-0000-0000-0000000000d1'`);
  check('ratee is derived server-side, client value ignored', rows[0].ratee_id === driver, rows[0].ratee_id);

  rows = await run(db, `select rating_avg, rating_count from public.drivers where id = $1`, [driver]);
  check('driver rating rollup updated', Number(rows[0].rating_avg) === 5 && rows[0].rating_count === 1);

  await rejects('the same rider cannot rate a ride twice', run(db, `
    insert into public.ratings (ride_id, rater_id, score) values
    ('00000000-0000-0000-0000-0000000000d1', $1, 1)
  `, [rider]));

  await asUser(db, '00000000-0000-0000-0000-000000000004');
  await rejects('an outsider cannot rate a ride they were not part of', run(db, `
    insert into public.ratings (ride_id, rater_id, score) values
    ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-000000000004', 1)
  `));

  // ------------------------------------------------------------------
  console.log('\n== RLS: rides ==');
  await resetToOwner(db);
  await run(db, 'grant usage on schema public to authenticated, anon');
  await run(db, 'grant usage on schema auth, storage to authenticated, anon');

  await asClient(db, rider);
  rows = await run(db, `select count(*)::int as c from public.rides`);
  check('rider sees only their own rides', rows[0].c === 1, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.ride_otps`);
  check('rider can read the OTP for their ride', rows[0].c === 1, rows[0].c);

  await resetToOwner(db);
  await asClient(db, '00000000-0000-0000-0000-000000000004');
  rows = await run(db, `select count(*)::int as c from public.rides`);
  check("an unrelated rider sees no rides", rows[0].c === 0, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.ride_otps`);
  check('an unrelated rider cannot read the OTP', rows[0].c === 0, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.rides where id = '00000000-0000-0000-0000-0000000000d1'`);
  check('an unrelated rider cannot read the ride row', rows[0].c === 0);

  await resetToOwner(db);
  await asClient(db, driver);
  rows = await run(db, `select count(*)::int as c from public.rides`);
  check('the assigned driver sees the ride', rows[0].c === 1, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.ride_otps`);
  check('the driver can NOT read the ride OTP', rows[0].c === 0, rows[0].c);
  rows = await run(db, "select count(*)::int as c from public.profiles where id = '00000000-0000-0000-0000-000000000001'");
  check('the driver can read their rider counterpart profile', rows[0].c === 1, rows[0].c);

  await resetToOwner(db);
  await asClient(db, '00000000-0000-0000-0000-000000000009');
  rows = await run(db, `select count(*)::int as c from public.rides`);
  check('admin sees all rides', rows[0].c === 1, rows[0].c);

  // The app shows the plate/colour on the ride card, so a rider must be able
  // to read the vehicle that was assigned to one of their own rides.
  await asClient(db, rider);
  rows = await run(db, `
    select plate_number from public.vehicles
     where id = '00000000-0000-0000-0000-0000000000e1'
  `);
  check(
    "rider can read the vehicle used on their ride",
    rows.length === 1 && rows[0].plate_number === 'RJ14AB1234',
    JSON.stringify(rows)
  );
  rows = await run(db, `select count(*)::int as c from public.payments`);
  check('rider can read the payment for their ride', rows[0].c === 1, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.fare_rules`);
  check('every rider can read the fare table', rows[0].c === 5, rows[0].c);

  console.log('\n== RLS: no privileged columns reachable by clients ==');
  await resetToOwner(db);
  await asClient(db, driver);
  await rejects('driver cannot promote themselves to admin', run(db, `
    update public.profiles set role = 'admin' where id = '${driver}'
  `), DENIED);
  await rejects('driver cannot self-approve verification', run(db, `
    update public.drivers set verification_status = 'approved' where id = '${driver}'
  `), DENIED);
  await rejects('driver cannot forge their rating', run(db, `
    update public.drivers set rating_avg = 5.00, total_rides = 9999 where id = '${driver}'
  `), DENIED);
  await run(db, `
    insert into public.rides (rider_id, vehicle_type, pickup_address, pickup_lat, pickup_lng,
                              drop_address, drop_lat, drop_lng)
    values ('00000000-0000-0000-0000-000000000004', 'bike', 'A', 26.9, 75.8, 'B', 26.95, 75.85)
  `);
  rows = await run(db, `
    select rider_id from public.rides
     where vehicle_type = 'bike' and pickup_address = 'A'
  `);
  check(
    'forged rider_id is rewritten to the caller (no ride on someone else\'s behalf)',
    rows.length === 1 && rows[0].rider_id === driver,
    JSON.stringify(rows)
  );
  await rejects('no client UPDATE policy on rides (RPCs only)', run(db, `
    update public.rides set status = 'completed' where id = '00000000-0000-0000-0000-0000000000d1'
  `), DENIED);
  await rejects('no client can insert payments directly', run(db, `
    insert into public.payments (ride_id, amount, method, status)
    values ('00000000-0000-0000-0000-0000000000d1', 1, 'cash', 'paid')
  `), DENIED);
  await rejects('a non-admin cannot open the coupon table', run(db, `
    insert into public.coupons (code, discount_type, discount_value) values ('FREE100', 'flat', 100)
  `), DENIED);
  await rejects('a non-admin cannot call admin_set_role', run(db, `
    select public.admin_set_role('${driver}', 'admin')
  `));

  await resetToOwner(db);
  await asClient(db, rider);
  rows = await run(db, `select public.validate_coupon('JAIPUR50', 'bike', 10, 20) as v`);
  check('rider can price a coupon without reading the coupons table', Number(rows[0].v) === 40, rows[0].v);
  rows = await run(db, `select public.validate_coupon('jaipur50', 'bike', 10, 20) as v`);
  check('coupon codes are case-insensitive', Number(rows[0].v) === 40, rows[0].v);
  rows = await run(db, `select public.validate_coupon('NOSUCHCODE', 'bike', 10, 20) as v`);
  check('an unknown code is simply worth nothing', Number(rows[0].v) === 0, rows[0].v);
  await rejects('an unknown code cannot be resolved to an id', run(db, `
    select public.coupon_id_for_code('NOSUCHCODE')
  `), 'P0001');
  rows = await run(db, `select public.coupon_id_for_code('JAIPUR50') as v`);
  check('a valid code resolves to its id', rows[0].v === '00000000-0000-0000-0000-0000000000c1', rows[0].v);
  rows = await run(db, `select count(*)::int as c from public.coupons`);
  check('the coupons table itself stays hidden', rows[0].c === 0, rows[0].c);

  console.log('\n== RLS: driver presence + nearby_drivers ==');
  await resetToOwner(db);
  await run(db, `update public.drivers set current_lat = 26.9124, current_lng = 75.7873,
                 last_location_at = now() where id = $1`, [driver]);
  await asClient(db, driver);
  await run(db, `select public.update_driver_location(26.9124, 75.7873)`);
  rows = await run(db, `select count(*)::int as c from public.nearby_drivers(26.9239, 75.8267, 5, 'bike')`);
  check('nearby_drivers finds the online driver', rows[0].c === 1, rows[0].c);
  rows = await run(db, `select count(*)::int as c from public.nearby_drivers(26.9239, 75.8267, 5, 'suv')`);
  check('nearby_drivers respects the vehicle filter', rows[0].c === 0, rows[0].c);
  await rejects('invalid coordinates are rejected', run(db, `select public.update_driver_location(999, 75.7)`));

  // ------------------------------------------------------------------
  console.log('\n== storage policies ==');
  await resetToOwner(db);
  rows = await run(db, `select id, public from storage.buckets order by id`);
  check('both buckets exist', rows.length === 2);
  check("avatars bucket is public, documents bucket is private",
    rows[0].public === true && rows[1].public === false);

  await asClient(db, driver);
  await run(db, `insert into storage.objects (bucket_id, name) values ('driver-documents', '${driver}/license.pdf')`);
  check('driver uploads into their own document folder', true);
  await rejects('driver cannot upload into another driver folder', run(db, `
    insert into storage.objects (bucket_id, name)
    values ('driver-documents', '00000000-0000-0000-0000-000000000009/license.pdf')
  `), DENIED);
  rows = await run(db, `select count(*)::int as c from storage.objects where bucket_id = 'driver-documents'`);
  check('driver reads only their own documents', rows[0].c === 1, rows[0].c);

  // ------------------------------------------------------------------
  console.log('\n== booking with a coupon ==');
  await asClient(db, '00000000-0000-0000-0000-000000000004');
  await run(db, `
    insert into public.rides (id, vehicle_type, pickup_address, pickup_lat, pickup_lng,
                              drop_address, drop_lat, drop_lng, distance_km, duration_min,
                              coupon_id)
    values ('00000000-0000-0000-0000-0000000000da', 'bike', 'C', 26.9, 75.8, 'D', 26.95, 75.85,
            10, 20, '00000000-0000-0000-0000-0000000000c1')
  `);
  await resetToOwner(db);
  rows = await run(db, `
    select estimated_fare, discount_amount from public.rides
     where id = '00000000-0000-0000-0000-0000000000da'
  `);
  check(
    'coupon discount applied server-side at booking',
    Number(rows[0].estimated_fare) === 100 && Number(rows[0].discount_amount) === 40,
    JSON.stringify(rows[0])
  );
  rows = await run(db, `select used_count from public.coupons where code = 'JAIPUR50'`);
  check('coupon usage counter incremented', rows[0].used_count === 1, rows[0].used_count);

  await asClient(db, '00000000-0000-0000-0000-000000000005');
  await rejects('a coupon below its min_fare blocks the booking', run(db, `
    insert into public.rides (vehicle_type, pickup_address, pickup_lat, pickup_lng,
                              drop_address, drop_lat, drop_lng, distance_km, duration_min,
                              coupon_id)
    values ('bike', 'E', 26.9, 75.8, 'F', 26.95, 75.85, 0, 0,
            '00000000-0000-0000-0000-0000000000c1')
  `), 'P0001');

  // ------------------------------------------------------------------
  console.log('\n== notifications on state changes ==');
  await resetToOwner(db);
  rows = await run(db, `
    select title from public.notifications
     where user_id = '00000000-0000-0000-0000-000000000001'
     order by created_at
  `);
  const riderTitles = rows.map((r) => r.title);
  check('rider was notified at every status change', riderTitles.length === 4, riderTitles.join('|'));
  rows = await run(db, `
    select title from public.notifications
     where user_id = '${driver}' order by created_at
  `);
  check(
    'driver is notified when the trip completes',
    rows.some((r) => r.title === 'Ride completed'),
    rows.map((r) => r.title).join('|')
  );

  // ------------------------------------------------------------------
  console.log('\n== one active ride per driver ==');
  await resetToOwner(db);
  await run(db, `update public.rides set status = 'cancelled'
                  where id = '00000000-0000-0000-0000-0000000000da'`);
  await run(db, `insert into public.rides (id, rider_id, vehicle_type, pickup_address, pickup_lat, pickup_lng,
                 drop_address, drop_lat, drop_lng, distance_km, duration_min)
                 values
                 ('00000000-0000-0000-0000-0000000000d8', '00000000-0000-0000-0000-000000000004',
                  'bike', 'P1', 26.90, 75.80, 'D1', 26.95, 75.85, 3, 6),
                 ('00000000-0000-0000-0000-0000000000d9', '00000000-0000-0000-0000-000000000001',
                  'bike', 'P2', 26.91, 75.81, 'D2', 26.96, 75.86, 3, 6)`);
  await asUser(db, driver);
  await callRpc(db, `select public.accept_ride('00000000-0000-0000-0000-0000000000d8') as r`);
  await rejects('a driver with an active ride cannot take a second one', run(db, `
    select public.accept_ride('00000000-0000-0000-0000-0000000000d9')
  `));
  await callRpc(db, `select public.cancel_ride('00000000-0000-0000-0000-0000000000d8', 'test cleanup') as r`);

  // ------------------------------------------------------------------
  console.log('\n== rider onboarding: become_driver ==');
  await resetToOwner(db);
  const rider2 = '00000000-0000-0000-0000-000000000004';
  await asUser(db, rider2);
  await run(db, `select public.become_driver()`);
  await resetToOwner(db);
  rows = await run(db, `select role from public.profiles where id = '${rider2}'`);
  check('become_driver promotes the profile role', rows[0].role === 'driver', rows[0].role);
  rows = await run(db, `select count(*)::int as c from public.drivers where id = '${rider2}'`);
  check('become_driver creates the drivers row', rows[0].c === 1, rows[0].c);
  await asUser(db, rider2);
  await run(db, `select public.become_driver()`);
  await resetToOwner(db);
  rows = await run(db, `select count(*)::int as c from public.drivers where id = '${rider2}'`);
  check('become_driver is idempotent', rows[0].c === 1, rows[0].c);

  // ------------------------------------------------------------------
  console.log('\n== document review ==');
  await asClient(db, driver);
  await run(db, `
    insert into public.driver_documents (id, driver_id, doc_type, file_path, status)
    values ('00000000-0000-0000-0000-0000000000f1', '${driver}', 'license',
            '${driver}/license.pdf', 'pending')
  `);
  await rejects('a driver cannot pre-approve their own document', run(db, `
    insert into public.driver_documents (driver_id, doc_type, file_path, status)
    values ('${driver}', 'rc', '${driver}/rc.pdf', 'approved')
  `), DENIED);
  await rejects('a driver cannot review their own document', run(db, `
    select public.admin_review_document('00000000-0000-0000-0000-0000000000f1', 'approved')
  `));

  await asClient(db, '00000000-0000-0000-0000-000000000009');
  await run(db, `select public.admin_review_document('00000000-0000-0000-0000-0000000000f1', 'approved')`);
  await resetToOwner(db);
  rows = await run(db, `
    select status, reviewed_by, reviewed_at from public.driver_documents
     where id = '00000000-0000-0000-0000-0000000000f1'
  `);
  check(
    'admin approval stamps reviewer and timestamp',
    rows[0].status === 'approved'
      && rows[0].reviewed_by === '00000000-0000-0000-0000-000000000009'
      && rows[0].reviewed_at !== null,
    JSON.stringify(rows[0])
  );

  // ------------------------------------------------------------------
  console.log('\n== admin workflows ==');
  await resetToOwner(db);
  await asClient(db, '00000000-0000-0000-0000-000000000009');
  await run(db, `select public.admin_set_driver_status('${driver}', 'rejected')`);
  rows = await run(db, `select verification_status from public.drivers where id = '${driver}'`);
  check('admin can change driver verification', rows[0].verification_status === 'rejected');
  await rejects('admin cannot revoke their own admin role', run(db, `
    select public.admin_set_role('00000000-0000-0000-0000-000000000009', 'rider')
  `));

  await resetToOwner(db);
  rows = await run(db, `
    select c.relname, c.relreplident
      from pg_class c join pg_namespace n on n.oid = c.relnamespace
     where n.nspname = 'public' and c.relname in ('rides','drivers','notifications')
  `);
  check('realtime tables use REPLICA IDENTITY FULL', rows.every((r) => r.relreplident === 'f'));
  rows = await run(db, `
    select tablename from pg_publication_tables where pubname = 'supabase_realtime' order by 1
  `);
  check('rides/drivers/notifications are published',
    rows.map((r) => r.tablename).join(',') === 'drivers,notifications,rides',
    rows.map((r) => r.tablename).join(','));

  rows = await run(db, `
    select count(*)::int as c from pg_policies where schemaname = 'public'
  `);
  check('expected number of public policies created', rows[0].c >= 20, rows[0].c);

  rows = await run(db, `
    select count(*)::int as c from pg_tables where schemaname = 'public' and rowsecurity = false
  `);
  check('every public table has RLS enabled', rows[0].c === 0, rows[0].c);

  await db.close();

  console.log(`\n${'='.repeat(60)}`);
  if (failures.length) {
    console.log(`${pass} passed, ${failures.length} FAILED`);
    for (const f of failures) console.log(`  - ${f}`);
    process.exit(1);
  }
  console.log(`All ${pass} checks passed.`);
}

main().catch((error) => {
  console.error('\nHARNESS ERROR:', error);
  process.exit(1);
});
