# Drive Partner — Backend Integration & Contract Specification

**Document ID:** DP-BACKEND-SPEC-001 | **Version:** 2.1 (Supabase Auth, Postgres/PostGIS, RLS & Private Storage Integrated) | **Date:** 2026-11-11  
**Implementation Status:**
- **TypeScript + Fastify + Supabase PostgreSQL + PostGIS Modular Monolith:** Implemented under `/backend` (`npm run typecheck`, `npm run build`, and `npm test` `12/12` passing across `4` suites; forward migrations `001_initial_schema.sql` & `002_supabase_auth_and_rls.sql` ready).
- **Canonical Contracts:** Synchronized in `/contracts/openapi.yaml` and `/contracts/events.md` (`D-018` Resolved).

> **ARCHITECTURAL BOUNDARIES:**
> - **Supabase Integration (`ADR-004-REV`):** Replaces Firebase Phone Auth and Firebase Storage with **Supabase Auth** (GoTrue Phone OTP on Android `LivePhoneAuthBoundary` -> JWKS `ES256`/`RS256` verification via `SUPABASE_JWKS_URL` on `/backend`), **Supabase PostgreSQL + PostGIS** (with RLS in `002_supabase_auth_and_rls.sql`), and **Supabase Storage** private bucket signed upload URLs (`SupabasePrivateStorageUploader`).
> - **Zero backend secrets belong in the Android client (`app/`).** Only `SUPABASE_URL` and public `SUPABASE_PUBLISHABLE_KEY` exist in Android `live` `BuildConfig`; `SUPABASE_SECRET_KEY` and `DATABASE_URL` live strictly server-side in `/backend` env.
> - **Live payouts (`LIVE_PAYOUTS_ENABLED=false`)** and **SOS responder webhook (`SOS_RESPONDER_WEBHOOK_URL=""`)** stay truthfully gated off until external provider sign-offs (`D-003`, `D-005`, `D-014`).

---

## 1. System Ownership Matrix

| Domain | Authoritative Owner | Client Role (Android `app/`) | Backend Implementation (`/backend`) |
|---|---|---|---|
| Phone OTP verification | Supabase Auth (GoTrue OTP + JWKS) | `LivePhoneAuthBoundary` calls `/auth/v1/otp` & `/auth/v1/verify` with public `SUPABASE_PUBLISHABLE_KEY` -> posts `idToken` to `POST /api/v1/auth/session` | `src/middleware/auth.ts` (JWKS `ES256`/`RS256` verification via `SUPABASE_JWKS_URL`; `hs256-dev` forbidden in `production`) |
| Identity, Vehicle, KYC status | Supabase PostgreSQL (`users.auth_uid`, `drivers`, `vehicles`, `driver_documents`, `audit_logs`) + RLS | Uploads to short-lived private signed upload URL; confirms object key only | `src/db/pgRepository.ts`, `SupabasePrivateStorageUploader`, `src/common/validators.ts` (blocks raw Aadhaar & public URLs) |
| Online/Offline & Eligibility | PostgreSQL + PostGIS (`driver_presence`, `driver_location_events`, `service_zones`) | Sends contextual location pings while ONLINE; switches state only on `200 OK` | `/api/v1/home`, `/api/v1/drivers/eligibility`, `/api/v1/availability/online|offline`, `/api/v1/locations` |
| Dispatch, Orders, Offers | PostgreSQL (`orders`, `order_offers`) + WebSocket `/ws/v1/driver` + FCM wakeup | Subscribes to WS, fetches `/api/v1/offers/active`, sends explicit Accept/Reject with `Idempotency-Key` | Atomic `SELECT ... FOR UPDATE` order claim + single active trip partial index `idx_trips_one_active_per_driver` |
| Trips & Order History | PostgreSQL (`trips`, `trip_events`, `earning_statements`) | Renders server state, restores active trip via `/api/v1/trips/active` | Optimistic version locking (`expectedVersion`), server fare breakdown (`src/common/money.ts`) |
| Fares, Ledger, Wallet, Payouts | PostgreSQL (`fare_configs`, `earning_statements`, `ledger_transactions`, `ledger_entries`, `payouts`) | Displays integer-paise server summaries (`MoneyPaise`), submits tokenized payout requests | Integer `BIGINT` minor paise, `assertBalancedDoubleEntry`, DB immutability trigger `trg_ledger_entries_immutable`, HMAC payout webhook |
| Push Notifications | Backend `RealtimeHub` + FCM wakeup -> REST resync | Receives minimal `{ eventId, eventType, resourceId }` / `{ type, entityId, dedupeKey }` wakeup and fetches REST | `src/modules/realtime/ws.gateway.ts` (no PII, GPS, or balances emitted over WS) |

