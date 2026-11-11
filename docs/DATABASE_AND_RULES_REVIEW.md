# Database Model & Business Rules Technical Review — Drive Partner

**Document ID:** DP-DOC-DB-RULES-001  
**Version:** 1.0  
**Status:** Baseline Specification & Technical Audit  
**Source References:** `05_SCHEMA (1).md` (DP-DB-001), `06_RULES (1).md` (DP-RULES-001), `02_Architecture.md` (DP-ARCH-001), `04_TECHSPEC.md` (DP-TECH-001)  
**Date:** 11 November 2026  

---

## 1. Executive Summary & Authoritative Invariants

This technical review analyzes the complete relational data model, domain entities, state machines, and business rules governing the **Drive Partner** platform.

### Non-Negotiable Core Invariants:
1. **Single Driver Assignment (OFFER-004, TRIP-001):** An order can be assigned to exactly one driver. Multiple drivers may receive concurrent offers, but acceptance is governed by an atomic database transaction using row-level conditional locking.
2. **Atomic & Idempotent Acceptance (OFFER-003, OFFER-005):** Offer acceptance requires a valid server-generated `Idempotency-Key` and checks: status is `OFFERED`, current timestamp < `expires_at`, driver is `ONLINE` and eligible, and `order.assigned_driver_id IS NULL`. Stale, expired, withdrawn, or already-assigned offers fail immediately with standard error codes.
3. **No Unverified Duty (DUTY-001, KYC-002):** Unverified drivers (`APPLICANT`, `PENDING_REVIEW`, `SUSPENDED`, `REJECTED`) cannot go `ONLINE`. All mandatory documents for the driver's category and zone must be in `APPROVED` status and unexpired.
4. **Backend-Controlled Trip States (TRIP-001, TRIP-002):** State transitions are validated server-side. Skipping states (e.g., jumping from `ASSIGNED` directly to `COMPLETED`) is strictly forbidden. Every transition logs an immutable entry to `trip_events`.
5. **Client Financial Impotence (WALLET-001, WALLET-002):** The client cannot create ledger entries, modify wallet balances, or declare a payout successful. All balances are derived from append-only, double-entry balanced postings in `ledger_entries`.
6. **Auditable Financials (WALLET-003, WALLET-004, FARE-007):** Every ledger posting must reference an immutable `source_type` and `source_id` with an `idempotency_key`. All amounts are stored strictly as integer minor units (`amount_minor: BIGINT`, paise for INR) with explicit ISO currency codes.
7. **Settlement Evidence Gate (PAY-004):** A payout remains `REQUESTED` or `PROCESSING` until an authenticated, signature-verified webhook from the banking/payout provider confirms successful fund transfer.

---

## 2. Entity Relationship Summary

```
[users] 1 ──── 1 [drivers] 1 ──── * [vehicles]
                   │   │
                   │   ├──── * [driver_category_permissions]
                   │   ├──── * [driver_documents] 1 ─── * [verification_events]
                   │   ├──── 1 [driver_presence]
                   │   ├──── * [driver_zone_preferences] ─── * [service_zones]
                   │   ├──── 1 [wallet_accounts]
                   │   ├──── * [payout_accounts]
                   │   └──── * [payouts]
                   │
                   ├──── * [order_offers] ─── * [orders] 1 ─── 1 [trips]
                   │                                             │
                   │                                             ├── * [trip_events]
                   │                                             └── 1 [earning_statements]
                   │                                                         │
                   └──── * [driver_rewards] ─────────────────────────────────┼───► [ledger_transactions]
                                                                             │             │
                                                                             │             └── * [ledger_entries]
```

### Detailed Entity Analysis Matrix

