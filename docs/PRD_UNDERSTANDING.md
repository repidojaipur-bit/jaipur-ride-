# Drive Partner — Comprehensive PRD & Master Specification Analysis

**Document ID:** DP-DOC-PRD-001  
**Version:** 1.0  
**Status:** Baseline Product Specification  
**Source Documents:** `00_MASTER.md` (DP-MASTER-001) & `PRD.md` (DP-PRD-001)  
**Date:** 11 November 2026  

---

## 1. Product Vision

**Drive Partner** is an original, reliable, driver-first native Android application engineered for drivers and delivery partners to register, prove eligibility, manage work availability, receive genuine work offers, navigate and complete trips/jobs, inspect transparent earnings, access approved incentives, and request settlements.

### Core Product Principles
1. **Driver Control:** The driver explicitly chooses when to go online and explicitly accepts or rejects every dispatch offer. No automatic acceptance without driver consent.
2. **Truthful Money:** Clear conceptual separation between estimated fare, finalized earnings, incentives, available balance, pending balance, and settled payout. All monetary values in INR are represented strictly as integer paise (never floating-point numbers).
3. **Backend Authority:** The backend is the single source of truth for partner eligibility, availability acknowledgment, dispatch generation, atomic assignment, trip state transitions, fare calculation, reward qualification, immutable ledger entries, and payout reconciliation.
4. **Safety & Privacy:** Location tracking is strictly purpose-limited. Offline drivers are never tracked unless an active trip or approved safety workflow explicitly requires it. KYC documents and PII are private and secured.
5. **Recoverability & Resilience:** Process death, network drops, out-of-order events, retries, and expired offers must never corrupt trip states or ledger balances.
6. **No Fake Production Behavior:** Simulated orders, fabricated demand heatmaps, artificial earnings, or unverified insurance promises must never appear in Live Mode; synthetic data is strictly isolated to a labeled Demo Mode.
7. **Original Identity:** Independent branding, copy, illustrations, visual system, and terminology; never copying competitor trademarks, logos, mascots, or proprietary assets.

---

## 2. Target Users, Personas & Roles

| Persona / Role | Description & Primary Need | Access Scope & Governance |
|---|---|---|
| **Applicant** | Driver seeking registration, entering profile/vehicle data, and uploading KYC documents. | Restricted strictly to their own registration draft. Cannot go online or see dispatch. |
| **Verified Driver** | Onboarded, document-verified partner operating Bike, Auto, Cab, or Delivery services. | Own driver profile, active duty controls, received offers, active trip, earning history, wallet, safety, and support. |
| **KYC Reviewer** | Operator reviewing submitted licenses, RC, permits, and identity documents. | Dedicated KYC review workflow only; all approval/rejection decisions are fully audited with logged reasons. |
| **Support Agent** | Operations team resolving driver disputes, safety queries, and operational tickets. | Ticket scope only; restricted PII view; internal notes hidden from drivers. |
| **Dispatcher / Operations** | Operational staff monitoring active incidents, service area health, and unassigned exceptions. | Least-privilege operational monitoring scope. |
| **Finance Operator** | Finance staff managing ledger reconciliation, bank settlement exceptions, and payout failures. | Financial ledger records and payout exception tools; authorized actions only. |
| **Platform Admin** | System administrators configuring categories, operational zones, incentive rules, and global flags. | Role-Based Access Control (RBAC); all privileged mutations strictly logged and audited. |

---

## 3. Driver & Service Categories

Supported service categories defined in the domain data model:
1. **Bike Taxi (`BIKE`)**: Two-wheeler passenger transport (`passenger_two_wheeler`).
2. **Auto Rickshaw (`AUTO`)**: Three-wheeler passenger transport (`passenger_three_wheeler`).
3. **Cab / Car (`CAB`)**: Four-wheeler passenger transport (`passenger_four_wheeler`).
4. **Delivery Partner (`DELIVERY`)**: Parcel, grocery, and courier logistics (`logistics_delivery`).

*Launch Guidance:* Operationally launch with one or two approved categories (e.g., Bike and Auto). A service category cannot be activated until its local legal status, vehicle requirements, fare cards, insurance policy, and operational rules are formally approved.

---

## 4. Core Application Modules

