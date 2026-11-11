# Drive Partner — Complete Dependency-Aware Implementation Roadmap

**Document ID:** DP-ROADMAP-001 | **Date:** 2026-11-11 | **Branch:** `arena/1ec7d8a6-jaipur-ride` (`9b48d6e`)  
**Status:** Planning Complete (No features implemented in this step; Phase 0 audit logged in `docs/08_TRACKER.md` and `docs/10_memory.md`).

---

## 1. Repository Baseline Summary (Verified Evidence)

Before executing any phase, here is the verified state of `repidojaipur-bit/jaipur-ride-`:

1. **Native Android Scaffold (`app/`):**
   - Exists as a single-module Gradle project (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`, AGP `8.7.2`, Kotlin `2.0.21`, Compose BOM `2024.11.00`, Material 3 `1.3.1`, Navigation Compose `2.8.4`).
   - Contains Material 3 theme (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`), 5-tab navigation shell (`Home`, `Orders`, `Earnings`, `Rewards`, `Profile`), EN/HI `strings.xml`, reusable UI state views (`DriveLoadingView`, `DriveEmptyView`, `DriveErrorView`, `DriveOfflineView`), and an in-memory `PartnerRepositoryImpl`.
   - **Verified Defects & Blockers:**
     - `gradle/wrapper/gradle-wrapper.jar` is missing, `gradlew` is non-executable (`Permission denied`), and the sandbox lacks JDK 17, `gradle`, and Android SDK (`compileSdk 35`).
     - `MainViewModel.kt` invokes `combine(...)` with **6 flows**; Kotlin Coroutines `combine` typed overloads cap at 5 flows and will fail Kotlin compilation.
     - `PartnerRepositoryImpl.kt`, `HomeScreen.kt`, `ProfileScreen.kt`, and `EarningsScreen.kt` contain hardcoded sample/verified data (`Rajesh Kumar`, `DP-7842`, `RJ-14-EA-2024`, `isVerified = true`, `"Driving License, RC, Insurance Verified"`, `"Bank account linked & ready..."`) and no-op button callbacks.
     - Zero unit tests (`src/test`) or instrumentation tests (`src/androidTest`) exist.
2. **React/Vite Emulator Showcase (`src/App.tsx`, `package.json`, `vite.config.ts`):**
   - Root `package.json` (`"react-example"`) is a React 19 + Vite 8 web mockup previewing Android screens; `node_modules` is absent (`npm run lint` failed: `tsc: not found`).
   - Must stay strictly isolated from both the production Android app (`app/`) and the separate TypeScript backend repository.
3. **Separate Backend Project (External to this Android Repo):**
   - Does not exist in this repository and **must NOT be created inside the Android project**.
   - Integration contracts, required environment variables, and missing backend credentials/dependencies are documented in `docs/BACKEND_INTEGRATION_SPEC.md`.
4. **Android Architectural Boundaries Established (2026-11-11):**
   - Added typed DTOs (`data/remote/dto/*`), API interfaces (`data/remote/api/*`), `LiveBackendApiClient`, `DemoDataSource` vs `LiveApiDataSource`, `UnconfiguredApiTransport`, `FinancialRepository`/`LiveFinancialRepository`, `demo`/`live` Gradle productFlavors, and `docs/BACKEND_INTEGRATION_SPEC.md`.
5. **Documentation Gap (`D-008`):**
   - 5 planning/tracking docs are present in `docs/` (`07_IMPLEMENTATIONPLAN.md`, `08_TRACKER.md`, `09_Phases.md`, `10_memory.md`, `12_PHASE_PROMPTS.md`).
   - 10 referenced specification documents (`00_MASTER.md`, `PRD.md`, `02_Architecture.md`, `03_APPFLOW.md`, `04_TECHSPEC.md`, `05_SCHEMA (1).md`, `06_RULES (1).md`, `11_SCREENS.md`, `Design.md`, `Engineering-Practices.md`) are not present in the repository.

---

## 2. Cross-Cutting Architectural Rules

1. **Preserved Phase Structure:** Follows Phases `0` through `8` from `07_IMPLEMENTATIONPLAN.md`, `09_Phases.md`, and `12_PHASE_PROMPTS.md` strictly in dependency order. Never start all phases together; never skip prerequisites.
2. **Android vs. Backend Separation:**
   - **This Repository (`jaipur-ride-`) is the Native Android Driver Partner Application ONLY (`app/`).** Do **NOT** create TypeScript backend source code inside this Android project.
   - **Separate Backend Project (external repository/service):** Standalone TypeScript modular monolith backed by PostgreSQL + PostGIS, documented in `docs/BACKEND_INTEGRATION_SPEC.md`. Owns identity verification, driver eligibility, online/offline state, dispatch, trip state transitions, fare config/calculation, ledger, payouts, campaigns, referrals, and support ownership. **Firestore must never be used as an alternative source of truth for PostgreSQL orders or financial ledger.**
   - **Android (`app/`):** Communicates exclusively via agreed `/api/v1/...` REST & realtime contracts (`data/remote/api/*`, `data/remote/dto/*`, `LiveApiDataSource`), never computes authoritative fares/balances, never stores backend secrets, and never pretends unconfigured backend APIs are deployed (`ApiResult.BackendNotDeployed`).
   - Within each vertical-slice phase (`1`–`7`), freeze DTO/API contracts first (`docs/BACKEND_INTEGRATION_SPEC.md`), implement & test the separate Backend endpoints/migrations first, and build/wire Android against those agreed contracts.
3. **Demo vs. Live Separation (`ADR-007`):**
   - `BuildConfig.DEMO_MODE_ENABLED` (`demo` vs `live` Gradle productFlavors in `app/build.gradle.kts`) and backend `DEMO_FIXTURES_ENABLED` isolate all synthetic/demo data.
   - Android uses `DemoDataSource` strictly in `demo` mode (with persistent `DEMO` banner and isolated demo models) and `LiveApiDataSource` in `live` mode—never mixing demo earnings or orders with live financial records.
   - Production/live mode must never display fake driver data, fake KYC approval, fake bank links, fake demand heatmaps, or unacknowledged SOS success.
4. **Evidence-Based Tracking & Memory:**
   - Update `docs/08_TRACKER.md` only with actual build/test commands, commit refs, and results. Never mark `[x]` without verification evidence.
   - Log unresolved product/legal/credential/spec decisions in `docs/10_memory.md`.

---

## 3. Phase-by-Phase Roadmap

