# Technical Architecture Specification — Drive Partner

**Document ID:** DP-DOC-ARCH-001  
**Version:** 1.0  
**Status:** Approved Technical Baseline  
**Source References:** `02_Architecture.md` (DP-ARCH-001), `04_TECHSPEC.md` (DP-TECH-001), `00_MASTER.md` (DP-MASTER-001), `PRD.md` (DP-PRD-001)  
**Date:** 11 November 2026  

---

## 1. Executive Summary & Platform Boundary

Drive Partner is developed as a native Android application using **Kotlin**, **Jetpack Compose**, **Material 3**, and **MVVM architecture**.

### Non-Negotiable Platform Boundary
- **Android Client Scope:** The Android project strictly encompasses client-side presentation, local state orchestration, secure sensor/location capture, and secure network communication.
- **Backend Separation:** The backend is a separate, independently deployable TypeScript modular monolith backed by **PostgreSQL + PostGIS**. No Node.js/TypeScript backend runtime or database server will ever be embedded inside the Android client repository.
- **Backend Authority:** The backend database alone owns:
  1. Driver verification and eligibility gates.
  2. Availability acknowledgment and presence state.
  3. Dispatch offer generation, queueing, and atomic single-driver assignment.
  4. Authoritative trip state machines and valid transitions.
  5. Versioned pricing, quote snapshots, and final fare computation.
  6. Double-entry immutable financial ledger, wallet balances, and bank payout verification.
- **Firebase Role:** Firebase Auth (Phone OTP) and Firebase Cloud Messaging (FCM) act strictly as supporting infrastructure for identity token generation and wake/push hints. Neither Firebase nor Firestore shall ever be used as competing sources of truth for orders or monetary transactions.

---

## 2. Android Application Architecture

The Android application adheres to the official Android Modern Architecture Guidelines, structured around **Unidirectional Data Flow (UDF)** and a clean separation of concerns.

```
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer                              │
│  - Jetpack Compose Screens (Material 3)                     │
│  - Reusable Components (Cards, Buttons, TopBar, BottomNav)  │
│  - Reusable State Views (Loading, Empty, Error, Offline)    │
└───────────────────────────▲─────────────────────────────────┘
                            │ StateFlow<UiState>
                            │ User Intents / Actions
┌───────────────────────────┴─────────────────────────────────┐
│                    ViewModel Layer                          │
│  - MVVM ViewModels (viewModelScope)                         │
│  - Exposes immutable StateFlow to UI                        │
│  - Handles UI business logic & error mapping                │
└───────────────────────────▲─────────────────────────────────┘
                            │ Domain Calls (suspend / Flow)
┌───────────────────────────┴─────────────────────────────────┐
│                    Repository Layer                         │
│  - Abstracts Network API (Retrofit) & Local Cache (DataStore)│
│  - Maps Network DTOs to Clean Domain Models                 │
│  - Enforces currency integer paise rules                    │
└───────────────────────────▲─────────────────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
    ┌───────────────────┐       ┌───────────────────┐
    │  Remote Data API  │       │    Local Cache    │
    │  - Retrofit HTTP  │       │  - DataStore      │
    │  - WebSocket / SSE│       │  - EncryptedPrefs │
    └───────────────────┘       └───────────────────┘
```

### Layer Responsibilities
- **UI Layer (Compose):** Stateless, declarative Composables. Consumes state from `StateFlow` and emits user events. Performs zero business logic, zero fare calculations, and zero permission bypasses.
- **ViewModel Layer:** Owns UI state preparation, coroutine lifecycle management (`viewModelScope`), and domain orchestration. Holds state surviving configuration changes.
- **Repository Layer:** Single source of data for ViewModels. Coordinates API calls, caching, offline resilience, and network-to-domain model translation.
- **Navigation Architecture:** Single Activity (`MainActivity.kt`) hosting `NavHost` via Navigation Compose. Deep links and push notifications navigate through standard route arguments.

### Target Package Topology
```text
com.drivepartner.app/
├── core/
│   ├── common/         # Result wrappers, Dispatcher providers, Extensions
│   ├── network/        # Retrofit client, AuthInterceptor, IdempotencyInterceptor
│   ├── location/       # FusedLocation client wrapper, Foreground Service
│   ├── maps/           # Google Maps SDK wrapper, Marker helpers
│   ├── security/       # Token storage, EncryptedSharedPreferences
│   └── designsystem/   # Material 3 Theme, Typography, Shapes, Colors
├── data/
│   ├── model/          # Domain models (PartnerProfile, PartnerStatus, ServiceType)
│   ├── dto/            # Retrofit request/response DTOs
│   ├── repository/     # Repository interfaces and implementations
│   └── local/          # DataStore preferences & session management
├── navigation/         # DriveDestination, DriveNavHost
└── ui/
    ├── components/     # TopBar, BottomNav, Card, Button, State Views
    ├── screens/        # Home, Orders, Earnings, Rewards, Profile
    └── viewmodel/      # MainViewModel, Screen-specific ViewModels
```