1. **Authentication & Session Management (`FR-01`)**
2. **Driver Onboarding & Profile (`FR-02`)**
3. **KYC Document Verification (`FR-03`)**
4. **Availability, Duty Status & Service Area (`FR-04`)**
5. **Home Dashboard & Demand Visualizer (`FR-05`)**
6. **Offer Dispatch & Real-Time Negotiation (`FR-06`)**
7. **Trip / Job Execution Lifecycle (`FR-07`)**
8. **Orders & Trips History (`FR-08`)**
9. **Earnings & Financial Ledger (`FR-09`)**
10. **Wallet & Bank Payout Settlement (`FR-10`)**
11. **Incentives & Milestone Campaigns (`FR-11`)**
12. **Referral Program (`FR-12`)**
13. **Safety Center, SOS & Incident Reporting (`FR-13`)**
14. **Help Desk & Support Tickets (`FR-14`)**
15. **Notifications & In-App Alerts (`FR-15`)**
16. **Localization & Accessibility (`FR-16`)**
17. **Demo Mode vs. Live Mode Harness (`FR-17`)**

---

## 5. Functional Requirements Breakdown

### FR-01: Authentication (P0 - Launch Critical)
- Phone OTP authentication flow with country code handling (+91 baseline).
- Resend throttling, countdown timer, and OTP expiration window.
- Token-based authenticated sessions (Firebase Phone Auth + backend JWT verification).
- Session recovery upon application relaunch or process death.
- Immediate revocation upon account deactivation or suspension.

### FR-02: Driver Onboarding (P0 - Launch Critical)
- Collection of approved personal fields: full legal name, phone, emergency contact, address.
- Vehicle details: category selection (Bike/Auto/Cab/Delivery), registration number, make/model, fuel type.
- Progress checklist indicating completion percentage.
- Draft saving and resumption across sessions.
- Client and server validations must strictly agree; incomplete applicant cannot go online.

### FR-03: Document Verification (P0 - Launch Critical)
- Secure, private upload for Driving License, Registration Certificate (RC), Vehicle Insurance, and Identity document.
- Per-document state machine: `Pending Upload` → `Under Review` → `Approved` OR `Rejected` (with specific reason) → `Expired`.
- In-app document replacement flow for rejected or expiring documents.
- Data minimization: do not store raw Aadhaar numbers; store only approved identification tokens/redacted proofs.
- Mandatory verified documents gate the online switch.

### FR-04: Availability & Service Area (P0 - Launch Critical)
- Explicit driver online/offline duty toggle.
- Local toggle is a request; online state is active only upon backend acknowledgement.
- Runtime preconditions: valid GPS fix, fine location permission granted, account active, mandatory documents approved, vehicle linked, inside service zone.
- Periodic location telemetry only during active duty (foreground service with persistent notification).
- Automatic offline transition if location permission is revoked or network disconnects permanently.

### FR-05: Home Dashboard (P0 - Launch Critical)
- Map interface displaying current driver coordinates and operational service zone boundaries.
- Duty status indicator pill with rapid toggle mechanism.
- Today’s earnings summary widget and completed trip counter.
- Backend-sourced demand/surge indicators (no fake artificial surges).
- Standardized UI states: Loading, Empty (no active offers), Permission Error, Offline, and Network Error.

### FR-06: Offer Dispatch (P0 - Launch Critical)
- Real-time dispatch delivery (WebSocket/SSE) with FCM push fallback.
- Offer card displaying: Service Category, Pickup point, Drop point, Estimated distance (km), Estimated duration (mins), Estimated net earning (in INR), Countdown timer before expiry.
- Explicit `Accept` and `Reject` buttons.
- Atomic backend assignment: prevents double-booking and rejects stale/expired offers idempotently.

### FR-07: Trip / Job Lifecycle (P0 - Launch Critical)
- Strict state machine: `Accepted` → `En Route to Pickup` → `Arrived at Pickup` → `OTP/Trip Started` → `In Transit to Drop` → `Completed` (or `Cancelled`/`Disputed`).
- Server-authoritative transitions: client cannot transition without backend validation.
- Process death and app restart recovery: restores active trip view with accurate progress.
- Navigation integration (turn-by-turn routing via Google Maps intent or SDK).
- Cancellation with mandatory reason selection and cancellation policy enforcement.

### FR-08: Orders History (P1 - Post-MVP / Fast-Follow)
- Paginated list of historical trips categorized by `All`, `Completed`, and `Cancelled`.
- Date range filtering and search by Order ID.
- Detailed receipt view: pickup/drop addresses, timestamps, distance, duration, fare breakdown, and tip.
- Authorized scope: drivers can query only their own historical records.