| Entity | Primary Key | Key Foreign Keys & Relationships | Status Enums / Values | Ownership & Auth Scope | Retention & Privacy Notes |
|---|---|---|---|---|---|
| **`users`** | `id UUID` | None (1:1 with `drivers`) | `account_status` (`APPLICANT`, `PENDING_REVIEW`, `ACTIVE`, `SUSPENDED`, `REJECTED`, `CLOSED`) | Driver owns self; backend resolves internal ID from `firebase_uid`. | PII (`phone_e164`) must be encrypted/tokenized at rest. |
| **`drivers`** | `id UUID` | `user_id UUID FK users` | `verification_status`, `activation_status` | Driver views own record. Admin/Ops view with audit trail. | Address fields are restricted PII. Profile photo stored in private object storage. |
| **`vehicles`** | `id UUID` | `driver_id UUID FK drivers` | `service_category` (`BIKE`, `AUTO`, `CAB`, `DELIVERY`), `status` | Driver manages own vehicle. Verified upon review. | Plate numbers stored both normalized and masked for privacy. |
| **`driver_documents`** | `id UUID` | `driver_id UUID FK drivers`, `vehicle_id UUID NULL FK` | `document_status` (`NOT_SUBMITTED`, `UPLOADED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `EXPIRED`) | Driver uploads; KYC Reviewer audits and decides. | Raw files in private S3/GCS with short-lived presigned URLs. No raw Aadhaar. |
| **`service_zones`** | `id UUID` | MultiPolygon boundary | `enabled BOOLEAN` | Platform Admin managed. | Geospatial PostGIS definitions. |
| **`driver_presence`** | `driver_id UUID` | `driver_id UUID FK drivers` | `duty_status` (`OFFLINE`, `GOING_ONLINE`, `ONLINE`, `GOING_OFFLINE`, `BLOCKED`) | Driver sets desired; backend acknowledges. | Ephemeral table (1 row per driver). Represents *current* presence, not historical trail. |
| **`orders`** | `id UUID` | `assigned_driver_id UUID NULL FK drivers`, `fare_quote_id UUID` | `status` (`UNASSIGNED`, `ASSIGNED`, `CANCELLED`, `COMPLETED`) | Passenger/system creates; driver assigned atomically. | Customer PII masked; only pickup/drop labels and coordinates exposed to driver. |
| **`order_offers`** | `id UUID` | `order_id UUID FK`, `driver_id UUID FK` | `offer_status` (`CREATED`, `OFFERED`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `WITHDRAWN`) | Driver receives and acts before `expires_at`. | Unique composite `(order_id, driver_id, offer_sequence)`. Offer TTL 15–30s. |
| **`trips`** | `id UUID` | `order_id UUID UNIQUE FK`, `driver_id UUID FK` | `trip_status` (`ASSIGNED`, `EN_ROUTE_TO_PICKUP`, `ARRIVED_AT_PICKUP`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `DISPUTED`) | Assigned driver and system dispatch. | 1 active trip per driver invariant in MVP. Unique order 1:1 constraint. |
| **`trip_events`** | `id UUID` | `trip_id UUID FK` | `actor_type` (`DRIVER`, `CUSTOMER`, `OPS`, `SYSTEM`) | System append-only audit trail. | Unique `(trip_id, idempotency_key)` to prevent duplicate milestone recording. |
| **`earning_statements`**| `id UUID` | `driver_id UUID FK`, `trip_id UUID FK` | `status` (`DRAFT`, `FINALIZED`, `ADJUSTED`) | Assigned driver views own statement. | Unique source constraint prevents duplicate earnings generation. |
| **`ledger_transactions`**| `id UUID`| None (Parent of entries) | `status` (`POSTED`, `REVERSED`) | Finance/Ledger system only. | Unique `(source_type, source_id)` and unique `idempotency_key`. |
| **`ledger_entries`** | `id UUID` | `transaction_id UUID FK`, `account_id UUID FK` | `direction` (`DEBIT`, `CREDIT`) | Immutable append-only. Zero direct driver access. | Double-entry invariant: $\sum \text{Debits} = \sum \text{Credits}$ per currency. |
| **`wallet_accounts`** | `id UUID` | `driver_id UUID UNIQUE FK` | `status` (`ACTIVE`, `FROZEN`, `RESTRICTED`) | Driver views derived balance. | Read-only projection of ledger balances. Client cannot mutate. |
| **`payouts`** | `id UUID` | `driver_id UUID FK`, `payout_account_id UUID FK` | `payout_status` (`REQUESTED`, `PROCESSING`, `PAID`, `FAILED`, `CANCELLED`, `REVERSED`) | Driver initiates; backend & bank provider settle. | `PAID` requires cryptographically verified provider webhook confirmation. |
| **`support_tickets`** | `id UUID` | `driver_id UUID FK` | `ticket_status` (`OPEN`, `IN_PROGRESS`, `WAITING_FOR_DRIVER`, `RESOLVED`, `CLOSED`) | Driver views own tickets only. | Internal operator notes strictly filtered from driver-facing payloads. |
| **`safety_incidents`** | `id UUID` | `driver_id UUID FK`, `trip_id UUID NULL FK` | `status` (`REPORTED`, `ACKNOWLEDGED`, `INVESTIGATING`, `CLOSED`) | Driver, Safety Ops, Emergency services. | Immediate dispatch trigger with high-priority audit trail. |

---

## 3. Business State Machines

### 3.1 Account Lifecycle State Machine
```
[APPLICANT] ──(Profile & Documents Submitted)──► [PENDING_REVIEW]
                                                        │
                      ┌─────────────────────────────────┴─────────────────────────────────┐
                      ▼                                                                   ▼
                 [APPROVED] ──► [ACTIVE]                                             [REJECTED]
                                   │
                      ┌────────────┴────────────┐
                      ▼                         ▼
                 [SUSPENDED]                 [CLOSED]
```
- **Rule:** A driver can enter `ACTIVE` only after all mandatory documents are marked `APPROVED` and unexpired by an authorized KYC reviewer.

---

### 3.2 Duty Status State Machine
```
 [OFFLINE] ──(Driver Toggles "Go Online")──► [GOING_ONLINE] (Pending Server Validation)
     ▲                                                │
     │                        ┌───────────────────────┴───────────────────────┐
     │                        ▼ (Validation Success - 200 OK)                 ▼ (Validation Fails)
     │                    [ONLINE]                                        [BLOCKED / OFFLINE]
     │                        │
     │                        ▼ (Driver Toggles "Go Offline")
     └─────────────── [GOING_OFFLINE] ◄──(Active Trip Completion Required)
```
- **Rule DUTY-002:** The local switch does not prove availability. State remains `GOING_ONLINE` until the backend verifies account, mandatory documents, vehicle eligibility, location freshness, and returns acknowledgment.
- **Rule DUTY-006:** A driver cannot silently abandon an active trip by toggling offline.

---

### 3.3 Offer & Dispatch State Machine
```
                  [CREATED]
                      │
                      ▼
                  [OFFERED] (Timer Starts: 15–30s TTL)
                      │
        ┌─────────────┼─────────────────────────────┬─────────────────────────────┐
        ▼             ▼                             ▼                             ▼
   [ACCEPTED]     [REJECTED]                   [EXPIRED]                     [WITHDRAWN]
(Locks Order)  (Driver Declines)          (Timer Expiration)            (Cancelled by System)
```
- **Rule OFFER-004:** Atomic conditional update:
  ```sql
  UPDATE orders 
  SET assigned_driver_id = :driverId, status = 'ASSIGNED', assigned_at = NOW() 
  WHERE id = :orderId AND assigned_driver_id IS NULL AND status = 'UNASSIGNED';
  ```
  If 0 rows affected, another driver won the race condition; the client receives `OFFER_ALREADY_ASSIGNED`.

---

### 3.4 Trip Lifecycle State Machine
```
 [ASSIGNED]
     │
     ▼ (Driver action: "Start Navigation to Pickup")
[EN_ROUTE_TO_PICKUP]
     │
     ▼ (Driver arrives at pickup coordinates)
[ARRIVED_AT_PICKUP]
     │
     ▼ (Passenger OTP verified)
[IN_PROGRESS]
     │
     ├─────────────────────────────────────────────┐
     ▼ (Destination reached & verified)            ▼ (Driver/Customer/Ops cancel)
[COMPLETED]                                   [CANCELLED]
     │
     ▼ (Fare calculated & ledger credited)
[Earning Statement Generated]
```
- **Rule TRIP-001:** Transitions must occur sequentially (`ASSIGNED` $\rightarrow$ `EN_ROUTE` $\rightarrow$ `ARRIVED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`).
- **Rule TRIP-002:** Every transition logs an immutable entry to `trip_events` with actor ID and idempotency key.

---

### 3.5 Payout Lifecycle State Machine
```
[REQUESTED] ──(Balance & Limits Validated)──► [PROCESSING]
                                                     │
                       ┌─────────────────────────────┴─────────────────────────────┐
                       ▼ (Verified Bank Webhook)                                   ▼ (Bank Rejection / Failure)
                    [PAID]                                                      [FAILED]
                       │                                                           │
             (Settlement Confirmed)                                     (Hold Released / Balance Refunded)
                                                                                   │
                                                                                   ▼
                                                                              [REVERSED]
```
- **Rule PAY-004:** Client cannot mark a payout `PAID`. Only cryptographically verified webhooks (`provider_webhook_events`) with valid signature and matching transfer ID can transition a payout to `PAID`.

---

## 4. Security & Privacy Requirements

1. **Identity & Authentication:**
   - Every protected API route authenticates via `Authorization: Bearer <Firebase_ID_Token>`.
   - The backend validates signature, issuer, audience, and expiry, then maps the identity to an internal `user_id` and `driver_id`. The client never supplies its own role or driver ID.
2. **KYC & Document Storage:**
   - Document binaries are stored in private cloud object storage (S3/GCS buckets with blocked public access).
   - Document upload: Client requests a short-lived (15-min) presigned upload URL via `/api/v1/documents/upload-intent`.
   - Document view: Reviewers access files through short-lived presigned read URLs with access logging.
   - Raw Aadhaar numbers are never stored in plain text. Compliant verification provider reference tokens and masked strings (`XXXX-XXXX-1234`) are used exclusively.
3. **Location Privacy:**
   - Background location collection is enabled only when `duty_status = 'ONLINE'` or an active trip is in progress.
   - When the driver goes `OFFLINE`, location updates are immediately halted and the foreground service is terminated.
   - Location history (`driver_location_events`) is retained strictly per legal and operational requirements and subjected to automated data pruning.
4. **Customer & Driver PII Masking:**
   - Real telephone numbers are never exposed in mobile UI or API payloads. All voice/SMS communications route through virtual telephony masking proxies (e.g., Exotel/Twilio).
   - Detailed passenger addresses are limited strictly to active trip execution and masked in historical order receipts.

---

## 5. Data Validation & Integrity Rules

1. **Integer Money Representation:**
   - All financial attributes (`amount_minor`, `quoted_base_amount_minor`, `gross_amount_minor`, `net_amount_minor`) use `BIGINT` representing integer paise (1 INR = 100 paise).
   - Floating-point arithmetic (`Float`, `Double`) is strictly prohibited in monetary pipelines.
2. **Double-Entry Ledger Balancing:**
   - Every `ledger_transactions` record must have corresponding balanced `ledger_entries`:
     $$\sum_{\text{direction} = \text{'DEBIT'}} \text{amount\_minor} = \sum_{\text{direction} = \text{'CREDIT'}} \text{amount\_minor}$$
   - Posted ledger records are permanent and append-only. Adjustments and reversals require new compensatory postings.
3. **Idempotency Guarantees:**
   - Mutating endpoints (`/api/v1/offers/{id}/accept`, `/api/v1/trips/{id}/events`, `/api/v1/payouts`) require unique `Idempotency-Key` headers.
   - Database tables `trip_events`, `ledger_transactions`, `payouts`, and `provider_webhook_events` enforce `UNIQUE` constraints on their respective idempotency/source keys.

---

## 6. Open Schema Decisions Catalog

The following technical schema decisions are recorded for operational and infrastructure alignment:

| Decision ID | Area | Context & Open Question | Recommendation / Baseline |
|---|---|---|---|
| **SCH-DEC-01** | Vehicle Uniqueness | Can a single vehicle registration number be registered to multiple drivers (e.g., shared fleet vehicle / shift drivers)? | For MVP, enforce unique active vehicle registration per category. Support multi-driver vehicle linking in Phase 2. |
| **SCH-DEC-02** | Location History Partitioning | How long should high-frequency `driver_location_events` be retained before automated archiving/deletion? | Retain raw points for 30 days for safety/incident investigations, then aggregate or prune to comply with privacy laws. |
| **SCH-DEC-03** | Fare Rule Versioning Storage | Should dynamic pricing and surge multipliers be stored as relational columns or flexible JSONB? | Use `fare_configs` with relational base/per-km/per-min rates and JSONB `config` for specialized surcharge brackets. |
| **SCH-DEC-04** | Payout Account Verification | Which bank account verification method will be deployed (Penny drop IMPS verification vs. UPI VPA validation)? | Integrate automated penny-drop validation before activating `payout_accounts` for automated settlements. |
| **SCH-DEC-05** | Real-Time Presence Cache | Should driver presence be stored directly in PostgreSQL or mirrored in Redis? | Baseline PostgreSQL with PostGIS indexing. Introduce Redis presence layer once active driver concurrency exceeds 5,000. |
