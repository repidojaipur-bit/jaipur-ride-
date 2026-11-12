# Drive Partner — Setup, Build & Release Guide

This guide covers configuring, building, running, and testing **both** sides of the monorepo:
- **Android Client (`app/`)**: Native Kotlin + Jetpack Compose (`demo` & `live` flavors).
- **Backend API (`/backend`)**: Fastify + TypeScript + PostgreSQL 16 + PostGIS 3.4+.
- **Contracts (`/contracts`)**: `openapi.yaml` & `events.md`.

---

## 1. Prerequisites

| Component | Version | Purpose |
|---|---|---|
| JDK | 17 (Temurin) | Compiling & running Android Gradle tasks (`D-009` in local headless sandboxes) |
| Android SDK | Platform 35, Build-Tools 35, Min SDK 24 | Building `:app` APKs / AABs and instrumentation tests |
| Gradle | 8.11.1 (`./gradlew` once wrapper JAR present, or system `gradle`) | Android build runner |
| Node.js & npm | Node 22+ / npm 10+ | Running `/backend` API, migrations, and test suite |
| Docker & Compose (or local PG 16) | PostgreSQL 16 + PostGIS 3.4+ | Running `/backend` stateful DB & spatial queries |

---

## 2. Backend Setup (`/backend`)

### 2.1 Install, Typecheck, Build & Test (Hermetic)
```bash
cd backend
npm ci
npm run typecheck
npm run build
npm test
```

### 2.2 Supabase Setup (Auth + PostgreSQL/PostGIS + Private Storage)
1. **Enable PostGIS in Supabase:** Open **Supabase Dashboard -> Database -> Extensions** and enable `postgis` and `pgcrypto`.
2. **Enable Phone Auth OTP Provider:** Open **Authentication -> Providers -> Phone**, configure your SMS OTP provider (e.g., Twilio/MessageBird/MSG91) so `/auth/v1/otp` and `/auth/v1/verify` deliver SMS OTPs cleanly.
3. **Create Private KYC Storage Bucket:** Open **Storage -> Buckets**, create private bucket **`drive-partner-kyc-private`** (keep **Public bucket = OFF**).
4. **Configure Local/Staging `backend/.env` (NEVER commit `.env`):**
```bash
cd backend
cp .env.example .env
# Set DATABASE_URL to your Supabase Transaction/Session Pooler PostgreSQL URL:
# DATABASE_URL=postgresql://postgres.<project-ref>:<DB_PASSWORD>@aws-0-<region>.pooler.supabase.com:6543/postgres
# AUTH_MODE=supabase
# SUPABASE_URL=https://zdkoqxkxeayimjzqxfvi.supabase.co
# SUPABASE_JWKS_URL=https://zdkoqxkxeayimjzqxfvi.supabase.co/auth/v1/.well-known/jwks.json
# SUPABASE_SECRET_KEY=<your-rotated-supabase-secret-key>
# SUPABASE_STORAGE_BUCKET=drive-partner-kyc-private
npm run migrate
npm run seed:dev
npm run dev
```

### 2.3 Local PostgreSQL + PostGIS Alternative via Docker Compose
```bash
cd backend
cp .env.example .env
docker compose up -d db
npm run migrate
npm run seed:dev
npm run dev
```

Verify health endpoint:
```bash
curl -s http://localhost:8080/api/v1/health | jq .
# Expected: { "status": "ok", "databaseReady": true, "postgisReady": true, ... }
```

### 2.4 Critical Backend Environment Flags
- **`AUTH_MODE`:**
  - `supabase` (default): verifies Supabase Auth JWTs (`ES256`/`RS256`) using `SUPABASE_JWKS_URL`.
  - `hs256-dev`: **Blocked automatically in `NODE_ENV=production`**; used for local integration testing and automated tests.
  - `firebase`: optional legacy verifier if needed.
- **`SUPABASE_SECRET_KEY`:** Privileged backend key used strictly server-side by `SupabasePrivateStorageUploader` to sign private KYC upload URLs. **Never commit to Git or include in Android.**
- **`LIVE_PAYOUTS_ENABLED`:** Must remain `false` until payout provider KYC, limits, settlement schedule, and legal/finance sign-offs (`D-003`, `D-005`) are approved.
- **`PAYOUT_WEBHOOK_SECRET`:** HMAC-SHA256 shared secret used to verify `x-webhook-signature` on `POST /api/v1/webhooks/payouts/:provider`.
- **`SOS_RESPONDER_WEBHOOK_URL`:** Optional emergency responder webhook endpoint; when omitted, `POST /api/v1/safety/sos` truthfully fails with `503 SOS_RESPONDER_UNCONFIGURED` so drivers are guided to dial `112`.
- **`ADMIN_API_KEY`:** Required header (`x-admin-api-key`) for admin KYC approval, test dispatch order creation, and audit log reads.

---

## 3. Android Client Configuration (`app/`)

