# DRIVE PARTNER — Master Project Control Document

**Document ID:** DP-MASTER-001 | **Version:** 1.0 | **Status:** Baseline specification | **Updated:** 11 November 2026  
**Working product name:** Drive Partner (temporary; configurable)  
**Client:** Native Android — Kotlin + Jetpack Compose  
**Initial market assumption:** India; launch city, operating entity and regulatory scope require approval.

## 1. Mission
Build an original, reliable, driver-first Android application through which eligible drivers can register, complete verification, manage work availability, receive genuine trip/delivery offers, complete jobs, inspect earnings, access approved incentives and request settlements.

The product is inspired by common driver-platform workflows shown in the supplied references. It must have its own name, logo, copy, illustrations, information architecture details and visual system. Do not copy Rapido or any competitor's protected brand identity, logo, mascot, artwork, screenshots, exact text, proprietary icons, source code or confusingly similar design.

## 2. Product principles
1. **Driver control:** driver explicitly goes online and explicitly accepts every offer.
2. **Truthful money:** estimated fare, final earning, incentive, available balance and settled amount are different concepts.
3. **Backend authority:** eligibility, availability, dispatch, trip states, fare, rewards, ledger and payout are server-authoritative.
4. **Safety and privacy:** location is purpose-limited; offline drivers are not tracked unless an active trip or approved safety workflow requires it; KYC files are private.
5. **Recoverability:** network loss, process death, duplicate events, retries and expired offers must not corrupt trips or money.
6. **Incremental delivery:** each phase builds, runs and is tested before moving forward.
7. **No fake production behavior:** sample orders, map data, earnings and payouts exist only in isolated, visibly labelled Demo Mode.

## 3. Document map and precedence

Read in this order:
1. `00_MASTER.md` — authority, scope and project control.
2. `PRD.md` — product requirements and acceptance outcomes.
3. `02_Architecture.md` — system boundaries and topology.
4. `04_TECHSPEC.md` — technologies, APIs and implementation constraints.
5. `05_SCHEMA (1).md` — data model.
6. `06_RULES (1).md` — authoritative business rules/state machines.
7. `03_APPFLOW.md` — user journeys and recovery flows.
8. `11_SCREENS.md` + `Design.md` — screen behavior and UI system.
9. `Engineering-Practices.md` — security, code quality and tests.
10. `09_Phases.md`, `07_IMPLEMENTATIONPLAN.md`, `12_PHASE_PROMPTS.md` — build sequence and agent prompts.
11. `08_TRACKER.md` — evidence-based status.
12. `10_memory.md` — durable decisions and handoff notes.

If documents conflict, do not silently choose. Follow this precedence, log the conflict in `10_memory.md` under Open Decisions, and update all impacted docs in one change. Security, privacy, legal compliance and backend financial controls override visual convenience.

## 4. Product scope

### Included
- Phone OTP authentication.
- Driver profile and vehicle onboarding.
- Document upload, review, correction and expiry status.
- Online/offline duty control and location handling.
- Map, service-area preferences and backend-fed demand overlays when available.
- Live offers with expiry, accept/reject and atomic assignment.
- Trip/job lifecycle: accepted, en-route, arrived, started, completed/cancelled.
- Order history and details.
- Earnings summaries, transaction history, wallet and payout requests.
- Configurable incentives, rewards and referral tracking.
- Safety center, SOS/incident workflow and help tickets.
- Push notifications and in-app inbox.
- Back-office capabilities required to operate the app.
- Isolated Demo Mode and Live Mode.

### Supported service categories in the data model
- Bike taxi.
- Auto rickshaw.
- Cab/car.
- Delivery partner.

**Launch recommendation:** start operationally with one or two approved categories (for example Bike and Auto). A category is not launch-ready until its local legal, vehicle, pricing, insurance, dispatch and operations rules are configured.

### Out of scope unless separately approved
Passenger/customer app, merchant app, iOS, lending/credit, insurance underwriting/sales, guaranteed-income products, auto-accept, pooled/multi-stop work, large-scale cash reconciliation and a full multi-city admin product.

