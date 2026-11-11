# Product Requirements Document — Drive Partner

**Document ID:** DP-PRD-001 | **Version:** 1.0 | **Status:** Baseline

## 1. Product summary
Drive Partner is an original native Android application for drivers and delivery partners to register, manage availability, receive work offers, complete trips/jobs and understand earnings. It prioritizes driver control, transparent financial records, safe location use and resilient order handling. India is the initial market assumption; city, categories and legal operating model require approval. The product name is temporary.

## 2. Problem statement
A driver platform requires more than a map and online switch. Drivers need to prove eligibility, understand a job before accepting, navigate and close it, see how an amount was calculated, track settlement and get help. The operator needs reliable verification, dispatch, auditability, privacy controls and reconciliation.

## 3. Goals and non-goals
### Goals
- Complete driver registration and verification.
- Synchronize availability with backend state.
- Deliver genuine offers with sufficient information for informed acceptance.
- Prevent double assignment, stale acceptance and invalid trip transitions.
- Provide order history and clear earning breakdowns.
- Distinguish estimated, finalized, available and settled money.
- Make incentive/referral eligibility transparent.
- Provide safety, support and notifications.
- Support Bike, Auto, Cab and Delivery through configurable categories.

### MVP non-goals
Passenger/merchant application, automatic acceptance, guaranteed-income claims, pooled or multi-stop trips, driver lending, insurance underwriting, complex loyalty marketplace and iOS.

## 4. Personas and roles
| Role | Need | Access |
|---|---|---|
| Applicant | Register and submit documents | Own application only |
| Verified Driver | Work, see trips/earnings, get help | Own driver data and assigned work |
| KYC Reviewer | Review documents | KYC workflow only; audited |
| Support Agent | Resolve tickets | Ticket scope; restricted PII |
| Dispatcher/Ops | Manage live operational issues | Least-privilege operational scope |
| Finance Operator | Reconcile and resolve payout exceptions | Financial records; authorized actions only |
| Admin | Configure platform | Role-based; all privileged actions audited |

## 5. Core journeys
1. Install → language → phone OTP → profile → vehicle → documents → review → activation.
2. Active driver → Home → service preferences → online → offer → accept/reject → navigate → arrive → start → complete → earning statement.
3. Driver → earnings → transaction → wallet → payout request → processing → paid/failed.
4. Driver → campaign → review terms/progress → qualifying work → server evaluation → reward entry.
5. Driver → Safety/Help → submit SOS/incident/ticket → acknowledged status and follow-up.

See `03_APPFLOW.md` for diagrams and recovery paths.

## 6. Functional requirements
Priority: **P0** launch-critical, **P1** important, **P2** post-MVP.

### FR-01 Authentication (P0)
Phone OTP, resend throttling, expiry, session recovery and backend token verification. Backend maps identity to internal user/driver. Invalid/expired credentials cannot access protected APIs.

### FR-02 Driver onboarding (P0)
Collect only approved personal/profile/address fields and vehicle information. Provide progress checklist and draft/resume. Client/server validation must agree. Incomplete applicant cannot go online.

### FR-03 Document verification (P0)
Private uploads, type/status/expiry/reviewer decision/rejection reason, replacement and expiry alerts. Minimize identity data; do not store raw Aadhaar by default. Verification decisions are auditable. Rejected/expired mandatory documents block online according to configured policy.

### FR-04 Availability/service area (P0)
Online/offline state, backend acknowledgement, location permission/GPS readiness, duty location updates, service-zone preference and reconnection sync. Network failure must not falsely display Online.

### FR-05 Home dashboard (P0)
Map, current driver location, duty state, daily earnings, completed work count, shortcuts and backend-sourced zone/demand data. Loading, empty, permission, offline and error states required.

### FR-06 Offer dispatch (P0)
Realtime event plus push fallback; fetch authoritative offer details. Show category, pickup/drop, distance, duration, estimated amount, eligible incentive and expiry. Explicit accept/reject; idempotent atomic acceptance; reject expired/stale requests.

### FR-07 Trip/job lifecycle (P0)
Accepted, en-route, arrived, started, completed/cancelled/disputed as configured. Backend validates every transition and records events. App restart restores active trip.

### FR-08 Orders history (P1)
Paginated All/Completed/Cancelled list, date/search filter and detailed receipt; show only driver's authorized records.

### FR-09 Earnings (P0)
Today/week/month summary, base/incentive/referral/adjustment breakdown and finalized amount. Must reconcile with ledger and clearly label estimates.

### FR-10 Wallet/payout (P0 when digital settlement enabled)
Available/pending/settled balances, payout request, status/history/failure. Server validates balance/limits and uses idempotent provider adapter. Client cannot modify balance. `Paid` requires verified settlement evidence.

### FR-11 Incentives (P1)
Show applicable campaign, terms, time window, progress and status. Backend computes eligibility and posts reward once.

### FR-12 Referrals (P1)
Server-generated code/link, share/copy, attribution and qualification status. Anti-self-referral and fraud controls.

### FR-13 Safety (P0 basic; expanded P1)
Emergency contacts, SOS/incident report, safety guidance and trip share where supported. Show insurance only from a valid policy with approved wording.

### FR-14 Help/support (P1)
Ticket category, description, attachment, ID, status and message history. No internal notes or other drivers' tickets exposed.

### FR-15 Notifications (P0)
Offers, trip updates, document review, earning/payout and support events; minimal push payload with authenticated deep-link fetch.

### FR-16 Localization/accessibility (P1)
English/Hindi baseline, externalized strings, font scaling, screen reader semantics, sufficient contrast and touch targets.

### FR-17 Demo Mode (P0 during development)
Synthetic fixtures, persistent Demo label, separate environment/build flavor. Demo cannot mutate production data or payout systems.

## 7. Success metrics (instrument; do not invent targets)
Applicant funnel; verification turnaround; time to first online session; online utilization; offer delivery latency/expiry/acceptance; cancellation/completion rate; crash-free sessions; API success; ledger reconciliation exceptions; payout success/time; support resolution time; safety acknowledgement time. Each metric needs a definition and privacy review.

## 8. Non-functional requirements
- Backend authoritative state for all critical mutations.
- Versioned APIs and contracts.
- INR amount represented as integer paise plus currency; never floating-point money.
- UTC persistence and localized display.
- Idempotent critical operations and auditability.
- Process death/network retry must not duplicate orders or financial events.
- Security authorization tests and performance budgets established from device/load tests.

## 9. Risks
| Risk | Mitigation |
|---|---|
| KYC error/expiry | Review workflow, expiry tracking, eligibility gate |
| Double assignment | Database transaction and concurrency tests |
| Location permission/battery | Contextual permission, duty-only service, adaptive updates |
| Ledger mismatch | Append-only ledger, source IDs, reconciliation |
| False demand claims | Backend data only |
| Payout/provider failure | Adapter, webhook verification, exception workflow |
| Regulation uncertainty | Launch-specific professional review |
| AI code defects | Phase gates, build/tests, code review |

## 10. Assumptions needing approval
No final fares, commission, fees, tax treatment, payout timing, insurance cover, income target, legal entity or launch city has been supplied. These remain configurable and Live Mode financial functions stay disabled until approved.