### FR-09: Earnings & Statement (P0 - Launch Critical)
- Aggregated summaries for Today, This Week, and This Month.
- Transparent line-item breakdown: Base Fare, Distance Fare, Time Fare, Waiting Charge, Surge/Incentive Bonus, Platform Fee deduction, Net Finalized Earning.
- Clear distinction between "Estimated" earnings and "Finalized" ledger entries.
- Reconciliation with backend ledger (all amounts stored in integer paise).

### FR-10: Wallet & Payout Settlement (P0 - Digital Settlement)
- Real-time balances: Available for Payout, Pending Clearance, and Lifetime Settled.
- Driver bank account / UPI ID registration and verification status.
- Payout request initiation respecting minimum/maximum limits.
- Payout state machine: `Requested` → `Processing` → `Settled (Paid)` OR `Failed (Reversed)`.
- Client cannot manipulate balances; `Paid` state requires verified settlement webhook from provider.

### FR-11: Incentives & Bonus Campaigns (P1 - Fast-Follow)
- In-app campaign browser displaying active incentive schemes (e.g., "Complete 10 trips between 5 PM - 10 PM for ₹250 bonus").
- Real-time progress bar tracking qualifying trips.
- Server-side rule evaluation; single-payout posting to prevent duplicate incentive claims.

### FR-12: Referral Program (P1 - Fast-Follow)
- Server-generated unique referral code and shareable invite link.
- Tracking referred applicants and their qualification milestones (e.g., "Completes 20 trips within 14 days").
- Anti-fraud controls: device fingerprinting, anti-self-referral validation.

### FR-13: Safety & Emergency SOS (P0 Basic, P1 Expanded)
- **P0 Basic:** One-tap Emergency SOS button connecting to 112/local police or internal emergency helpline; emergency contact setup.
- **P1 Expanded:** Real-time trip status sharing with trusted contacts; incident reporting form with evidence attachment.
- Clear, legally approved insurance terms displayed only when an active underwritten policy is present.

### FR-14: Help Desk & Support (P1 - Fast-Follow)
- Categorized ticketing system (Trip issues, Payment dispute, Account assistance, Vehicle update).
- Ticket detail view with status (`Open`, `In Progress`, `Resolved`) and two-way message history.
- Internal agent notes and PII of passengers/other drivers must never be exposed.

### FR-15: Notifications System (P0 - Launch Critical)
- FCM push notifications for incoming work offers, trip cancellations, KYC document review status, payout completions, and critical safety alerts.
- Data payloads kept minimal; tapping notification deep-links into app and fetches authoritative data over authenticated API.

### FR-16: Localization & Accessibility (P1 - Initial English/Hindi Baseline)
- Complete dual-language support: English (`res/values/strings.xml`) and Hindi (`res/values-hi/strings.xml`).
- Clean typography hierarchy, minimum 48dp touch targets, WCAG AAA daylight contrast for dashboard visibility.

### FR-17: Isolated Demo Mode (P0 - Development Harness)
- Explicit toggle for test/demo environments with persistent "DEMO MODE" watermark.
- Uses synthetic fixtures; strictly forbidden from calling live payment gateways, banking APIs, or production dispatch.

---

## 6. Non-Functional Requirements (NFRs)

1. **Financial Integrity:** Zero floating-point monetary calculations. Every currency value stored as integer paise (e.g., ₹150.50 = `15050` paise) alongside currency code `INR`. Append-only double-entry ledger.
2. **State Authority:** Client is a dumb display & capture terminal for critical state; backend database owns availability, assignments, and transitions.
3. **Idempotency:** Unique `Idempotency-Key` headers on all critical POST/PATCH endpoints (offers acceptance, trip status changes, payout requests).
4. **Resilience & Process Death:** SQLite/Room persistence of active session, active trip, and cached profile to ensure seamless recovery when the OS terminates background processes.
5. **Location & Battery Efficiency:** Adaptive location tracking (high accuracy during active trip navigation; balanced power during idle online duty; zero background tracking when offline).
6. **Network Optimization:** Offline detection, exponential backoff on retry, WebSocket with keep-alive ping/pong, and lightweight JSON payloads.
7. **Security & Cryptography:** HTTPS/TLS 1.3 everywhere, certificate pinning on production endpoints, API key protection via secrets plugin, no hardcoded credentials.

---

## 7. Driver Onboarding Requirements Matrix

