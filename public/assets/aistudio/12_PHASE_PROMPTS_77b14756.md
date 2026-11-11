# Copy/Paste Phase Prompts for AI Coding Agents — Drive Partner

**Document ID:** DP-PROMPTS-001 | **Version:** 1.0

Use one phase prompt at a time. Give the agent the repository and all 15 documentation files. The agent must inspect existing code before editing.

## Global instruction — prepend to every phase
```text
You are implementing the original Drive Partner Android driver application. Read 00_MASTER.md, PRD.md, 02_Architecture.md, 04_TECHSPEC.md, 05_SCHEMA (1).md, 06_RULES (1).md, 03_APPFLOW.md, 11_SCREENS.md, Design.md, Engineering-Practices.md, 09_Phases.md, 07_IMPLEMENTATIONPLAN.md, 08_TRACKER.md and 10_memory.md.

CRITICAL:
- Inspect repository tree, current branch and working tree before changing anything.
- Preserve existing working code; do not replace/delete blindly.
- Implement only the phase requested below.
- Backend owns identity, eligibility, availability, dispatch, trip state, fares, rewards, ledger and payouts.
- Do not copy competitor branding, logo, artwork, exact text or screenshots. Use original configurable branding.
- No fake live orders, locations, earnings, insurance or payout success. Demo data must be labelled and isolated.
- Never put secrets in source code. Do not invent credentials or claim integration is live without verification.
- Every visible button works or is disabled with a clear reason. Handle loading, empty, error, offline and permission-denied states.
- Build and test after meaningful changes; report exact commands and actual outputs.
- Update relevant docs, 08_TRACKER.md and 10_memory.md. Do not mark tasks Done without evidence.

First return a short repository audit and precise file-level plan. Then implement the requested phase. For missing decisions/credentials, create safe adapter/configuration boundaries and log blockers instead of guessing.
```

## Phase 0 — Repository audit
```text
PHASE 0: REPOSITORY DISCOVERY ONLY.
Inspect repository tree, branch/working tree, Android/backend projects, dependency versions, build scripts, environment config, migrations, tests and existing features. Run existing build/test commands where feasible. Do not make destructive changes or add features.
Deliver: (1) architecture/technology inventory; (2) exact build/test commands and results; (3) working functionality to preserve; (4) gaps against master/PRD; (5) missing credentials/provider/business decisions without printing secret values; (6) file-level Phase 1 plan. Update tracker/memory with evidence only.
```

## Phase 1 — Foundation
```text
PHASE 1: PROJECT FOUNDATION.
Create/repair native Kotlin + Jetpack Compose foundation and TypeScript backend skeleton according to architecture and tech spec.
Android: Gradle/dependency catalog, app entry, Material 3 theme from Design.md, navigation for Home/Orders/Earnings/Rewards/Profile, reusable loading/error/empty states, API abstraction, environment config, safe logs.
Backend: selected TypeScript framework, validated config, /api/v1/health, DB connection, first migration, common error envelope and test setup.
Set dev/staging/prod boundaries without committing credentials. Do not implement OTP, dispatch, wallet or payout yet. Placeholder screens must say Not Implemented, not pretend to work.
Acceptance: Android debug build and navigation pass; backend build/typecheck/tests pass; health endpoint and migration pass; no secrets committed. Update docs/tracker.
```

## Phase 2 — Authentication, profile, vehicle and KYC
```text
PHASE 2: IDENTITY AND DRIVER ONBOARDING.
Implement Firebase Phone Auth on Android and server verification of Firebase ID tokens. Map verified identities to internal user/driver records. Add profile and vehicle onboarding with server validation and draft/resume. Implement private document upload through short-lived authorization, metadata records and reviewer statuses.
Support NOT_SUBMITTED/UPLOADED/UNDER_REVIEW/APPROVED/REJECTED/EXPIRED; rejection reason, replacement and expiry. Do not collect/store raw Aadhaar by default; use compliant provider/reference only if configured. Add protected reviewer actions and audit events. Avoid building a broad admin UI unless separately scoped.
No fake Live OTP or public document URLs. If credentials are missing, create adapter and isolated local test mode. Acceptance: staging applicant completes auth/profile/vehicle/document flow; unapproved account is blocked online; token and cross-user access tests pass.
```

## Phase 3 — Eligibility, Home, service area and duty
```text
PHASE 3: HOME DASHBOARD, ELIGIBILITY, LOCATION AND ONLINE/OFFLINE.
Implement Home per 11_SCREENS.md and Design.md. Add server eligibility checks for account, mandatory docs, vehicle/category/zone. Implement server-confirmed online/offline, contextual permission education, GPS readiness and a compliant duty-location mechanism for selected target SDK/Play policy. Use Fused Location Provider and stop duty tracking after confirmed offline when no active trip requires it. Add zone preferences and Auto Boost as a backend preference only; never auto-accept.
Map may show own location/configured zones. Hide demand/surge layers unless backed by live backend data. Add stale-location, reconnect, permission denied and GPS-off states. Acceptance: online requires eligibility/location; backend state authoritative; offline and permission handling verified. Do not implement live dispatch yet.
```

