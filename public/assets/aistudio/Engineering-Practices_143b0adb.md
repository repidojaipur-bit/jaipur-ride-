# Engineering Practices — Drive Partner

**Document ID:** DP-ENG-001 | **Version:** 1.0

## 1. General engineering rules
- Inspect before modifying; preserve existing working code and repository conventions.
- Prefer small, reversible, reviewable changes.
- Use one consistent architecture/state-management/DI pattern per layer.
- Name code after domain concepts, not screenshot coordinates or temporary labels.
- No dead buttons, fake functionality or silent catch blocks.
- Never commit secrets, credentials, real PII, document numbers, bank details or precise real driver coordinates.

## 2. Kotlin and Android
- Use idiomatic Kotlin, null safety and immutable models.
- Compose screens should be stateless where practical: receive `UiState`, emit user intents.
- ViewModels expose `StateFlow`; do not expose mutable state directly.
- Use structured concurrency and cancel work with lifecycle.
- Inject dispatchers/repositories for tests.
- Keep DTOs separate from domain/UI models.
- Restore active trip and important state from backend after process recreation.
- Use `rememberSaveable` for transient UI state only, not authoritative business state.
- Do not execute network/database/location work from composables.
- Add previews for reusable components where useful.

## 3. Backend
- Use modular monolith boundaries.
- Validate input at API edge and enforce domain invariants in services.
- Use typed domain errors and stable API error codes.
- Use transactions for assignment, financial posting and critical state transitions.
- Idempotency keys for retryable mutations.
- Authorize every resource access; never trust client role, fare, balance or qualification.
- Avoid generic admin endpoints that bypass rules.
- Use transactional outbox for events that must publish after database commit.

## 4. API and integrations
- Version OpenAPI and event contracts.
- Use timeouts and bounded retries with exponential backoff/jitter for safe calls.
- Never retry a non-idempotent mutation without an idempotency key.
- Handle rate limits/provider outages explicitly.
- Verify webhook signatures and prevent replay.
- Use sandbox providers in dev/staging; production credentials belong in secret manager.
- Normalize provider errors before returning them to Android.

## 5. Database
- All schema changes use versioned migration files.
- Never edit a migration already applied to a shared environment.
- Add indexes from real query patterns and inspect plans for high-volume paths.
- Use foreign keys/unique/check constraints for invariants.
- Store money as integer minor units and currency; use UTC timestamps.
- Ledger rows are append-only; corrections are reversals/adjustments.
- Use PostGIS indexes for spatial queries.
- Plan retention/partitioning for location/event tables before data grows.
- Backup creation is not enough; test restore periodically.

## 6. Security and privacy
- Enforce least privilege and role-based authorization on backend.
- Verify Firebase ID tokens server-side.
- Keep document storage private and issue short-lived access only after authorization.
- Encrypt sensitive values as appropriate; protect keys in managed secret storage.
- Mask phone/bank identifiers in UI and logs.
- Do not store raw Aadhaar by default; use compliant verification and minimum necessary references.
- Keep precise location and customer identity out of push payloads/analytics labels.
- Enforce request/upload size, MIME, rate and input validation.
- Audit KYC, account, payout and administrative decisions.
- Threat-model dispatch, payouts and SOS.

## 7. Testing strategy
Required layers: domain unit tests; repository/API tests; PostgreSQL integration tests; Compose UI tests; API/event contract tests; and end-to-end tests for onboarding, accept/complete and payout lifecycle.

Mandatory edge cases:
- Expired OTP/offer/token.
- Double tap and retry.
- Two drivers accept one order concurrently.
- Duplicate FCM/realtime event.
- App killed/restarted during active trip.
- Network loss before/after server mutation response.
- GPS disabled, permission denied or stale location.
- Document rejected/expired.
- Duplicate trip completion/reward/payout.
- Invalid/replayed payout webhook.
- Driver A attempting to access Driver B's records.

Tests must assert business invariants, not merely that screens render.

## 8. Observability and errors
- Structured logs with request ID and safe resource IDs.
- Never log request bodies indiscriminately.
- Crash reporting without PII.
- Monitor latency, error rates, outbox backlog, dispatch outcome, stale location, payout exceptions and ledger mismatch.
- Correlate API/event/provider calls.
- User-facing errors should be actionable without exposing internals.

## 9. Git and change management
- Work on feature branches and use focused commits.
- Never commit `.env`, keystore, service-account JSON, private keys or real user data.
- Add migrations and tests with behavior changes.
- Update documentation in the same PR when contracts/rules/UI change.
- Do not churn lockfiles/dependency versions without reason.
- Review AI-generated code; it is untrusted until built and tested.

## 10. AI coding-agent protocol
1. Read active phase prompt and relevant docs.
2. Inspect repository/working tree and current implementation.
3. Identify existing patterns and exact files involved.
4. State a concise implementation plan.
5. Implement only authorized phase scope.
6. Run formatter, lint, build and relevant tests.
7. Fix failures caused by the change.
8. Report exact commands/results, including failures.
9. Update `08_TRACKER.md` and `10_memory.md`.
10. List blockers and next phase.

Never say “complete”, “production-ready” or “deployed” without evidence. If a command was not run, say so.