### Phase 0 — Repository Audit, Environment Verification & Baseline Documentation
- **Goal:** Establish truthful baseline evidence of the existing codebase, tooling, gaps, and blockers before writing application or backend code.
- **Features:**
  - *Discovery / Governance:* Complete repository inventory across `app/`, `src/`, `gradle/`, and git branches; run existing check commands and record exact failures; preserve baseline docs in `docs/`; identify missing specifications (`D-008`), build environment gaps (`D-009`), compile defects (`D-010`), fake-state violations (`D-011`), and missing product/credential approvals (`D-001`..`D-007`, `D-012`..`D-013`).
  - *Demo vs. Live Separation:* Document the boundary separating `src/App.tsx` preview, existing hardcoded sample data in `PartnerRepositoryImpl.kt`, and future production code.
- **Files to create:**
  - *Docs:* `docs/07_IMPLEMENTATIONPLAN.md`, `docs/08_TRACKER.md`, `docs/09_Phases.md`, `docs/10_memory.md`, `docs/12_PHASE_PROMPTS.md`, `docs/IMPLEMENTATION_ROADMAP.md`
- **Existing files to modify:**
  - None (Phase 0 discovery is strictly non-destructive; repairs occur in Phase 1).
- **Technical dependencies:**
  - Clean git working branch (`arena/1ec7d8a6-jaipur-ride`).
  - Provisioning path for JDK 17 + Android SDK 35 + Gradle `8.11.1` + Node 22 + PostgreSQL 16/PostGIS (either CI or local container) and retrieval/ADR confirmation of the missing 10 spec docs (`D-008`, `D-013`).
- **Backend dependencies:**
  - None (verified zero backend exists yet).
- **Acceptance criteria:**
  - Full architectural/technology inventory recorded with file-level accuracy.
  - Exact build/lint command outputs captured without fabricating pass results.
  - Existing working UI shell vs. broken/mocked pieces cataloged.
  - All open decisions, missing specs, and missing credentials recorded in `docs/10_memory.md` and `docs/08_TRACKER.md`.
  - Phase 1 file-level execution plan ready.
- **Unit tests:**
  - None added in Phase 0; baseline check commands executed (`./gradlew --version`, `npm run lint`) with actual failures logged.
- **Integration tests:**
  - None added in Phase 0.
- **Risks:**
  - Coding Phase 1 backend without `04_TECHSPEC.md` / `05_SCHEMA (1).md` / `06_RULES (1).md` without first locking framework/schema ADRs.
  - Treating `src/App.tsx` React preview as the Android app or backend.
- **Completion conditions:**
  - `docs/08_TRACKER.md` and `docs/10_memory.md` saved with factual audit evidence; missing-doc/toolchain blockers (`D-008`, `D-009`, `D-013`) acknowledged or resolved before starting Phase 1 implementation.

---

### Phase 1 — Project Foundation (Android + Backend Skeleton, Config, CI & DB Baseline)
- **Goal:** Establish a buildable native Android Kotlin/Compose app shell and an independent TypeScript + PostgreSQL/PostGIS backend skeleton with `dev`/`staging`/`prod` separation and CI.
- **Features:**
  - *Android Implementation:*
    - Repair Gradle wrapper (`gradle-wrapper.jar`, executable `gradlew`, `gradlew.bat`) and fix `MainViewModel.kt` 6-flow `combine` compile error.
    - Introduce DI container (Hilt or `AppContainer` per ADR), `BuildConfig` environments (`dev`, `staging`, `prod`), and Retrofit + OkHttp + `kotlinx.serialization` client with `ApiErrorEnvelope` parser and PII-redacting logger.
    - Implement `DemoModeBanner` and enforce `DEMO_MODE_ENABLED` boundary: strip hardcoded `"Verified"`, `"Bank account linked"`, and `"Rajesh Kumar"` defaults from live repository/screens; show explicit `"Not Implemented"` or disabled states for Phase 2–7 actions; wire real localization (`AppCompatDelegate.setApplicationLocales`).
  - *Backend Implementation (Separate TypeScript Backend Repository — NOT inside Android project):*
    - Scaffold standalone separate TypeScript backend repository (modular monolith layout: `config`, `common`, `db`, `modules/health`) per `docs/BACKEND_INTEGRATION_SPEC.md`.
    - Strict startup env validation (`zod`/`envalid`), request ID middleware, structured PII-redacting logger, unified error envelope (`{ error: { code, message, requestId } }`).
    - PostgreSQL + PostGIS connection pool, migration runner, `0001_baseline_extensions.sql`, and `GET /api/v1/health` (verifying DB & PostGIS readiness).
  - *Demo vs. Live Separation:*
    - `dev`/`demo` vs `staging`/`prod` flavors & env schemas; `DEMO_MODE_ENABLED=false` enforced in `prod` build and backend env validation.
- **Files to create:**
  - *Android:* `gradle/wrapper/gradle-wrapper.jar`, `gradlew.bat`, `app/src/main/java/com/drivepartner/app/core/config/AppEnvironment.kt`, `core/di/AppModule.kt`, `core/network/{ApiClient.kt,ApiErrorEnvelope.kt,SafeLoggingInterceptor.kt,HealthApiService.kt}`, `ui/components/{DemoModeBanner.kt,NotImplementedNotice.kt}`, `app/src/test/.../{MainViewModelTest.kt,ApiErrorEnvelopeTest.kt,SafeLoggingInterceptorTest.kt}`, `app/src/androidTest/.../DriveNavHostTest.kt`
  - *Backend:* `backend/package.json`, `backend/tsconfig.json`, `backend/.env.example`, `backend/src/{server.ts,app.ts}`, `backend/src/config/env.ts`, `backend/src/common/{errors/AppError.ts,errors/errorHandler.ts,logging/logger.ts,http/requestId.ts}`, `backend/src/db/{pool.ts,migrate.ts}`, `backend/migrations/0001_baseline_extensions.sql`, `backend/src/modules/health/{health.routes.ts,health.service.ts}`, `backend/test/{unit/env.test.ts,unit/errorHandler.test.ts,integration/health.test.ts}`
  - *Infra/CI:* `docker-compose.yml`, `.github/workflows/{android-ci.yml,backend-ci.yml}`
- **Existing files to modify:**
  - `gradlew` (executable bit & wrapper invocation)
  - `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
  - `app/src/main/java/com/drivepartner/app/{DrivePartnerApp.kt,MainActivity.kt}`
  - `app/src/main/java/com/drivepartner/app/ui/viewmodel/MainViewModel.kt`
  - `app/src/main/java/com/drivepartner/app/data/repository/PartnerRepositoryImpl.kt`
  - `app/src/main/java/com/drivepartner/app/ui/screens/{home/HomeScreen.kt,profile/ProfileScreen.kt,earnings/EarningsScreen.kt}`
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-hi/strings.xml`, `.gitignore`
- **Technical dependencies:**
  - Phase 0 blockers `D-009` (JDK 17, Android SDK 35, Gradle, Postgres+PostGIS CI/local) and `D-013` (TypeScript backend framework + migration tool ADR, ideally referencing `04_TECHSPEC.md` and `Design.md`).
