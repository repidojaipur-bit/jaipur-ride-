# Project Tracker — Drive Partner

**Document ID:** DP-TRACK-001 | **Version:** 1.0 | **Initial status:** Not Started

## 1. Status legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Done and verified
- `[!]` Blocked
- `[?]` Decision required

A task is not Done because code was generated. It is Done only when build/test evidence is recorded.

## 2. Phase tracker
| Phase | Workstream | Status | Evidence/build ref | Owner | Notes |
|---|---|---|---|---|---|
| 0 | Repository audit and baseline | [ ] | — | — | Inspect before edits |
| 1 | Foundation, theme, navigation, CI | [ ] | — | — | Android + backend skeleton |
| 2 | Auth, profile, vehicle, KYC | [ ] | — | — | Staging OTP/private upload |
| 3 | Eligibility, duty, location, map | [ ] | — | — | Backend-confirmed status |
| 4 | Dispatch, offers, accept/reject | [ ] | — | — | Atomic acceptance test |
| 5 | Trip lifecycle and order history | [ ] | — | — | Restart recovery |
| 6 | Earnings, wallet, ledger, payout | [ ] | — | — | Reconciliation evidence |
| 7 | Rewards, referrals, safety, support | [ ] | — | — | Eligibility and audit tests |
| 8 | Hardening and release | [ ] | — | — | Sign-off required |

## 3. Functional checklist

### Identity/onboarding
- [ ] OTP and resend throttling
- [ ] Backend token verification
- [ ] Profile draft/resume
- [ ] Vehicle category/details
- [ ] Private document upload/status
- [ ] Reviewer approve/reject and audit
- [ ] Expiry blocking logic

### Duty/map
- [ ] Eligibility check before online
- [ ] Pending/confirmed/error switch states
- [ ] GPS and permission states
- [ ] Compliant foreground location service
- [ ] Offline tracking stop
- [ ] Service-area preferences
- [ ] Location freshness/reconnect sync

### Dispatch/trip
- [ ] Offer event and API fetch
- [ ] Server-expiry countdown
- [ ] Explicit accept/reject
- [ ] Duplicate event deduplication
- [ ] Atomic acceptance concurrency test
- [ ] Expired offer rejection
- [ ] Backend trip-transition validation
- [ ] Active-trip restoration after process death
- [ ] Order history/detail

### Money
- [ ] Fare config versioning
- [ ] Final earning statement
- [ ] Append-only ledger
- [ ] Integer paise handling
- [ ] Wallet reconciliation
- [ ] Payout idempotency
- [ ] Webhook signature/replay protection
- [ ] Payout failure/reversal path

### Rewards/operations
- [ ] Campaign terms/eligibility
- [ ] Reward duplicate prevention
- [ ] Referral fraud checks
- [ ] Truthful SOS acknowledgement
- [ ] Support-ticket ownership isolation
- [ ] Notification inbox/preferences
- [ ] Admin roles and audit

## 4. Quality gates
| Gate | Required evidence | Status |
|---|---|---|
| Android build | Successful debug/staging build | [ ] |
| Backend build | Successful typecheck/build | [ ] |
| Database | Migrations applied; restore process reviewed | [ ] |
| Auth | Invalid/expired token rejected | [ ] |
| Authorization | Cross-driver access tests pass | [ ] |
| Dispatch | Competing acceptance test passes | [ ] |
| Trip | Duplicate completion does not duplicate earning | [ ] |
| Ledger | Entries balance and are immutable | [ ] |
| Payout | Duplicate request/webhook tests pass | [ ] |
| Location | Permission/offline behavior verified | [ ] |
| Privacy | Sensitive logs/push payload reviewed | [ ] |
| Accessibility | Screen reader/large text/contrast review | [ ] |
| Release | Legal/provider/operations sign-off | [ ] |

## 5. Blockers and decisions log
| ID | Date | Blocker/decision | Impact | Owner | Resolution |
|---|---|---|---|---|---|
| D-001 | 2026-11-11 | Brand name and visual identity not approved | Configurable placeholder only | Business | Pending |
| D-002 | 2026-11-11 | Launch city and categories not confirmed | Live eligibility/pricing disabled | Business/Ops | Pending |
| D-003 | 2026-11-11 | Fare, cancellation, fee/commission and payout policies not supplied | No live financial calculation | Business/Finance | Pending |
| D-004 | 2026-11-11 | Insurance provider/policy not confirmed | No coverage amount claim | Business/Legal | Pending |
| D-005 | 2026-11-11 | Firebase/Maps/backend/provider credentials not configured | Adapter/dev integration only | Engineering | Pending |
| D-006 | 2026-11-11 | KYC provider and retention policy not confirmed | Manual staging review; no raw Aadhaar storage | Business/Legal | Pending |
| D-007 | 2026-11-11 | Passenger/order source integration not defined | Real order supply cannot be assumed | Product/Engineering | Pending |

Add new rows; do not erase history. Close blockers with decision/PR evidence.

## 6. Phase completion report template
```text
Phase:
Date:
Commit / PR:
Files changed:
Build command and result:
Tests run and result:
Manual test device/emulator:
Acceptance criteria passed:
Known limitations:
New decisions/blockers:
Docs updated:
Next phase authorized:
```

## 7. Current status
Documentation baseline is prepared. Android application, backend, integrations, APK/AAB and live service have not been built or verified by this documentation package. All implementation items remain Not Started until evidence is supplied.