## Phase 4 — Dispatch and offers
```text
PHASE 4: REALTIME ORDER OFFERS AND SAFE ACCEPT/REJECT.
Implement order/offer models, dispatch boundary, expiry, transactional outbox and authenticated realtime delivery. Add FCM fallback with minimal payload; app fetches authoritative offer details from API. Offer card shows category, pickup/drop, distance/duration, estimated base, eligible incentive, total estimate and server-based countdown. Require explicit Accept or Reject.
Accept must be an atomic conditional transaction checking status, expiry, driver eligibility/online state and order unassigned. Add idempotency and duplicate-event protection. If another driver wins, show unavailable. Never queue accept offline or auto-accept.
Demo fixtures are only for isolated dev/test and show persistent DEMO label. Acceptance: competing drivers produce exactly one assignment; duplicate accept, expired offer and reconnect tests pass.
```

## Phase 5 — Trip lifecycle and order history
```text
PHASE 5: ACTIVE TRIP, NAVIGATION AND ORDER HISTORY.
Implement trip lifecycle per 06_RULES (1).md and 03_APPFLOW.md: assigned, en-route, arrived, in progress, completed, cancelled/disputed if allowed. Every transition is backend-authoritative and stored as event. Add configured map navigation handoff, pickup/drop, contact adapter only if contracted, cancellation reason and completion validation. Restore active trip after process death. Build paginated All/Completed/Cancelled order history and detail.
Do not calculate final earnings on client. Completion records one unique earning source reference for Phase 6; do not fabricate wallet credit. Acceptance: one completion only, illegal transitions rejected, retries idempotent, app restart recovers active trip, cross-driver data isolation passes.
```

## Phase 6 — Pricing, earnings, ledger, wallet and payout
```text
PHASE 6: FINANCIAL SYSTEM.
Implement versioned backend fare config/final calculation. Use integer paise and currency; persist itemized snapshot. Create earning statements linked to completed work. Implement append-only balanced ledger with unique source/idempotency keys; corrections are reversals/adjustments, never edits. Wallet displays ledger-derived available/pending/settled and transaction details.
Implement payout request with verified/tokenized account reference, limits, idempotency, provider adapter, signed webhook validation, replay protection and reconciliation. Start payout as Pending/Processing. Only verified provider/reconciliation evidence may set Paid. Never put provider secrets in Android. Use sandbox or test adapter only in staging/demo. Acceptance: balance/reconciliation, duplicate completion/payout, webhook replay, failure/reversal and cross-driver tests pass. Live payouts remain disabled until approvals.
```

## Phase 7 — Campaigns, referrals, safety, support and notifications
```text
PHASE 7: DRIVER RETENTION AND OPERATIONS.
Implement server-configured campaigns, qualification/progress, terms/version and idempotent reward posting. Add referral code/link, attribution and anti-self-referral/anti-fraud; reward only after server qualification. Build Safety Center with emergency contacts, incident report and SOS state. Never show SOS as contacted until acknowledged. Show insurance only with confirmed policy data and approved wording. Build support ticket creation, secure attachments, driver-visible messages/status, and keep internal notes private. Add notification inbox/preferences/deep links with minimal FCM payload and deduplication.
Acceptance: reward source posts once; referrals cannot self-credit; tickets isolated; SOS failure truthful; notifications recover after restart.
```

## Phase 8 — Hardening and release readiness
```text
PHASE 8: PRODUCTION READINESS AUDIT; DO NOT ADD MAJOR FEATURES.
Audit against release gates in 00_MASTER.md. Run Android lint/build/tests, backend lint/typecheck/unit/integration, DB migration and restore rehearsal, authorization, offer concurrency, trip/ledger/payout idempotency, location lifecycle, accessibility and device/network tests.
Review current target SDK and Play rules for foreground service/location. Review privacy policy, retention/deletion, KYC, Maps key restriction, secrets, monitoring, alerts, incident response, support escalation, rollback and release signing. Verify dev/staging/prod separation. Do not claim publication/live integrations unless verified.
Deliver PASS/FAIL/BLOCKED report with evidence, risk owners and go/no-go recommendation. Update tracker and docs.
```

## Bug-fix prompt template
```text
Fix only the issue below in the existing Drive Partner repository. Reproduce it first. Inspect the relevant path and identify root cause. Do not refactor unrelated files or change unrelated UI/features. Make the smallest safe fix, add a regression test, run relevant build/tests and report exact changed files and actual results. Preserve master/rules behavior.
Issue: [exact screen, expected result, actual result, device, logs]
```

## UI-only prompt template
```text
Improve only this screen's visual implementation. Preserve behavior, APIs, navigation and business rules. Follow Design.md and 11_SCREENS.md. Do not copy competitor assets/screenshots. Use original components/tokens. Verify small/large widths, font scaling, loading/empty/error and accessibility. Do not replace live data with hardcoded sample values. Report changed files and test/screenshot evidence.
Screen/request: [details]
```