- **Backend dependencies:**
  - PostgreSQL 16 + PostGIS instance, `0001_baseline_extensions.sql` applied, `GET /api/v1/health` operational.
- **Acceptance criteria:**
  - Android debug/staging APKs build cleanly (`assembleDevDebug`, `assembleStagingDebug`), launch, and navigate across all 5 tabs with EN/HI switching.
  - Placeholder buttons/tabs display `Not Implemented` or disabled explanations; zero fake verified/bank-linked state in live mode.
  - Backend compiles (`tsc --noEmit`), runs migrations cleanly, and returns `200` on `GET /api/v1/health` with Postgres/PostGIS verified.
  - `dev`/`staging`/`prod` separated; zero secrets committed.
- **Unit tests:**
  - *Android:* `MainViewModelTest`, `ApiErrorEnvelopeTest`, `SafeLoggingInterceptorTest`.
  - *Backend:* Env validation schema tests, error envelope serializer tests, logger redaction tests.
- **Integration tests:**
  - *Backend:* Health endpoint + DB connectivity (`200` healthy / `503` degraded) and migration idempotency test against Postgres/PostGIS.
  - *Android:* `DriveNavHostTest` verifying all 5 destinations and `DriveUiState` error/empty/loading/offline views.
- **Risks:**
  - Mixing root React preview (`package.json`) with `backend/` dependencies.
  - Leaving sample data visible in live flavor builds.
  - Framework/design drift if `04_TECHSPEC.md` / `Design.md` are still absent (mitigate with explicit ADRs in `docs/10_memory.md`).
- **Completion conditions:**
  - Green CI runs on Android build/lint/unit tests and Backend typecheck/build/migration/integration tests; actual outputs recorded in `docs/08_TRACKER.md`.

---