---

## 2. Reconciled Canonical Enums (`D-018` Resolved vs `docs/05_SCHEMA (1).md`)

| Domain Enum | Canonical Values (Backend SQL, OpenAPI & Android DTOs) |
|---|---|
| `AccountStatus` / `DriverAccountStatus` | `APPLICANT`, `PENDING_REVIEW`, `ACTIVE`, `SUSPENDED`, `REJECTED`, `CLOSED` |
| `ServiceCategory` / `ServiceTypeCode` | `BIKE`, `AUTO`, `CAB`, `DELIVERY` |
| `DocumentType` | `DRIVING_LICENSE`, `VEHICLE_RC`, `VEHICLE_INSURANCE`, `PERMIT`, `PAN_CARD`, `AADHAAR_COMPLIANT_REF`, `PROFILE_PHOTO` |
| `DocumentStatus` | `NOT_SUBMITTED`, `UPLOADED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `EXPIRED` |
| `DutyStatus` / `RemoteDutyStatus` | `OFFLINE`, `GOING_ONLINE`, `ONLINE`, `GOING_OFFLINE`, `BLOCKED` |
| `OfferStatus` | `CREATED`, `OFFERED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `WITHDRAWN` |
| `TripStatus` | `ASSIGNED`, `EN_ROUTE_TO_PICKUP`, `ARRIVED_AT_PICKUP`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `DISPUTED` |
| `PayoutStatus` | `REQUESTED`, `PROCESSING`, `PAID`, `FAILED`, `CANCELLED`, `REVERSED` |
| `TicketStatus` / `SupportTicketStatus` | `OPEN`, `IN_PROGRESS`, `WAITING_FOR_DRIVER`, `RESOLVED`, `CLOSED` |

---

## 3. REST & WebSocket Route Inventory (1-to-1 with `/contracts/openapi.yaml` & Android Retrofit APIs)

All Android Retrofit interfaces use **relative paths** (`api/v1/...`) paired with a trailing-slash `baseUrl`:

- **Health (`DrivePartnerHealthRetrofitApi`):** `GET /api/v1/health`
- **Auth, Profile, Vehicle & KYC (`DrivePartnerAuthRetrofitApi`):**
  - `POST /api/v1/auth/session`, `GET /api/v1/me`, `POST /api/v1/auth/logout`
  - `GET /api/v1/drivers/me`, `POST /api/v1/drivers/profile`
  - `GET /api/v1/vehicles`, `POST /api/v1/vehicles`
  - `POST /api/v1/documents/upload-intent`, `POST /api/v1/documents`, `GET /api/v1/documents`
- **Eligibility, Duty, Zones & Location (`DrivePartnerDutyRetrofitApi`):**
  - `GET /api/v1/home`, `GET /api/v1/drivers/eligibility`
  - `GET /api/v1/drivers/preferences`, `PUT /api/v1/drivers/preferences`
  - `GET /api/v1/zones?city=JAI`
  - `POST /api/v1/availability/online`, `POST /api/v1/availability/offline`, `POST /api/v1/locations`