---

## 3. Backend Integration & API Communication Strategy

### Communication Standards
- **Protocol:** HTTPS REST for transactional request/response + authenticated WebSocket/SSE for real-time dispatch signals.
- **Base Endpoint:** `/api/v1`
- **Authentication:** Every protected endpoint requires `Authorization: Bearer <Firebase_ID_Token>`.
- **Tracing & Reliability:**
  - `X-Request-Id`: Unique UUID generated per request for end-to-end audit tracing.
  - `Idempotency-Key`: UUID generated for all mutating actions (offer accept, trip event transitions, payout requests) to guarantee safe retries across flaky mobile networks.
- **Data Serialization:**
  - Dates and timestamps: UTC ISO-8601 (`YYYY-MM-DDTHH:mm:ss.sssZ`).
  - Monetary values: Integer paise (`amountMinor: Long`) alongside currency code `INR`. Never use floating-point types (`Float`/`Double`) for financial data.
  - Distances in integer meters; durations in integer seconds.

### Standardized Error Envelope
All backend errors follow a consistent, machine-readable envelope:
```json
{
  "error": {
    "code": "OFFER_EXPIRED",
    "message": "This offer is no longer available.",
    "requestId": "req_8f1b2c4e",
    "details": {}
  }
}
```
Client translates HTTP/network errors into sealed `NetworkResult<T>` and user-friendly localized messages without leaking technical stack traces.

---

## 4. Repository & ViewModel Structure

### Domain Repositories
1. **`AuthRepository`:** Handles phone OTP verification, Firebase ID token acquisition, backend session exchange, and token refresh.
2. **`PartnerRepository`:** Manages driver profile, vehicle details, KYC document statuses, and reactive duty status (`ONLINE`/`OFFLINE`).
3. **`DispatchRepository`:** Handles real-time active offer retrieval, atomic offer acceptance, and offer rejection.
4. **`TripRepository`:** Restores active trip state upon app launch, coordinates turn-by-turn navigation intents, and submits verified lifecycle events (`ARRIVED`, `STARTED`, `COMPLETED`, `CANCELLED`).
5. **`EarningsRepository`:** Fetches periodic earnings summaries (Today, Week, Month) and ledger transaction breakdowns.
6. **`WalletRepository`:** Retrieves ledger-derived wallet balances (Available, Pending, Settled) and submits idempotent payout requests.
7. **`SafetyRepository`:** Dispatches emergency SOS alerts with GPS coordinates and submits incident reports.
8. **`SupportRepository`:** Handles help tickets, category selection, and support message threads.

### Coroutine & Threading Model
- **`Dispatchers.IO`:** Network requests, DataStore reads/writes, file transfers.
- **`Dispatchers.Default`:** JSON deserialization, heavy list filtering, spatial distance calculations.
- **`Dispatchers.Main`:** UI state updates and Composable collectors.

---

## 5. Realtime Event & Dispatch Strategy

```
               [Realtime Gateway (WebSocket / SSE)]
                               │
                       Event Notification
                     (Event ID, Type, Expiry)
                               │
                               ▼
[Driver Android App] ──(Deduplicate Event ID)──► [Authoritative API Fetch]
       ▲                                                 │
       │                                                 ▼
       └────────── Push Wake Hint (FCM) ────────── [REST /api/v1/offers/active]
```

### Event Contract Principles
1. **Events as Hints:** Real-time messages (`offer.created`, `offer.withdrawn`, `trip.updated`, `payout.updated`) are signals, not authoritative payloads. The Android client receives the event hint, checks for duplicate `eventId`, and fetches fresh authoritative data from the REST API.
2. **Dual-Channel Delivery:** Primary real-time channel is WebSocket/SSE; secondary fallback is Firebase Cloud Messaging (FCM) for background wake.
3. **Reconnection & State Resync:** On network restore or app foregrounding, the client issues parallel queries to:
   - `GET /api/v1/availability/status`: Reconfirms server duty status.
   - `GET /api/v1/offers/active`: Checks if a pending offer exists.
   - `GET /api/v1/trips/active`: Restores any mid-flight active trip.
4. **Atomic Concurrency:** If a driver taps "Accept", the request includes the `offerId` and `Idempotency-Key`. The backend executes a PostgreSQL row-lock transaction; if already claimed, the client receives `409 Conflict` (`OFFER_ALREADY_ASSIGNED`) and cleanly displays an expired offer state.

---

## 6. Location Services & Google Maps Architecture