| Step | Data Collected | Validation Rules | Blocker for Online? |
|---|---|---|---|
| 1. Phone Verification | Mobile number (+91) | 10 digits, valid SMS OTP, session token issued | Yes |
| 2. Basic Profile | Legal name, email, city, language preference | Non-empty, valid name format, supported city | Yes |
| 3. Vehicle Linking | Category, plate number, make/model, year | Regex for Indian plate (e.g., `^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$`) | Yes |
| 4. Document Submission | DL, RC, Insurance, Photo | PDF/JPEG/PNG, under 5MB, non-expired date entered | Yes |
| 5. Review & Approval | KYC Reviewer approval record | Must be `APPROVED` by authorized reviewer in backend | Yes |
| 6. Bank / Settlement | Account holder name, IFSC, Account/UPI | IFSC verification, account number confirmation | Only for Payout |

---

## 8. Order Management & Dispatch Lifecycle

```
[Available Online]
       │
       ▼ (Backend Dispatches Offer)
[Offer Received] ──(Countdown Expired / Driver Rejects)──► [Offer Dismissed / Returned to Pool]
       │
       ▼ (Driver Clicks Accept - Atomic Transaction)
 [Trip Accepted]
       │
       ▼ (Driver En Route)
[Arrived at Pickup]
       │
       ▼ (OTP Verified with Passenger/Sender)
  [Trip Started]
       │
       ▼ (In Transit - Turn-by-Turn GPS)
 [Trip Completed] ──► [Earning Statement Generated] ──► [Ledger Credited in Paise]
```

*Rules:*
- At most **one** active trip per driver in MVP (no multi-order pooling or split deliveries).
- Trip cancellations require a standardized reason code and record the canceling party.
- If driver app dies mid-trip, relaunching immediately restores the active trip state and map route.

---

## 9. Earnings & Wallet Requirements

- **Ledger Invariance:** Wallet balance is calculated as $\sum \text{Credits} - \sum \text{Debits}$. The client cannot modify or increment wallet amounts directly.
- **Earning States:**
  - *Estimated:* Displayed on dispatch offer card.
  - *Finalized:* Calculated upon trip completion after distance/time verification.
  - *Available:* Unlocked for payout request.
  - *Pending:* Under temporary fraud hold or weekly clearance.
  - *Settled:* Confirmed payout deposited to driver's bank.
- **Payout Gateways:** Integration with Indian bank payout APIs (IMPS/NEFT/UPI) via idempotent backend payment adapters.

---

## 10. Rewards, Incentives & Referrals

- **Incentive Types:**
  - *Daily Trip Targets:* Complete $N$ trips within a defined time window.
  - *Peak Hour Multipliers:* Bonus payout for trips completed in high-demand zones.
- **Referral Flow:**
  - Unique alphanumeric code per driver.
  - Attribution tracked upon applicant signup.
  - Reward unlocked only after referred partner completes threshold qualifying trips within deadline.
  - Anti-fraud check: prevents matching bank accounts or duplicate hardware IDs.

---

## 11. Safety & Support Requirements

- **Emergency SOS:** Prominent Red SOS button accessible on the top bar during an active trip.
  - Displays local emergency number (112) with instant dialer intent.
  - Transmits immediate distress payload to backend with live GPS coordinates, vehicle plate, and active trip ID.
- **Incident Reporting:** Post-trip reporting form for accidents, passenger misconduct, or vehicle breakdown.
- **Support Tickets:** Categorized ticket submission, status tracking, and message thread.

---

## 12. Admin & Backend Dependencies

1. **Identity & Auth Service:** Firebase Auth token exchange and driver role mapper.
2. **Driver Profile & KYC Service:** Document storage (private GCS/S3), reviewer audit trail, and document expiry cron jobs.
3. **Location & Geospatial Dispatch Engine:** PostGIS spatial queries, geofencing, and driver proximity indexing.
4. **Dispatch & Assignment Service:** Atomic queue, lock manager to guarantee single assignment, and offer countdown timer.
5. **Trip Lifecycle Engine:** State machine validator and cancellation fee evaluator.
6. **Financial Ledger & Wallet Service:** Append-only ledger in PostgreSQL, payout adapter with webhook reconciliation.
7. **Messaging & Push Service:** FCM integration with localized payload strings.

---

## 13. Security, Privacy & Compliance

- **No Public KYC URLs:** Document uploads must use time-limited presigned URLs. Files must never be publicly readable.
- **PII Protection:** Redacted phone numbers (virtual call masking or in-app VoIP preferred before launch). Passengers never see driver's full private address.
- **Location Purpose Limitation:** Background location collection occurs exclusively during `ONLINE` status or active trips. Foreground service must display an un-dismissible ongoing notification.
- **Play Store Compliance:** Full compliance with Google Play Developer Policy on Foreground Service Types (`location`), Background Location disclosures, and financial transparency.