- **Dispatch & Offers (`DrivePartnerDispatchRetrofitApi`):**
  - `GET /api/v1/offers/active`, `GET /api/v1/offers/:offerId`
  - `POST /api/v1/offers/:offerId/accept`, `POST /api/v1/offers/:offerId/reject`
- **Trips & Order History (`DrivePartnerTripsOrdersRetrofitApi`):**
  - `GET /api/v1/trips/active`, `POST /api/v1/trips/:tripId/events`
  - `GET /api/v1/orders`, `GET /api/v1/orders/:orderId`
- **Earnings, Wallet, Ledger & Payouts (`DrivePartnerFinancialRetrofitApi` + Webhooks):**
  - `GET /api/v1/earnings/summary`, `GET /api/v1/earnings/transactions`, `GET /api/v1/earnings/statements/:statementId`
  - `GET /api/v1/wallet`, `GET /api/v1/payout-accounts`, `POST /api/v1/payout-accounts`
  - `POST /api/v1/payouts`, `GET /api/v1/payouts`, `POST /api/v1/webhooks/payouts/:provider`
- **Retention, Safety, Support & Notifications (`DrivePartnerRetentionSafetyRetrofitApi`):**
  - `GET /api/v1/campaigns`, `GET /api/v1/referrals/me`, `POST /api/v1/referrals/apply`
  - `GET /api/v1/safety/contacts`, `POST /api/v1/safety/contacts`, `POST /api/v1/safety/sos`, `POST /api/v1/safety/incidents`, `GET /api/v1/safety/insurance`
  - `GET /api/v1/support/tickets`, `POST /api/v1/support/tickets`, `GET /api/v1/support/tickets/:ticketId/messages`, `POST /api/v1/support/tickets/:ticketId/messages`
  - `GET /api/v1/notifications`, `POST /api/v1/notifications/:notificationId/read`, `GET /api/v1/notifications/preferences`, `PUT /api/v1/notifications/preferences`
- **Admin Ops (`x-admin-api-key` gated):**
  - `POST /api/v1/admin/drivers/:driverId/documents/:documentId/review`
  - `POST /api/v1/admin/orders`
  - `GET /api/v1/admin/audit-logs`
- **Realtime WebSocket:** `GET /ws/v1/driver` (envelope & FCM fallback schema in `/contracts/events.md`).

---

## 4. Remaining External Dependencies & Sign-Offs

| ID | Dependency / Credential | Blocking Scope | Current Behavior |
|---|---|---|---|
| `D-001` | Approved Brand Name, Package Finalization & Visual Assets | Release branding | `"Drive Partner"` placeholder |
| `D-002` | Approved Launch City Polygons & Categories | Production dispatch zones | Jaipur dev seed in `backend/src/db/seed-dev.ts` |
| `D-003` | Fare Config, Commission, Tax & Payout Policies | Production fare rules & payouts | Server fare matrix in `money.ts` (`LIVE_PAYOUTS_ENABLED=false`) |
| `D-004` | Insurance Provider & Policy Legal Copy | Safety Insurance card | Defaults `hasConfirmedPolicy=false` |
| `D-005` | Firebase `google-services.json`, Maps Key, S3/GCS Bucket & Payout Keys | Staging/Prod Auth, FCM, Maps, Uploads, Payouts | Clean abstraction boundaries; `UnconfiguredApiTransport` when `API_BASE_URL` blank |
| `D-006` | Automated KYC Provider & Retention Policy | Production automated KYC | Manual admin API review + private object keys + raw Aadhaar block |
| `D-007` | Upstream Passenger / Customer Order System | Real demand supply | Admin order dispatch endpoint for integration testing |
| `D-009` | Local JDK 17 / Android SDK 35 & Local PostgreSQL Host | Local Gradle/PG runtime execution | Static Android verification + hermetic backend tests pass; CI workflows added for Android & Backend |
| `D-014` | Masked Telephony & SOS Responder Webhook | Live call masking & SOS dispatch | Masked contact button disabled; SOS returns `503 SOS_RESPONDER_UNCONFIGURED` when unconfigured |