### Location Handling
- **Provider:** Google Play Services `FusedLocationProviderClient`.
- **Contextual Permission Flow:** Prompts for Fine Location only when driver initiates duty toggle (`Go Online`). Clear rationale presented in compliance with Google Play location policies.
- **Foreground Service:** When `ONLINE`, a dedicated Android Foreground Service runs with a persistent, non-dismissible notification stating "Drive Partner is active and tracking availability".
- **Duty Boundaries:** When the driver toggles `OFFLINE`, the foreground service stops and location polling immediately terminates. Background location is never polled when offline.
- **Adaptive Telemetry:** Polling interval dynamically throttles:
  - *Active Navigation:* High accuracy, 5-second interval.
  - *Idle Online Duty:* Balanced power, 15-30 second interval.
  - *Offline:* 0 updates.

### Google Maps SDK Integration
- Android Google Maps SDK integrated with Restricted API Keys (locked to package `com.drivepartner.app` and SHA-1 release/debug fingerprint).
- Maps Compose library (`com.google.maps.android:maps-compose`) used for declarative map rendering, driver marker rotation, pickup/drop pins, and polyline route overlays.

---

## 7. Security Boundaries Checklist

- [x] **No Embedded Backend Secrets:** No database credentials, backend master keys, or payment gateway private keys exist in the Android codebase.
- [x] **API Key Protection:** Google Maps API key stored in `.env` / `local.properties` and injected via the Secrets Gradle Plugin; restricted by package name and SHA-1 hash.
- [x] **Untrusted Client Model:** Client assumes it has zero financial or authorization authority. All permissions, balances, and role checks occur on the server.
- [x] **Private KYC Storage:** Client uploads document files directly to private object storage using short-lived (15-minute) presigned URLs provided by `/api/v1/documents/upload-intent`. Documents are never stored at public URLs.
- [x] **PII Protection:** Driver and customer personal phone numbers are masked through virtual telephony proxies. Real passenger numbers are never exposed in the Android UI or network payloads.
- [x] **Zero PII Logging:** Strict ProGuard / R8 rules and logging interceptors strip authorization headers, OTPs, Aadhaar/PAN numbers, and precise GPS trails from Logcat output.
- [x] **Transport Security:** Strict HTTPS / TLS 1.3 for all REST endpoints and WSS for WebSocket channels. Cleartext traffic disabled in `AndroidManifest.xml`.

---

## 8. Technical Dependencies Matrix

To transition to full network and hardware capabilities in subsequent phases, the following dependencies are documented in the architecture roadmap:

| Capability | Library / Artifact | Version Reference | Target Phase |
|---|---|---|---|
| **Networking** | `com.squareup.retrofit2:retrofit` | `2.11.0` | Phase 2 (Network Layer) |
| **HTTP Engine** | `com.squareup.okhttp3:okhttp` & `logging-interceptor` | `4.12.0` | Phase 2 (Network Layer) |
| **JSON Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | Phase 2 (Network Layer) |
| **Preferences & Session** | `androidx.datastore:datastore-preferences` | `1.1.1` | Phase 2 (Session Store) |
| **Location Services** | `com.google.android.gms:play-services-location` | `21.3.0` | Phase 3 (Location & Duty) |
| **Maps Integration** | `com.google.android.gms:play-services-maps` | `19.0.0` | Phase 3 (Maps Display) |
| **Maps Compose** | `com.google.maps.android:maps-compose` | `6.2.1` | Phase 3 (Maps Display) |
| **Firebase Auth** | `com.google.firebase:firebase-auth-ktx` | via BoM | Phase 2 (Authentication) |
| **Firebase Messaging** | `com.google.firebase:firebase-messaging-ktx` | via BoM | Phase 4 (Dispatch & Push) |
| **Secrets Management** | `secrets-gradle-plugin` | `2.0.1` | Build Pipeline |

*Note: In accordance with project instructions, no new dependencies or API stubs are injected prematurely until their designated implementation turn.*

---

## 9. Baseline Architectural Decisions (ADR Summary)

- **ADR-001 (Native Compose):** Pure native Kotlin and Jetpack Compose; no cross-platform wrappers or web-view embeddings.
- **ADR-002 (Clean Backend Boundary):** Backend remains an independent modular monolith; Android codebase contains only client-side code.
- **ADR-003 (Transactional Authority):** PostgreSQL + PostGIS owns transactional order, trip, and financial ledger states.
- **ADR-004 (Supporting Cloud Services):** Firebase Auth and FCM operate as support infrastructure, not financial or order databases.
- **ADR-005 (Integer Paise Currency):** All financial logic uses integer paise (`amountMinor: Long`) and ISO currency codes.
- **ADR-006 (State Resync & Hint Delivery):** Realtime events are wake/action hints backed by authoritative REST state queries.
- **ADR-007 (Demo Mode Isolation):** Demo fixtures operate completely decoupled from live dispatch and payment systems.
