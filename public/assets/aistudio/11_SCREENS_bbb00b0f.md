# Screen Specification — Drive Partner Android App

**Document ID:** DP-UI-001 | **Version:** 1.0

This file defines screen content and behavior. Visual tokens are in `Design.md`; backend rules are in `06_RULES (1).md`.

## 1. Global shell
- Bottom navigation: Home, Orders, Earnings, Rewards, Profile.
- Home toolbar: menu, duty control, service-area/saved-location shortcut where approved, notification bell and unread indicator.
- Secondary drawer: profile, vehicle, documents, service area, safety, support, language, settings and legal links.
- Active offer/trip must remain reachable and restore after process death.

## 2. Screen inventory

### S-01 Splash and language
**Content:** configurable original logo/name, concise loading state, English/हिंदी selection.  
**Actions:** save language; route based on authenticated server state.  
**States:** first launch, saved preference, loading/config failure.  
**Acceptance:** localization strings externalized and Hindi font fallback verified.

### S-02 Phone login and OTP
**Content:** country code, mobile number, send OTP, OTP entry, resend timer, edit number.  
**Actions:** request/verify OTP.  
**States:** empty, invalid, loading, expired, rate limited, network error, verified.  
**Acceptance:** no fake successful OTP in Live Mode.

### S-03 Applicant profile
**Content:** progress stepper, name, contact, approved address fields and optional profile photo.  
**Actions:** validate, save draft, continue.  
**States:** new, restored draft, validation error, save failure.  
**Acceptance:** collect sensitive fields only when necessary.

### S-04 Vehicle details
**Content:** enabled categories (Bike/Auto/Cab/Delivery), registration, make/model/color and approved vehicle data.  
**Actions:** save/update and upload photo if required.  
**States:** disabled category, validation, duplicate registration, saved.  
**Acceptance:** server controls category and vehicle approval.

### S-05 Documents and verification
**Content:** required document checklist, statuses, expiry, upload/replace, preview and rejection reason.  
**Actions:** camera/gallery selection, validation, private upload and resubmission.  
**States:** not submitted, uploading, under review, approved, rejected, expired, failed.  
**Acceptance:** no public URL; no unnecessary raw Aadhaar; internal reviewer notes hidden.

### S-06 Verification pending/blocked
**Content:** accurate status, missing/rejected/expired items, next action and support link.  
**Actions:** open document or support.  
**Acceptance:** no invented turnaround-time promise; show server reason.

### S-07 Home dashboard
**Layout:** top toolbar/duty switch; today's earnings strip; performance/completed-work card; map; configurable shortcuts (rewards, referral, safety, Auto Boost).  
**Map:** current location, service zone and only backend-backed demand overlays, with zoom/recenter.  
**States:** offline, pending, online/searching, location unavailable, no demand data, map loading/error, blocked account.  
**Acceptance:** demo data visibly labelled; no fake heatmap/multiplier.

### S-08 Service area and preferences
**Content:** area/preferences sections, searchable permitted zones, map boundary, Auto Boost explanation/toggle.  
**Actions:** add/remove allowed zones and save preference.  
**States:** loading, saved, unavailable zone, server rejection, disabled category.  
**Acceptance:** preference does not override eligibility and cannot auto-accept.

### S-09 Incoming offer
**Content:** category, pickup/drop summary, pickup distance, estimated trip distance/duration, base estimate, eligible incentive, total estimate and expiry countdown.  
**Actions:** explicit accept/reject and route preview.  
**States:** loading, active, accepting, rejected, expired, withdrawn, already assigned, offline.  
**Acceptance:** countdown uses server expiry; expired offer is not actionable; duplicate taps are controlled.

### S-10 Active trip/job
**Content:** route/map, pickup/drop details, configured contact option, trip stage, single primary action, help/SOS access.  
**Actions:** navigate, arrived, start, complete, cancel only when allowed.  
**States:** assigned, en-route, arrived, in progress, completing, completed, disputed, network lost.  
**Acceptance:** backend validates transitions; process restart restores active trip.