Non-secret configuration properties can be passed in `gradle.properties` (already pre-wired with non-secret `DRIVE_PARTNER_SUPABASE_URL` & `DRIVE_PARTNER_SUPABASE_PUBLISHABLE_KEY`), `~/.gradle/gradle.properties`, or `-P` Gradle flags:

| Gradle Property | Default | Description |
|---|---|---|
| `DRIVE_PARTNER_API_BASE_URL` | `""` | Backend REST base URL (e.g., `http://10.0.2.2:8080/` for emulator debug, or `https://api.example.com/` for staging/prod) |
| `DRIVE_PARTNER_REALTIME_WS_URL` | `""` | Backend WebSocket URL (e.g., `ws://10.0.2.2:8080/ws/v1/driver` or `wss://api.example.com/ws/v1/driver`) |
| `DRIVE_PARTNER_SUPABASE_URL` | configured in `gradle.properties` | Public Supabase project URL for `live` Phone OTP (`BuildConfig.SUPABASE_URL`) |
| `DRIVE_PARTNER_SUPABASE_PUBLISHABLE_KEY` | configured in `gradle.properties` | Public Supabase client publishable key (`BuildConfig.SUPABASE_PUBLISHABLE_KEY`, zero secrets) |
| `DRIVE_PARTNER_MAPS_API_KEY` | `""` | Google Maps SDK for Android key (`BuildConfig.MAPS_API_KEY`) |
| `DRIVE_PARTNER_KEYSTORE_PATH` | `""` | Path to release `.jks` / `.keystore` file |
| `DRIVE_PARTNER_KEYSTORE_PASSWORD` | `""` | Keystore password |
| `DRIVE_PARTNER_KEY_ALIAS` | `""` | Release signing key alias |
| `DRIVE_PARTNER_KEY_PASSWORD` | `""` | Release key password |

### 3.1 Build Variants (`demo` vs `live`)
- **`demoDebug` / `demoRelease`:** `DEMO_MODE = true`. Uses isolated `Demo*Repository` and DataStore persistence (`DemoDataSource`) for offline UI/workflow walkthroughs. **Never connects to live financials.**
- **`liveDebug` / `liveRelease`:** `DEMO_MODE = false`. Uses `LiveApiDataSource` + Retrofit/OkHttp against `DRIVE_PARTNER_API_BASE_URL`. When unconfigured (`""`), returns explicit `BackendNotDeployed` errors (`UnconfiguredApiTransport`).

```bash
# Compile & unit test Demo + Live variants (requires JDK 17 + Android SDK 35)
./gradlew assembleDemoDebug assembleLiveDebug testDemoDebugUnitTest testLiveDebugUnitTest

# Verify Release R8 shrinking/obfuscation
./gradlew assembleLiveRelease

# Build signed Play Store App Bundle
./gradlew bundleLiveRelease \
  -PDRIVE_PARTNER_API_BASE_URL="https://api.yourdomain.com/" \
  -PDRIVE_PARTNER_REALTIME_WS_URL="wss://api.yourdomain.com/ws/v1/driver" \
  -PDRIVE_PARTNER_MAPS_API_KEY="$MAPS_KEY" \
  -PDRIVE_PARTNER_KEYSTORE_PATH="/secure/path/release.keystore" \
  -PDRIVE_PARTNER_KEYSTORE_PASSWORD="$STORE_PW" \
  -PDRIVE_PARTNER_KEY_ALIAS="drivepartner" \
  -PDRIVE_PARTNER_KEY_PASSWORD="$KEY_PW"
```

### 3.2 Push Notifications Boundary (`DrivePartnerPushBoundary`)
Phone Auth and private KYC document uploads run on **Supabase Auth & Supabase Storage** without requiring Firebase `google-services.json`. Push wakeup hints pass through `DrivePartnerPushBoundary.handleRemoteMessage(data)` (with WebSocket `/ws/v1/driver` serving as primary live transport when online). If optional FCM wakeup is added later, register a `FirebaseMessagingService` calling `DrivePartnerPushBoundary.handleRemoteMessage(data)`.

---

## 4. Production Launch Readiness Checklist (`D-001`..`D-014`)
Before removing the **NO-GO** release gate:
- [ ] Approve brand name, package ID, Play Store listing assets & privacy policy (`D-001`).
- [ ] Configure PostgreSQL + PostGIS managed database, run `001_initial_schema.sql`, configure backups & restore drill.
- [ ] Configure launch city polygons (`service_zones`) & enabled categories (`D-002`).
- [ ] Approve fare configs, tax/fee rules, and payout provider adapter + limits before setting `LIVE_PAYOUTS_ENABLED=true` (`D-003`).
- [ ] Configure insurance policy disclosures (`D-004`), Firebase project (`D-005`), KYC provider (`D-006`), upstream passenger order webhook (`D-007`), and masked telephony + SOS responder webhook (`D-014`).