## 5. Architecture baseline

| Area | Baseline | Rule |
|---|---|---|
| Android | Kotlin, Jetpack Compose, Material 3, MVVM | Native Android, not a web-only wrapper |
| Backend | TypeScript modular monolith | Domain modules; no premature microservices |
| API/events | Versioned REST + authenticated realtime channel | FCM fallback; API remains authoritative |
| Database | PostgreSQL + PostGIS | Owns transactional order, trip and financial state |
| Cache | Redis optional after measurement | Never the sole ledger or assignment source |
| Identity | Firebase Phone Auth + backend token verification | Backend resolves roles and internal IDs |
| Push | Firebase Cloud Messaging | Minimal payload; fetch details from API |
| Maps | Google Maps SDK and approved APIs | Restricted keys and billing configured |
| Files | Private object storage | No public KYC/document URLs |

Do not use Firestore and PostgreSQL as competing sources of truth for orders or money. A replacement requires a recorded architecture decision with migration and consistency analysis.

## 6. Non-negotiable rules
- Driver can go online only if account, mandatory documents, vehicle, category, zone and location conditions permit it.
- A local switch is not proof of availability. Wait for backend acknowledgement.
- One order can be assigned to at most one driver. Acceptance must be atomic and idempotent.
- Expired, withdrawn, rejected or already assigned offers cannot be accepted.
- MVP allows at most one active trip per driver unless a separately approved workflow is implemented.
- Fare, incentive and rewards are calculated/validated on the backend.
- Wallet is derived from an immutable append-only ledger. Client cannot edit balances or declare payouts successful.
- Never show simulated orders, fabricated demand/surge, invented earnings or unverified insurance in Live Mode.
- Location collection must match the actual user-facing disclosure and Android/Play requirements. Stop duty tracking when offline and no approved active-work purpose remains.

See `06_RULES (1).md` for rule IDs and state machines.

## 7. Environment separation
Maintain isolated `local`, `dev`, `staging` and `production` configuration and data. Demo Mode must never write to production dispatch, KYC, ledger or payout. Never use real driver PII in fixtures, screenshots or automated tests.

## 8. Project-wide Definition of Done
A feature is Done only when it matches PRD/rules/screen docs; handles loading, empty, validation, error, permission and offline states; enforces authorization on the server; protects sensitive data; has critical tests; builds successfully; is verified on a representative device/emulator; and updates documentation/tracker.

## 9. Production release gates
No production release until the following are approved and evidenced:
- Legal operating entity, launch geography, service categories, fare/cancellation/tax/commission model and support process.
- Local transport, labour, tax, consumer, privacy and insurance review by qualified professionals.
- Published privacy policy, terms, location disclosure and data-retention policy.
- Firebase, Maps, storage, backend, monitoring and messaging configured securely.
- KYC review operations and admin roles live.
- Dispatch expiry, duplicate protection, monitoring and operational escalation tested.
- Ledger, payout reconciliation, failure/reversal and audit tested.
- Incident response and account suspension process ready.
- Current Android target SDK, foreground service, location permission and Google Play policy review complete.

## 10. AI coding-agent operating contract
Before coding, inspect the repository, branch, working tree, existing build and current architecture. Preserve working code. Implement only the requested phase. Do not invent credentials, approved policies, live data, payout success or insurance terms. If a dependency is missing, add a safe adapter/configuration boundary and record the blocker. Run real builds/tests, report exact commands/results and update `08_TRACKER.md` and `10_memory.md`. Never claim generated code is production-ready without evidence.

## 11. Open decisions before launch
- Final name, logo, palette and domain.
- Legal operator, launch city/zone and enabled categories.
- Document list, KYC vendor/process and retention rules.
- Fare components, minimum fare, waiting, cancellation, tax and driver fee/commission.
- Cash/digital collection and reconciliation model.
- Payout provider, settlement schedule, limits and account verification.
- Insurance policy/provider and approved coverage wording.
- Customer/order-source integration, contact masking and SOS responder.
- Support channel/hours and escalation contacts.
- Whether Redis is required at launch.

Do not silently invent any of these values.
