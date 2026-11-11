# Delivery Phases and Acceptance Gates — Drive Partner

**Document ID:** DP-PHASE-001 | **Version:** 1.0

Use the matching execution prompt in `12_PHASE_PROMPTS.md`. A phase is not complete from code generation alone; acceptance evidence is mandatory.

## Phase 0 — Repository audit
**Goal:** understand the actual repository before coding.  
**Inputs:** master, PRD and engineering practices.  
**Tasks:** inspect tree/branch/working tree, identify Android/backend code, run builds/tests, inventory integrations without exposing secrets, list what already works.  
**Acceptance:** baseline report, no destructive changes, exact blockers and Phase 1 file plan.  
**Do not:** rewrite the project or claim tests passed without running them.

## Phase 1 — Foundation
**Goal:** buildable native Android and backend foundations.  
**Includes:** Gradle/dependency catalog, Compose theme, navigation shell, feature structure, API client, backend skeleton, health route, DB migration baseline, environment config and CI.  
**Acceptance:** app launches and navigates; backend health responds; database migration passes; dev/staging are separated.  
**Excludes:** real dispatch and payouts.

## Phase 2 — Identity and onboarding
**Goal:** register a driver and submit verification evidence safely.  
**Includes:** Firebase OTP, server token verification, profile, vehicle, draft/resume, private upload, document statuses, reviewer actions/audit and correction flow.  
**Acceptance:** staging applicant completes the flow; document access is private; unapproved driver is blocked from online.  
**Excludes:** production launch before KYC process/policies are approved.

## Phase 3 — Eligibility, home, duty and location
**Goal:** let an approved driver go online safely.  
**Includes:** eligibility API, dashboard, map, service zones, permission education, backend online/offline, compliant duty location and reconnect sync.  
**Acceptance:** backend confirms availability; permission/GPS denial is handled; offline tracking stops; no fake demand overlay.  
**Excludes:** real dispatch until Phase 4.

## Phase 4 — Offers and dispatch
**Goal:** deliver and accept/reject genuine work offers.  
**Includes:** order/offer model, dispatch boundary, realtime, FCM fallback, offer card/countdown, accept/reject, atomic assignment, expiry, idempotency and isolated Demo fixtures.  
**Acceptance:** concurrency test proves one assignment; duplicate/stale offers are safe; reconnect restores state.  
**Excludes:** claims of live demand before actual order source and operations are connected.

## Phase 5 — Trip and order history
**Goal:** execute and record one job lifecycle.  
**Includes:** navigation handoff, pickup/drop details, state transitions, immutable events, cancellation, process restart recovery and history.  
**Acceptance:** trip reaches completed once; duplicate completion does not create duplicate earning source.  
**Excludes:** final settlement until Phase 6.

## Phase 6 — Earnings, wallet and payout
**Goal:** trustworthy financial records and controlled settlement.  
**Includes:** versioned fare config, quote snapshot, earning statement, ledger, wallet, payout adapter, signed webhook and reconciliation.  
**Acceptance:** ledger traces to source; idempotency/reconciliation passes; client cannot alter amounts; sandbox only until approvals.  
**Excludes:** live money movement without finance/provider/legal/operations sign-off.

## Phase 7 — Rewards, referral, safety and support
**Goal:** add retention and driver support capabilities.  
**Includes:** campaigns, progress, rewards, referral safeguards, SOS/incident, support tickets and notification inbox/preferences.  
**Acceptance:** reward posts once; support is isolated; SOS never claims unconfirmed dispatch.  
**Excludes:** unsupported insurance or earning guarantees.

## Phase 8 — Hardening and release
**Goal:** prove launch readiness.  
**Includes:** device matrix, current Android/Play policy, security review, load/concurrency, backup/restore, observability, privacy, accessibility, signing, store metadata and runbooks.  
**Acceptance:** all `00_MASTER.md` release gates signed off; staging-to-production configuration and rollback/incident path verified.  
**Excludes:** declaring production success before actual deployment and approval.

## Cross-phase rules
- Later phases cannot bypass security/data dependencies from earlier phases.
- Failed acceptance criteria must be fixed or formally deferred with risk owner.
- Update schema/API/design/rules docs in the same PR as behavior changes.
- Agent must inspect repository and ask no repeat questions already answered in project memory.