### Phase 2 — Identity, Authentication, Profile, Vehicle & KYC Onboarding
- **Goal:** Register and authenticate drivers securely via Firebase Phone Auth + server token verification, persist profile/vehicle onboarding drafts, and handle private KYC document uploads + reviewer approval/rejection gating.
- **Features:**
  - *Android Implementation:*
    - Phone OTP auth screens with resend cooldown throttling, Firebase Phone Auth SDK integration, and isolated local test auth adapter for `dev` when credentials are absent.
    - Encrypted session storage (`SessionStore`), `AuthInterceptor` attaching ID/session tokens, `401` expiry recovery.
    - Profile & Vehicle onboarding forms with local draft persistence + backend draft/resume sync; category selection (`BIKE`, `AUTO`, `CAB`, `DELIVERY`).
    - Private document capture/picker -> short-lived pre-signed upload intent -> direct private upload -> confirm -> live status view (`NOT_SUBMITTED`, `UPLOADED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `EXPIRED`) with rejection reason and re-upload flow.
    - Explicit prohibition of raw Aadhaar collection unless compliant provider is approved (`D-006`).
  - *Backend Implementation:*
    - `AuthProvider` verifying Firebase ID tokens (`FirebaseAdminVerifier`) + `DevMockAuthVerifier` strictly disabled in `staging`/`prod`.
    - `POST /api/v1/auth/session` mapping Firebase UID/phone to internal `users` + `drivers` records (`APPLICANT` by default).
    - `GET/PUT /api/v1/driver/profile` and `GET/PUT /api/v1/driver/vehicle` with server-side validation and draft persistence.
    - `POST /api/v1/driver/documents/upload-intent` (private short-lived signed upload URL), `POST /api/v1/driver/documents/:id/confirm`, `GET /api/v1/driver/documents`.
    - Protected reviewer API (`POST /api/v1/admin/drivers/:driverId/documents/:docId/review`) with RBAC guard, mandatory rejection reason on reject, immutable `kyc_audit_events`, and expiry enforcement.
  - *Demo vs. Live Separation:*
    - Test auth adapter and local signed storage adapter allowed in `dev` only; `staging` uses private cloud storage bucket & Firebase staging test numbers.
- **Files to create:**
  - *Android:* `core/auth/{AuthProvider.kt,FirebasePhoneAuthProvider.kt,DevMockAuthProvider.kt,AuthInterceptor.kt,SessionStore.kt}`, `data/model/{KycDocument.kt,OnboardingDraft.kt,DriverAccountStatus.kt}`, `data/remote/{AuthApi.kt,OnboardingApi.kt,DocumentUploadClient.kt}`, `data/repository/{AuthRepository.kt,OnboardingRepository.kt}`, `ui/screens/auth/{PhoneAuthScreen.kt,OtpVerifyScreen.kt,AuthViewModel.kt}`, `ui/screens/onboarding/{ProfileSetupScreen.kt,VehicleSetupScreen.kt,DocumentUploadScreen.kt,OnboardingViewModel.kt}`, unit & UI tests
  - *Backend:* `backend/migrations/0002_identity_drivers_vehicles_kyc.sql`, `backend/src/modules/auth/{auth.adapter.ts,firebase.verifier.ts,auth.middleware.ts,auth.routes.ts}`, `backend/src/modules/drivers/{driver.routes.ts,driver.service.ts,driver.schema.ts}`, `backend/src/modules/kyc/{storage.adapter.ts,kyc.routes.ts,kyc.service.ts,kyc.review.routes.ts}`, unit & integration tests
- **Existing files to modify:**
  - `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/drivepartner/app/navigation/{DriveDestination.kt,DriveNavHost.kt}`
  - `app/src/main/java/com/drivepartner/app/ui/screens/profile/ProfileScreen.kt`
  - `backend/src/app.ts`
- **Technical dependencies:**
  - Phase 1 completed.
  - Firebase Auth project config (`D-005`), private object storage bucket/adapter, KYC data retention/provider decision (`D-006`).
- **Backend dependencies:**
  - `0002_identity_drivers_vehicles_kyc.sql` (`users`, `drivers`, `vehicles`, `driver_documents`, `kyc_audit_events`, `reviewer_roles`), auth/RBAC middleware, signed URL generator.
- **Acceptance criteria:**
  - Staging applicant completes Phone OTP -> Profile -> Vehicle -> Private Document Upload -> Reviewer Review -> Resubmission/Approval.
  - Invalid/expired tokens rejected (`401`); cross-driver profile/document access rejected (`403`/`404`); non-reviewer calls to review endpoint rejected (`403`).
  - Document URLs are private and short-lived; raw Aadhaar inputs rejected by default.
  - Unapproved/rejected/expired accounts cannot transition to approved driver eligibility.
- **Unit tests:**
  - *Android:* OTP resend throttle, form validation, `DocumentStatus` mapping, token header injection.
  - *Backend:* Token verifier adapter, KYC state transition matrix, rejection-reason enforcement, raw-Aadhaar rejection.
- **Integration tests:**
  - *Backend:* Auth + profile/vehicle draft + upload intent + cross-driver `403` + reviewer reject/reupload/approve + audit log + expiry blocking.
  - *Android:* Onboarding & KYC rejection UI flow test.
- **Risks:**
  - Missing Firebase/S3 credentials in staging (`D-005`).
  - Publicly exposed object URLs or PII leaks in logs/EXIF metadata.
  - Non-compliant Aadhaar storage (`D-006`).
- **Completion conditions:**
  - Auth, authorization/IDOR, KYC status transitions, and staging/dev applicant verification evidence logged in `docs/08_TRACKER.md`.

---

### Phase 3 — Eligibility, Home Dashboard, Service Area, Duty Status & Compliant Location
- **Goal:** Gate online duty strictly behind backend eligibility, deliver Home map/zones UI, enforce contextual location permissions & Play-compliant foreground tracking while online, and stop tracking offline.
- **Features:**
  - *Android Implementation:*
    - Home Dashboard displaying server eligibility status, blocking reasons if ineligible, and backend-confirmed duty switch (`OFFLINE` -> `PENDING` -> `ONLINE` / error fallback).
    - Map card showing driver's own location and backend-configured service zone polygons; hide demand/surge overlays unless live backend data exists.
    - Contextual location & notification permission education, permanent-denial settings redirect, GPS-off resolution dialog, and stale-location alert.
    - `DutyLocationService` (`ForegroundService` with `foregroundServiceType="location"`) using Fused Location Provider while `ONLINE` (or active trip); terminates cleanly upon confirmed `OFFLINE`.
    - Network reconnect resync (`GET /api/v1/driver/duty/state`), service-area preferences UI, and `Auto Boost` backend preference toggle (never auto-accept).
  - *Backend Implementation:*
    - `GET /api/v1/driver/eligibility` verifying account status, mandatory approved non-expired KYC docs, approved vehicle/category, and launch city/zone (`D-002`).
    - `GET /api/v1/driver/duty/state` & `POST /api/v1/driver/duty/status` (`ONLINE`/`OFFLINE` authoritative validation).
    - `GET/PUT /api/v1/driver/preferences` (preferred zones, `auto_boost_enabled` preference flag, active approved category).
    - `GET /api/v1/zones` (PostGIS zone boundaries) & `POST /api/v1/driver/location` (validating coords, accuracy, freshness in `driver_locations`).
  - *Demo vs. Live Separation:*
    - Unapproved accounts and Demo Mode never register in live PostGIS availability pools.
- **Files to create:**
  - *Android:* `data/model/{EligibilityStatus.kt,DutyState.kt,ServiceZone.kt,DriverPreferences.kt}`, `data/remote/{DutyApi.kt,LocationApi.kt,ZonesApi.kt}`, `data/repository/{DutyRepository.kt,LocationRepository.kt}`, `location/{DutyLocationService.kt,LocationPermissionManager.kt,GpsSettingsResolver.kt}`, `ui/screens/home/{HomeViewModel.kt,HomeMapCard.kt,LocationEducationDialog.kt,ZonePreferencesScreen.kt}`, unit & FGS tests
  - *Backend:* `backend/migrations/0003_zones_eligibility_duty_locations.sql`, `backend/src/modules/eligibility/{eligibility.service.ts,eligibility.routes.ts}`, `backend/src/modules/duty/{duty.service.ts,duty.routes.ts}`, `backend/src/modules/locations/{location.service.ts,location.routes.ts}`, `backend/src/modules/zones/zones.routes.ts`, unit & integration tests
- **Existing files to modify:**
  - `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` (FGS location permissions/service, restricted Maps key placeholder)
  - `app/src/main/java/com/drivepartner/app/data/model/PartnerStatus.kt`
  - `app/src/main/java/com/drivepartner/app/ui/viewmodel/MainViewModel.kt`
  - `app/src/main/java/com/drivepartner/app/ui/screens/home/HomeScreen.kt`
  - `app/src/main/java/com/drivepartner/app/ui/components/DriveTopBar.kt`
  - `backend/src/app.ts`
- **Technical dependencies:**
  - Phase 2 completed (verified driver identity, KYC & vehicle records).
  - Android 14/15 (`targetSdk 35`) FGS location rules, Maps SDK key (`D-005`) or safe fallback, launch city/zone seed (`D-002`).
- **Backend dependencies:**
  - `0003_zones_eligibility_duty_locations.sql` (`service_zones`, `driver_duty_sessions`, `driver_preferences`, `driver_locations` PostGIS GIST index).
- **Acceptance criteria:**
  - Unapproved/expired-doc driver blocked from going `ONLINE` by backend (`403`/`409`) and Android surfaces blocking reason.
  - Online/Offline switch changes state only after backend confirmation; permission or GPS denial prevents `ONLINE`.
  - Foreground location service starts only on confirmed `ONLINE` and stops completely after confirmed `OFFLINE` (with no active trip).
  - Reconnect restores authoritative backend duty state; zero fake demand overlays rendered.
- **Unit tests:**
  - *Android:* Duty switch pending/commit/rollback tests, permission/GPS state branching, Auto Boost preference flag test.
  - *Backend:* Eligibility rule evaluator, stale coordinate rejection, zone preference validation.
- **Integration tests:**
  - *Backend:* Eligibility denial -> doc approval -> go online -> PostGIS location update -> doc expiry forces/blocks online.
  - *Android:* Foreground service lifecycle start/stop & permission-denied UI flow test on Android 14/15 emulator.
- **Risks:**
  - Google Play FGS location rejection if service runs while offline or without clear user initiation.
  - Maps key unrestricted/leaked; OEM Doze killing background tracking.
- **Completion conditions:**
  - Backend eligibility/duty/location tests + Android FGS start/stop emulator verification logged in `docs/08_TRACKER.md`.

---

### Phase 4 — Dispatch, Realtime Offers & Atomic Accept/Reject
- **Goal:** Deliver genuine backend dispatch offers via realtime + FCM fallback, render server-synced countdown offer cards requiring explicit Accept/Reject, and guarantee atomic single-driver assignment under concurrency.
- **Features:**
  - *Android Implementation:*
    - Authenticated realtime channel (`RealtimeSocketClient`) active while `ONLINE` + automatic reconnect resync (`GET /api/v1/offers/pending`).
    - FCM `DriveMessagingService` receiving minimal wakeup payload (`offerId` only) -> fetches authoritative details from API (`GET /api/v1/offers/:offerId`).
    - `OfferSheet` displaying category, pickup/drop, distance/duration, base estimate, eligible incentive, total estimate (**explicitly labelled estimate**), and server-synced countdown timer.
    - Explicit **Accept** (sending unique `Idempotency-Key`, never queued offline, never auto-accepted) and **Reject** actions; clean handling of `409 Conflict` ("already taken") and `410 Gone` ("expired"), plus event deduplication.
    - Isolated `DemoDispatchFixtures` usable only in `dev`/Demo mode with persistent `DEMO` badge.
  - *Backend Implementation:*
    - `DispatchSourceAdapter` boundary for upstream order intake (`D-007`) + non-prod test offer injector.
    - Transactional `outbox_events`, authenticated realtime gateway, and minimal-payload `FcmNotifierAdapter`.
    - `GET /api/v1/offers/pending`, `GET /api/v1/offers/:offerId` (includes `server_now` & `expires_at`), `POST /api/v1/offers/:offerId/reject`.
    - `POST /api/v1/offers/:offerId/accept`: single DB transaction with `SELECT ... FOR UPDATE`, checking offer `PENDING`, `now() < expires_at`, driver `ONLINE` & eligible & free of active trip, order `SEARCHING` & unassigned; atomically assigns order, supersedes competing offers, initializes `trips` row, and respects `Idempotency-Key`.
    - Background expiry worker transitioning timed-out offers to `EXPIRED`.
  - *Demo vs. Live Separation:*
    - Demo offers never hit live `/offers/:id/accept` or `orders` tables; live claims blocked unless real `DispatchSourceAdapter` is connected (`D-007`).
- **Files to create:**
  - *Android:* `data/model/{WorkOffer.kt,OfferStatus.kt}`, `data/remote/{OffersApi.kt,RealtimeSocketClient.kt}`, `fcm/DriveMessagingService.kt`, `data/repository/{DispatchRepository.kt,DemoDispatchFixtures.kt}`, `ui/screens/offers/{OfferSheet.kt,OfferViewModel.kt,ServerCountdownTimer.kt}`, unit & UI tests
  - *Backend:* `backend/migrations/0004_orders_offers_dispatch_outbox.sql`, `backend/src/modules/dispatch/{dispatch.adapter.ts,dispatch.service.ts,dispatch.routes.ts,offerExpiry.worker.ts}`, `backend/src/modules/realtime/{realtime.gateway.ts,outbox.publisher.ts}`, `backend/src/modules/notifications/fcm.adapter.ts`, unit & concurrency integration tests
- **Existing files to modify:**
  - `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/drivepartner/app/{MainActivity.kt,navigation/DriveNavHost.kt}`
  - `app/src/main/java/com/drivepartner/app/ui/screens/orders/OrdersScreen.kt`
  - `backend/src/{app.ts,server.ts}`
- **Technical dependencies:**
  - Phase 3 completed (eligibility, `ONLINE` state, PostGIS location).
  - Server-time synchronization, `Idempotency-Key` infrastructure, FCM server/client credentials (`D-005`), upstream order-source contract (`D-007`).
- **Backend dependencies:**
  - `0004_orders_offers_dispatch_outbox.sql` (`orders`, `offers`, `outbox_events`, `idempotency_keys`, partial unique active-assignment indexes).
- **Acceptance criteria:**
  - Competing concurrent accepts across N drivers assign **exactly 1 winner** and reject N-1 cleanly.
  - Duplicate accept with same `Idempotency-Key` is idempotent; expired offer accept is rejected (`410`).
  - Duplicate WebSocket + FCM notifications render one offer card; reconnect restores active pending offers.
  - Demo fixtures are strictly isolated and persistently labelled `DEMO`.
- **Unit tests:**
  - *Android:* Event deduplication, server-offset countdown, offline accept guard, demo isolation check.
  - *Backend:* Accept precondition checks, idempotency replay logic, minimal FCM payload assertion (no PII).
- **Integration tests:**
  - *Backend:* Parallel Postgres concurrency stress test (`Promise.all` competing drivers), WebSocket + API resync fallback test, expiry sweeper test.
  - *Android:* `OfferSheet` Compose UI test (countdown expiration, `409` conflict dismissal, `DEMO` badge visibility).
- **Risks:**
  - Double-booking race condition under concurrent accept calls.
  - Device clock drift breaking offer countdowns.
  - Staging simulator confused with live passenger demand before `D-007` is integrated.
- **Completion conditions:**
  - Concurrency test (`1` winner, `N-1` conflicts), idempotency, expiry, and reconnect test logs recorded in `docs/08_TRACKER.md`.

---

### Phase 5 — Trip Lifecycle, Navigation Handoff & Order History
- **Goal:** Manage one accepted trip cleanly through backend-authoritative states (`ASSIGNED` -> `COMPLETED`/`CANCELLED`), survive Android process death, emit exactly one earning source reference upon completion, and expose paginated order history.
- **Features:**
  - *Android Implementation:*
    - `ActiveTripScreen` driven by backend trip state (`ASSIGNED`, `EN_ROUTE_TO_PICKUP`, `ARRIVED_AT_PICKUP`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `DISPUTED`).
    - Pickup/drop details, configured external Maps navigation `Intent` with safe fallback, conditional masked contact handoff (active only if telephony adapter is configured), cancellation reason sheet, and completion action (sending `Idempotency-Key`).
    - Cold-start / process-death active trip recovery (`GET /api/v1/trips/active`).
    - `OrdersScreen` paginated `All` / `Completed` / `Cancelled` tabs + `OrderHistoryDetailScreen` (no client-calculated final earnings).
  - *Backend Implementation:*
    - `GET /api/v1/trips/active`, `POST /api/v1/trips/:tripId/events` (state transition + optimistic version + `Idempotency-Key`), appending immutable `trip_events`.
    - Authoritative state-transition guard (`06_RULES (1).md`), driver ownership enforcement, and cancellation reason validation.
    - On `COMPLETED`: atomic insert of **1 unique `earning_source_refs`** record (keyed uniquely by `trip_id`) for Phase 6 — no fabricated wallet credit!
    - `TelephonyAdapter` boundary (`501`/capability flag until contracted) and cursor-paginated `GET /api/v1/orders` & `GET /api/v1/orders/:orderId`.
  - *Demo vs. Live Separation:*
    - Demo trips never persist `trip_events` or `earning_source_refs` to live tables.
- **Files to create:**
  - *Android:* `data/model/{Trip.kt,TripStatus.kt,OrderHistoryItem.kt,CancellationReason.kt}`, `data/remote/{TripsApi.kt,OrdersHistoryApi.kt}`, `data/repository/{TripRepository.kt,OrderHistoryRepository.kt}`, `navigation/ExternalMapNavigator.kt`, `ui/screens/trip/{ActiveTripScreen.kt,ActiveTripViewModel.kt,CancelTripSheet.kt}`, `ui/screens/orders/{OrderHistoryDetailScreen.kt,OrdersViewModel.kt}`, unit & process-death tests
  - *Backend:* `backend/migrations/0005_trips_events_and_earning_refs.sql`, `backend/src/modules/trips/{tripStateMachine.ts,trips.service.ts,trips.routes.ts}`, `backend/src/modules/telephony/telephony.adapter.ts`, `backend/src/modules/orders/{ordersHistory.routes.ts,ordersHistory.service.ts}`, unit & integration tests
- **Existing files to modify:**
  - `app/src/main/java/com/drivepartner/app/{MainActivity.kt,navigation/DriveNavHost.kt,ui/viewmodel/MainViewModel.kt}`
  - `app/src/main/java/com/drivepartner/app/location/DutyLocationService.kt` (stay active during active trip)
  - `app/src/main/java/com/drivepartner/app/ui/screens/orders/OrdersScreen.kt`
  - `backend/src/modules/dispatch/dispatch.service.ts`, `backend/src/app.ts`
- **Technical dependencies:**
  - Phase 4 completed (atomic order acceptance initializing `trips`).
  - `06_RULES (1).md` trip/cancellation/OTP rules (or documented ADR defaults).
- **Backend dependencies:**
  - `0005_trips_events_and_earning_refs.sql` (`trips`, `trip_events`, `earning_source_refs` unique source constraint, `cancellation_reasons`).
- **Acceptance criteria:**
  - Trip completes exactly once; illegal state transitions rejected (`409`/`422`); cross-driver access rejected (`403`/`404`).
  - Concurrent/duplicate `complete` requests create at most 1 `earning_source_refs` record.
  - Process death + app relaunch restores the active trip automatically.
  - Order history displays paginated `All`/`Completed`/`Cancelled` trips isolated to the driver.
- **Unit tests:**
  - *Android:* Transition button states, external map URI/fallback builder, startup trip recovery logic, history filter state.
  - *Backend:* Full trip state transition matrix, optimistic locking/idempotency tests, cancellation validation.
- **Integration tests:**
  - *Backend:* Lifecycle end-to-end + parallel double-complete test (`earning_source_refs` count == 1) + cross-driver isolation + pagination test.
  - *Android:* Process kill/recreate recovery test + Orders history Compose test.
- **Risks:**
  - Process death at pickup/drop dropping trip UI; toggling Offline prematurely stopping location updates mid-trip; duplicate completion requests.
- **Completion conditions:**
  - State machine, idempotency/double-complete, cross-driver isolation, pagination, and restart recovery evidence logged in `docs/08_TRACKER.md`.

---

### Phase 6 — Pricing, Earnings, Append-Only Ledger, Wallet & Payouts
- **Goal:** Implement versioned integer-paise fare calculations, immutable balanced ledger accounting, ledger-derived wallet summaries, and tokenized/sandboxed payout workflows with signed webhook reconciliation.
- **Features:**
  - *Android Implementation:*
    - `EarningsScreen` powered by `EarningsViewModel`: backend-derived wallet summary (`available`, `pending`, `settled` in `Long` paise formatted safely via `MoneyPaise`), earning statements list, and itemized breakdown.
    - Immutable ledger transaction history view (credits, reversals/adjustments, payout holds, payouts).
    - Tokenized payout account linking & payout withdrawal request screen (`Idempotency-Key`, backend min/max limits, truthful `PENDING`/`PROCESSING`/`PAID`/`FAILED`/`REVERSED` states). Client never calculates final fares, edits ledger rows, or stores payout provider secrets.
  - *Backend Implementation:*
    - Versioned `fare_configs` & `quote_snapshots`; server-side `fareCalculator` in integer `BIGINT` paise (`INR`) generating itemized `earning_statements` 1-to-1 from Phase 5 `earning_source_refs`.
    - Append-only balanced ledger (`ledger_accounts`, `ledger_entries`) with DB trigger blocking `UPDATE`/`DELETE` (corrections via compensating `REVERSAL`/`ADJUSTMENT` entries only) and unique source/idempotency keys.
    - `GET /api/v1/wallet/summary` & `GET /api/v1/wallet/ledger` computed strictly from ledger postings.
    - `PayoutProviderAdapter` (`SandboxPayoutAdapter` in staging/dev; `LIVE_PAYOUTS_ENABLED=false` gate until `D-003` + provider/legal/finance sign-off): `POST /api/v1/wallet/payout-accounts`, `POST /api/v1/wallet/payouts`, signed & replay-protected `POST /api/v1/webhooks/payouts/:provider`, automatic reversal on `FAILED`, and periodic reconciliation worker.
  - *Demo vs. Live Separation:*
    - Live payouts hard-disabled until Finance/Legal/Provider approvals; staging uses sandbox adapter only; Demo earnings isolated from ledger DB.
- **Files to create:**
  - *Android:* `core/money/MoneyPaise.kt`, `data/model/{WalletSummary.kt,EarningStatement.kt,LedgerEntry.kt,PayoutAccount.kt,PayoutRequest.kt}`, `data/remote/{EarningsApi.kt,WalletApi.kt}`, `data/repository/{EarningsRepository.kt,WalletRepository.kt}`, `ui/screens/earnings/{EarningsViewModel.kt,EarningStatementDetailScreen.kt,LedgerHistoryScreen.kt,PayoutRequestScreen.kt,PayoutAccountScreen.kt}`, unit & UI tests
  - *Backend:* `backend/migrations/0006_fares_statements_ledger_payouts.sql`, `backend/src/modules/pricing/{fareCalculator.ts,fareConfig.service.ts}`, `backend/src/modules/earnings/{earnings.service.ts,earnings.routes.ts}`, `backend/src/modules/ledger/ledger.service.ts`, `backend/src/modules/payouts/{payout.adapter.ts,sandboxPayout.adapter.ts,payout.service.ts,payout.routes.ts,payoutWebhook.routes.ts,reconciliation.worker.ts}`, unit & financial pipeline tests
- **Existing files to modify:**
  - `app/src/main/java/com/drivepartner/app/navigation/DriveNavHost.kt`
  - `app/src/main/java/com/drivepartner/app/ui/screens/earnings/EarningsScreen.kt` (replace static bank-linked card/placeholder with real wallet & gated payout UI)
  - `backend/src/modules/trips/trips.service.ts` (publish/invoke statement generation on `TRIP_COMPLETED`)
  - `backend/src/app.ts` (raw body preservation for webhook HMAC signature verification)
- **Technical dependencies:**
  - Phase 5 completed (`earning_source_refs` on trip completion).
  - Open decisions `D-003` (pricing/commission/tax/payout limits) and `D-005` (payout sandbox keys).
- **Backend dependencies:**
  - `0006_fares_statements_ledger_payouts.sql` (including DB immutability trigger on `ledger_entries`), `LedgerService`, raw-body webhook middleware.
- **Acceptance criteria:**
  - All monetary math uses integer minor units (`paise`); ledger entries balance and DB rejects `UPDATE`/`DELETE`.
  - Duplicate trip completion produces 1 statement and 1 ledger credit; concurrent/duplicate payout requests cannot create negative balance or double debit.
  - Forged/replayed payout webhooks rejected; failed payout triggers compensating ledger reversal; reconciliation catches mismatches.
  - Cross-driver access rejected (`403`/`404`); live payouts remain disabled until approvals.
- **Unit tests:**
  - *Android:* `MoneyPaiseTest`, payout eligibility/limit validation UI logic.
  - *Backend:* Fare config versioning & paise math, balanced entry check, HMAC webhook signature & replay window checks.
- **Integration tests:**
  - *Backend:* End-to-end trip->statement->ledger->payout->webhook success/failure reversal->reconciliation suite, concurrent withdrawal race test, DB immutability test.
  - *Android:* Earnings, ledger reversal, and payout status UI tests.
- **Risks:**
  - Race condition double-spending wallet balance; floating-point arithmetic bugs; webhook spoofing/replay; enabling real money movement prematurely.
- **Completion conditions:**
  - Ledger balance/immutability, duplicate completion/payout, webhook replay, reversal, and reconciliation test evidence logged in `docs/08_TRACKER.md`.

---

### Phase 7 — Campaigns/Rewards, Referrals, Safety/SOS, Support & Notifications
- **Goal:** Deliver server-controlled retention campaigns & anti-fraud referrals, truthful SOS/safety/insurance UI, driver-isolated support tickets, and deduplicated notifications.
- **Features:**
  - *Android Implementation:*
    - `RewardsScreen`: server campaigns, progress, versioned terms modal, referral code/link sharing.
    - Safety Center & SOS: emergency contacts, incident report, SOS flow showing honest **Sending / Unacknowledged / Failed** vs **Acknowledged** states; Insurance section rendered **only** when backend supplies confirmed policy data & approved wording (`D-004`).
    - Support Tickets: create ticket with private attachment upload intents, view driver-visible messages & status updates.
    - Notification Inbox & Preferences: wired to `DriveTopBar` bell + badge, mark read, category preferences, deep-link routing preserved across restart.
  - *Backend Implementation:*
    - Server-configured `campaigns`, qualification tracking, idempotent reward credit via Phase 6 `LedgerService` (`CAMPAIGN_REWARD`).
    - `referrals` with anti-self-referral/device/phone/payout deduplication and post-qualification credit.
    - `SafetyService` + `SosResponderAdapter` (truthful acknowledgement tracking, `sos_alerts` audit), `insurance_policies` config endpoint.
    - `SupportService` filtering out `is_internal_note=true` from driver DTOs and enforcing strict ticket ownership.
    - `NotificationsService` with `dedupe_key` inbox persistence, preferences, and minimal-payload FCM push.
  - *Demo vs. Live Separation:*
    - Non-prod SOS runs strictly against test responder (`TEST` flagged); no hardcoded reward/insurance guarantees in UI.
- **Files to create:**
  - *Android:* `data/model/{Campaign.kt,ReferralInfo.kt,SosAlert.kt,SupportTicket.kt,AppNotification.kt}`, `data/remote/{RewardsApi.kt,SafetyApi.kt,SupportApi.kt,NotificationsApi.kt}`, `data/repository/{RewardsRepository.kt,SafetyRepository.kt,SupportRepository.kt,NotificationRepository.kt}`, `ui/screens/rewards/{RewardsViewModel.kt,CampaignDetailScreen.kt,ReferralScreen.kt}`, `ui/screens/safety/{SafetyCenterScreen.kt,SosButtonSheet.kt,IncidentReportScreen.kt,SafetyViewModel.kt}`, `ui/screens/support/{SupportTicketsScreen.kt,TicketDetailScreen.kt,SupportViewModel.kt}`, `ui/screens/notifications/{NotificationInboxScreen.kt,NotificationPreferencesScreen.kt,NotificationsViewModel.kt}`, unit & UI tests
  - *Backend:* `backend/migrations/0007_campaigns_referrals_safety_support_notifications.sql`, `backend/src/modules/{rewards,referrals,safety,support,notifications}/*`, unit & integration tests
- **Existing files to modify:**
  - `app/src/main/java/com/drivepartner/app/{MainActivity.kt,navigation/DriveNavHost.kt,fcm/DriveMessagingService.kt}`
  - `app/src/main/java/com/drivepartner/app/ui/screens/{rewards/RewardsScreen.kt,profile/ProfileScreen.kt}` (wire Support & SOS navigation)
  - `backend/src/modules/trips/trips.service.ts`, `backend/src/app.ts`
- **Technical dependencies:**
  - Phase 6 (`LedgerService` for reward postings), Phase 2 (private attachments & identity), Phase 4 (FCM infrastructure), `D-004` (insurance), SOS responder/support ops specs (`10_memory.md`).
- **Backend dependencies:**
  - `0007_campaigns_referrals_safety_support_notifications.sql`, `LedgerService`, private storage adapter.
- **Acceptance criteria:**
  - Campaign reward credits at most once; self-referrals blocked; SOS failure/unacknowledged states reported truthfully.
  - Insurance copy hidden unless backed by confirmed policy record; support tickets isolated and strip internal notes.
  - Notification inbox deduplicates and survives restart with working deep links.
- **Unit tests:**
  - *Android:* SOS failure/ack state tests, insurance null-policy UI hiding, deep-link parser.
  - *Backend:* Anti-self-referral rules, campaign cap checks, support DTO internal-note stripping, notification dedupe.
- **Integration tests:**
  - *Backend:* Campaign reward idempotency (`1` credit), self-referral rejection, SOS unacknowledged audit test, cross-driver ticket `403`, inbox restart persistence.
  - *Android:* Notification deep-link & SOS/Support Compose UI tests.
- **Risks:**
  - Misleading SOS confirmation if responder integration fails; referral abuse; internal support note leakage.
- **Completion conditions:**
  - All Phase 7 unit + integration evidence logged in `docs/08_TRACKER.md`.

---

### Phase 8 — Hardening, Security/Policy Audit & Release Readiness
- **Goal:** Validate production reliability, security, Play Store compliance, accessibility, observability, backup/restore, and operational sign-off without adding major new features.
- **Features:**
  - *Android Hardening:* R8 minification & ProGuard rules verification, signed `prodRelease` AAB/APK (keystores outside Git), Android 13–15 FGS/location/Play Policy & Data Safety audit, network/battery/device-matrix testing, 200% font scaling & TalkBack accessibility audit, verification that `DEMO_MODE_ENABLED == false` in `prod`.
  - *Backend & Ops Hardening:* Automated IDOR/authz sweep across all endpoints, dispatch & ledger load/concurrency tests, rate-limiting verification, Postgres backup/restore & migration rollback rehearsal, monitoring/alerting dashboards, incident/rollback runbooks, legal/privacy retention review (`D-001`..`D-007`).
- **Files to create:**
  - `docs/RELEASE_READINESS_REPORT.md`, `docs/RUNBOOKS.md`, `docs/PLAY_POLICY_AND_DATA_SAFETY.md`
  - `backend/test/load/dispatchLoad.test.ts`, `backend/test/security/authzSweep.test.ts`
  - `scripts/db-backup-restore-rehearsal.sh`
- **Existing files to modify:**
  - `app/build.gradle.kts`, `app/proguard-rules.pro`
  - `.github/workflows/{android-ci.yml,backend-ci.yml}`
  - `docs/08_TRACKER.md`, `docs/10_memory.md`
- **Technical dependencies:**
  - Phases 1–7 complete and passing; physical/emulator test matrix; staging mirroring prod; `00_MASTER.md` release gates & `D-001`..`D-007` business/legal/provider approvals for a `GO` verdict.
- **Backend dependencies:**
  - Full staging stack (`0001`–`0007`), alerting sinks, automated DB backup storage.
- **Acceptance criteria:**
  - Zero critical/high security/authz issues; signed R8 `prodRelease` AAB verified; backup/restore rehearsal passes; load/concurrency SLAs met; PASS/FAIL/BLOCKED matrix signed off for every `00_MASTER.md` / `08_TRACKER.md` quality gate.
- **Unit tests:**
  - Full Android release unit suite + full Backend unit suite.
- **Integration tests:**
  - Full Backend security authz sweep + load/concurrency + financial + retention integration suite; R8-minified Android UI smoke suite; DB backup/restore rehearsal.
- **Risks:**
  - R8 stripping Serialization/Retrofit models; Play Store rejection over FGS/location disclosure; shipping with unresolved `D-001`..`D-007` approvals (must block `GO` if open).
- **Completion conditions:**
  - Signed `RELEASE_READINESS_REPORT.md` with PASS/FAIL/BLOCKED evidence, risk owners, and formal Go/No-Go sign-off.

---

## 4. Dependency-Aware Implementation Order

```text
Phase 0 (Audit & Baseline) [~ IN PROGRESS]
   │
   ▼  [Gate: toolchain provisioned + ADR/specs locked for backend framework & design]
Phase 1 (Foundation: Android + Backend Skeleton + CI + Health + DB Baseline)
   │
   ▼  [Gate: Android compiles & navigates; Backend health & 0001 migration pass]
Phase 2 (Identity, Auth, Profile, Vehicle & KYC Onboarding)
   │
   ├──────────────────────────────────────────────────────────┐
   ▼                                                          ▼ (optional UI prep once Phase 7A DTOs freeze)
Phase 3 (Eligibility, Home Map, Duty & Compliant Location)   [Safety/Support/Notifications Baseline UI/Contracts]
   │
   ▼  [Gate: Backend-confirmed Online/Offline & FGS lifecycle verified]
Phase 4 (Dispatch, Realtime Offers, FCM Fallback & Atomic Accept/Reject)
   │
   ▼  [Gate: 1-winner concurrency test & idempotency verified]
Phase 5 (Active Trip Lifecycle, Restart Recovery & Order History)
   │
   ├──► Order History Detail Polish (read-only after Phase 5 trips)
   ▼  [Gate: Unique earning_source_refs & process-death recovery verified]
Phase 6 (Pricing, Earning Statements, Append-Only Ledger, Wallet & Payouts)
   │
   ▼  [Gate: Ledger immutability, double-spend protection & webhook reconciliation verified]
Phase 7 (Campaigns, Referrals, Safety/SOS, Support & Notifications)
   │
   ▼  [Gate: Reward idempotency, anti-fraud, SOS truth & ticket isolation verified]
Phase 8 (Hardening, Security/Play Policy Audit & Release Readiness)
```

### Safe Sequencing Rules
1. **Strict Vertical-Slice Order Inside Every Phase (`1`–`7`):** Freeze DTO/OpenAPI contract -> Apply Backend SQL Migration & Authoritative API + Integration Tests -> Wire Android Client & UI Tests -> Verify Demo vs. Live Isolation -> Update `docs/08_TRACKER.md`.
2. **Never Skip Prerequisites:** Do not implement online/location (Phase 3) before KYC eligibility (Phase 2); do not implement dispatch (Phase 4) before confirmed duty/location (Phase 3); do not implement wallet/payouts (Phase 6) before unique trip `earning_source_refs` (Phase 5); do not post campaign/referral rewards (Phase 7) before the append-only ledger (Phase 6).
3. **Controlled Parallelism Only After Phase 1:** Android Compose UI scaffolding for the *current* phase may proceed alongside Backend implementation **only after** DTO contracts are frozen. Never edit DB migrations or core state machines concurrently across phases.

---

## 5. Final Sequence & Readiness Confirmation

1. **Phase 0** — Repository Audit & Baseline Verification *(Current: `[~]` In Progress — discovery completed; awaiting toolchain/CI & missing spec/ADR lock to close Phase 0 gate)*
2. **Phase 1** — Foundation (Android Repair/DI/Network/Environments + Independent TypeScript/PostgreSQL Backend + CI) *(**NEXT PHASE READY TO BEGIN** once Phase 0 blockers `D-009` & `D-013` are confirmed/acknowledged)*
3. **Phase 2** — Identity, Firebase Phone Auth, Profile, Vehicle & Private KYC Onboarding
4. **Phase 3** — Eligibility, Home Dashboard, Service Zones, Duty Status & Compliant Foreground Location
5. **Phase 4** — Dispatch, Realtime Offers, FCM Fallback & Atomic Accept/Reject
6. **Phase 5** — Trip Lifecycle, Navigation Handoff, Restart Recovery & Order History
7. **Phase 6** — Versioned Pricing, Earnings, Append-Only Ledger, Wallet & Sandboxed Payouts
8. **Phase 7** — Campaigns/Rewards, Referrals, Safety/SOS, Support & Notifications
9. **Phase 8** — Hardening, Security/Play Policy Audit & Release Readiness