### S-11 Orders list/detail
**Content:** All/Completed/Cancelled filters, pagination, date filter/search, order ID/time/locations/distance/duration/status/final earning.  
**Actions:** open detail, raise support issue.  
**States:** loading, empty, pagination, offline/stale, error.  
**Acceptance:** only authorized driver records.

### S-12 Earnings dashboard
**Content:** Today/Week/Month totals, chart, base/incentive/referral/adjustment/deduction breakdown, completed work and averages.  
**Actions:** change period, open transaction and Wallet.  
**States:** no activity, loading, error, stale timestamp.  
**Acceptance:** estimated/finalized/settled labels and ledger reconciliation.

### S-13 Wallet and payout
**Content:** available/pending/settled, masked bank account, amount form, payout history/status.  
**Actions:** confirm payout request; manage account through secure provider flow.  
**States:** validation, submitted, processing, paid, failed, unverified account, insufficient balance, limit exceeded.  
**Acceptance:** Paid only after verified backend settlement.

### S-14 Rewards/incentives
**Content:** campaign requirements, progress, time window, reward, terms/version and status.  
**Actions:** inspect campaign terms.  
**States:** in progress, qualified, completed, not eligible, expired, capped.  
**Acceptance:** backend calculates; no unsupported income promise.

### S-15 Refer and Earn
**Content:** referral code/link, share/copy, pending/qualified referrals and terms.  
**Actions:** Android Sharesheet and copy.  
**States:** empty, pending, qualified, disqualified, reward posted.  
**Acceptance:** no self-reported client-side reward.

### S-16 Priority orders
**Content:** backend-assigned priority badge, additional estimated amount, eligibility/terms and offer list.  
**Actions:** open offer; no control to self-assign priority.  
**Acceptance:** priority status is truthful and server-controlled.

### S-17 Safety center
**Content:** SOS, emergency contacts, trip share where supported, safety tips, verified insurance policy information only, incident history.  
**Actions:** SOS confirmation/hold, report incident, call configured emergency contact.  
**States:** sending, confirmed, failed/unconfirmed, open, resolved.  
**Acceptance:** never claim emergency help contacted without acknowledgement.

### S-18 Help and support
**Content:** issue category, subject, description, attachment, ticket list/detail and messages.  
**Actions:** submit, reply, attach, reopen only when policy permits.  
**States:** open, in progress, waiting, resolved, closed, upload failure.  
**Acceptance:** driver sees only own tickets; internal notes hidden.

### S-19 Notifications
**Content:** grouped inbox, unread marker, date/time and type.  
**Actions:** open deep link, mark read/read all.  
**States:** empty, loading, pagination, error.  
**Acceptance:** push payload carries no unnecessary PII.

### S-20 Profile and settings
**Content:** driver ID, verification, profile, vehicle, documents, language, notification settings, privacy, terms and logout.  
**Actions:** edit permitted fields and preferences.  
**States:** loading, save error, restricted account.  
**Acceptance:** protected fields use secure flow; logout resolves active trip safely.

## 3. Shared components
- `DutyStatusControl`: Offline/Pending/Online/Turning Offline/Blocked/Unknown.
- `EarningsSummaryCard`: amount, period, financial status and updated time.
- `OfferSummaryCard`: category, endpoints, distance, amount breakdown and countdown.
- `StatusChip`: text/icon/semantic color.
- `DocumentStatusRow`: type, status, expiry and next action.
- `EmptyState`, `ErrorState`, `LoadingSkeleton`.
- `PermissionEducationSheet`: purpose explanation and settings guidance.

## 4. Accessibility and interactions
All meaningful icons have semantics; status is never color-only; critical actions are reachable and comfortably tappable; large text cannot clip; focus order is logical; avoid flashing motion; irreversible actions are confirmed; pending mutations prevent accidental double taps while retaining backend idempotency.

## 5. Data mapping
Every screen uses repository/use-case APIs, not hardcoded production values. APIs are defined in `04_TECHSPEC.md`; data ownership in `05_SCHEMA (1).md`. Demo fixtures are injected through a dedicated demo data source/environment.
