# Technical Specification — Drive Partner

**Document ID:** DP-TECH-001 | **Version:** 1.0

## 1. Technology baseline

### Android
- Kotlin, Jetpack Compose, Material 3, MVVM.
- Navigation Compose, Coroutines and Flow.
- Hilt (or one consistently selected DI framework).
- Retrofit + OkHttp.
- Kotlin Serialization or Moshi — choose one and standardize.
- Room only for intentional local cache/queue use.
- DataStore for non-sensitive preferences.
- Firebase Phone Authentication and Firebase Cloud Messaging.
- Google Maps SDK and Fused Location Provider.
- WorkManager for deferrable work, not time-critical order dispatch.

Use mutually compatible current stable versions at implementation time. Pin them in `libs.versions.toml`. Record Kotlin, AGP, Gradle, Compose BOM, min/target SDK and compile SDK. Android 7+ is an aspiration from the original brief, not a guarantee: verify all selected dependencies and Play requirements before committing to `minSdk`.

### Backend
- TypeScript on a maintained Node.js LTS runtime.
- A single structured framework (recommended NestJS) for a modular monolith.
- Versioned REST API + authenticated WebSocket/realtime channel.
- PostgreSQL + PostGIS.
- Redis optional after measured need; never financial source of truth.
- Private object storage for documents.
- Firebase Admin SDK for token verification and FCM.
- Container deployment on a managed runtime (for example Cloud Run or equivalent).

### Required accounts/services
Firebase environment projects, Google Maps API/billing, managed database and backups, object storage, secret manager, monitoring, and a payout provider sandbox before settlement implementation.

## 2. Suggested repository structure
```text
/android
  app/ core/ feature/ gradle/ build.gradle.kts settings.gradle.kts
/backend
  src/modules/ src/common/ src/config/ src/migrations/ src/tests/
  package.json Dockerfile
/contracts
  openapi.yaml events.md
/docs
  [these 15 documentation files]
/.github/workflows
```
Inspect the repository before creating this layout. Preserve existing working project structure if it is already sound.

## 3. API standards
- Base path: `/api/v1`.
- JSON payloads and UTC ISO-8601 timestamps.
- Protected routes require `Authorization: Bearer <Firebase ID token>`.
- Use `X-Request-Id` for tracing and `Idempotency-Key` for retryable mutations.
- Stable machine-readable error codes; do not expose stack traces, SQL, secrets or internal reviewer notes.

Standard error example:
```json
{
  "error": {
    "code": "OFFER_EXPIRED",
    "message": "This offer is no longer available.",
    "requestId": "server-generated-id",
    "details": {}
  }
}
```

## 4. Minimum endpoint catalogue
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/me` | Current authenticated user/driver |
| POST | `/api/v1/drivers/profile` | Create/update permitted profile fields |
| GET | `/api/v1/drivers/me` | Own driver and activation state |
| POST | `/api/v1/vehicles` | Add/update vehicle |
| GET | `/api/v1/documents` | Own document statuses |
| POST | `/api/v1/documents/upload-intent` | Short-lived private upload authorization |
| POST | `/api/v1/documents` | Register uploaded metadata |
| POST | `/api/v1/availability/online` | Request online state |
| POST | `/api/v1/availability/offline` | Request offline state |
| POST | `/api/v1/locations` | Duty/trip location update |
| GET | `/api/v1/home` | Dashboard summary/map state |
| GET | `/api/v1/offers/active` | Current actionable offer |
| POST | `/api/v1/offers/{offerId}/accept` | Atomic idempotent accept |
| POST | `/api/v1/offers/{offerId}/reject` | Reject offer |
| GET | `/api/v1/trips/active` | Restore active trip |
| POST | `/api/v1/trips/{tripId}/events` | Submit permitted lifecycle event |
| GET | `/api/v1/orders` | Paginated own history |
| GET | `/api/v1/orders/{orderId}` | Own order detail |
| GET | `/api/v1/earnings/summary` | Earnings by period |
| GET | `/api/v1/earnings/transactions` | Earning/ledger view |
| GET | `/api/v1/wallet` | Ledger-derived balance view |
| POST | `/api/v1/payouts` | Idempotent payout request |
| GET | `/api/v1/payouts` | Own payout history |
| GET | `/api/v1/campaigns` | Applicable campaigns |
| GET | `/api/v1/referrals/me` | Own referral data |
| POST | `/api/v1/support/tickets` | Create ticket |
| GET | `/api/v1/support/tickets` | Own tickets |
| POST | `/api/v1/safety/incidents` | SOS/incident report |
| GET | `/api/v1/notifications` | In-app inbox |

Admin endpoints use separately authorized routes; never expose admin capabilities as driver APIs.

## 5. Realtime event contract
Every event includes unique `eventId`, `eventType`, `occurredAt` UTC, `resourceId`, `schemaVersion` and minimal routing metadata. Initial event types: `offer.created`, `offer.updated`, `offer.withdrawn`, `trip.updated`, `driver.verification.updated`, `payout.updated`, `notification.created`.

Realtime messages are hints. Client fetches authoritative resource state. Deduplicate event IDs and safely ignore/log unknown schema versions.

## 6. Domain/data rules
- Separate API DTOs, domain models and UI state.
- `amountMinor: Long` plus ISO currency such as `INR`; never use `Double` for money.
- Store distance in meters and duration in seconds; format for display.
- Store times in UTC; render in local locale/time zone.
- IDs are opaque UUIDs/strings; client makes no sequential-ID assumptions.

## 7. Android permissions/lifecycle
Permissions are minimal, contextual and explained. Exact location/foreground-service declarations must match the selected target SDK and current Play policy. Do not request background location merely because maps are used. When continuous on-duty tracking is operationally necessary, use the compliant foreground service and persistent notification after explicit driver action. Stop on confirmed offline state, subject to active-trip safety policy. Handle denied/permanently denied/approximate location, GPS disabled, process recreation and battery constraints.

## 8. Storage and security
- PostgreSQL for transactional records and money.
- Object storage for file bytes; database stores private object keys/metadata.
- No public KYC URLs.
- TLS, encryption at rest and field-level encryption where justified.
- Never log OTPs, tokens, full identity/bank numbers, documents or unnecessary precise coordinates.
- Backend secrets belong in secret manager, not APK/source control.
- Restrict Maps keys by application/package/signing certificate/API; restrict server keys appropriately.
- Verify payout webhooks and make them replay-safe.

## 9. Migration strategy
Migrations are version-controlled and executed in a controlled deploy step. Never edit a migration already applied to shared environments. Use expand-migrate-contract for breaking schema changes. Test migrations against previous staging schema. Backups require documented, periodically tested restore procedures.

## 10. Testing
**Android:** ViewModel, repository/API, Compose UI, permissions, process recreation/deep links.  
**Backend:** rules/pricing unit tests, PostgreSQL integration tests, competing accept concurrency, idempotency, auth/authorization, cross-driver isolation, webhook signature/replay.  
**E2E:** applicant-to-approved, online→offer→complete→ledger, duplicate accept, payout success/failure, app death during trip, network recovery.

## 11. Observability
Structured logs with request/resource IDs and safe actor references. Track API latency/errors, dispatch latency, stale offers, acceptance conflicts, location freshness, payout exceptions, reconciliation breaks, FCM outcomes and crash-free sessions. Never put PII in metric labels.

## 12. Build/release
Use debug/staging/release variants, external environment config, protected release signing, CI lint/build/test/security checks and release artifact verification. Do not commit keystore/password. Generate APK/AAB only after release build passes. Do not claim Play Store publication without actual submission/approval.