---

## 14. MVP Scope vs. Future Roadmap

### In Scope for MVP
- Native Android app (Kotlin + Jetpack Compose + Material 3).
- Single Activity architecture with Navigation Compose.
- Phone OTP Login with session restoration.
- Driver onboarding: personal profile, vehicle details, document upload.
- Online/offline duty toggle with backend synchronization.
- Home dashboard with map, location tracking, and status banner.
- Real-time offer dispatch card with countdown timer, accept/reject, and atomic claim.
- Single-trip execution lifecycle (Accept → En Route → Arrived → Start → Complete).
- Today’s earnings summary and basic trip receipt statement.
- Basic wallet balance view and payout request submission.
- Basic SOS emergency call button.
- Reusable UI state components (Loading, Empty, Error, Offline).
- English and Hindi localization.
- Isolated Demo Mode with synthetic data fixtures.

### Out of Scope for MVP (Future Roadmap / Post-MVP)
- Passenger or customer mobile app.
- Merchant ordering interface.
- iOS client application.
- Multi-order pooling, split pickups, or multi-stop delivery batches.
- Automatic dispatch acceptance (Auto-Accept).
- Driver lending, micro-loans, or cash advances.
- In-house insurance underwriting and policy sales.
- Gamified loyalty marketplace with physical merchandise redemption.
- Large-scale cash-on-delivery reconciliation engine.
- Multi-city administrative dashboard with complex hierarchy.

---

## 15. Open Decisions Catalog (Prior to Live Launch)

The following items require formal business, regulatory, and executive sign-off before entering production:

1. **Brand Identity:** Final commercial name, legal registered trademark, official logo, and domain.
2. **Operating Entity & City:** Legal entity name, initial launch city (e.g., Jaipur, Rajasthan), and municipal operating permits.
3. **Launch Category Sequence:** Formal selection of initial launch categories (e.g., Launch with Bike Taxi + Auto Rickshaw first; enable Cab and Delivery in Phase 2).
4. **Fare Cards & Commission:** Base fare, per-kilometer rate, per-minute wait charges, platform take-rate (commission %), GST/TDS tax deductions, and cancellation fees.
5. **KYC Vendor & Aadhaar Policy:** Selection of third-party automated OCR/KYC verification vendor (e.g., Karza, HyperVerge, Setu) vs. manual back-office review.
6. **Payout Gateway & Bank Schedule:** Selection of payment disbursement partner (e.g., RazorpayX, Cashfree Payouts, Stripe) and settlement cadence (instant vs. daily T+1).
7. **Commercial Insurance Partner:** Group personal accident policy underwriter and verified coverage wording for driver partners during active duty.
8. **Contact Masking Solution:** Selection of telecommunications masking provider (e.g., Exotel, Twilio) to mask driver and customer mobile numbers.
9. **Emergency Response SLA:** Protocol and operational team responsible for acting on in-app SOS signals.
10. **Redis Infrastructure:** Decision on whether Redis is deployed immediately at launch or introduced after initial load benchmarking.

---

## 16. Acceptance Criteria Matrix

| Feature | Acceptance Criteria |
|---|---|
| **Duty Toggle** | Switching Online checks location permissions, account status, and approved documents; reflects Online only after backend 200 OK. |
| **Offer Acceptance** | Acceptance is atomic; if two drivers accept within milliseconds, exactly one succeeds and the other receives a graceful "Offer Expired / Already Taken" state. |
| **Trip Lifecycle** | Driver cannot skip states (e.g., cannot complete a trip without starting it). App relaunch mid-trip restores exact state without data loss. |
| **Financial Calculations** | Earnings and wallet balance match ledger records exactly down to the paise. No floating-point rounding errors. |
| **Offline Privacy** | Transitioning to Offline immediately terminates foreground location tracking and dismisses the foreground service notification. |
| **Localization** | Switching between English and Hindi updates all screen text, tab labels, and status messages without restarting the application. |
| **Demo Mode Isolation** | When Demo Mode is active, a persistent banner is visible, and no network calls are dispatched to live payment or production dispatch endpoints. |

---

## 17. Baseline Confirmation

Both **`00_MASTER.md`** and **`PRD.md`** have been thoroughly read, analyzed, and synthesized into this document. This specification serves as the authoritative product baseline for all subsequent development phases of the Drive Partner project.
