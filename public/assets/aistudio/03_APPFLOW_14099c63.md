# Application Flows — Drive Partner

**Document ID:** DP-FLOW-001 | **Version:** 1.0

## 1. Navigation model
Activated-driver primary destinations: Home, Orders, Earnings, Rewards and Profile. Home contains duty control. Side drawer/secondary navigation contains documents, vehicle, service area, safety, support, language, settings and legal links. Active offer/trip state must remain recoverable and take priority over ordinary navigation.

## 2. First-launch and onboarding
```mermaid
flowchart TD
  A[Install / Launch] --> B[Splash and config check]
  B --> C{Session exists?}
  C -- No --> D[Language]
  D --> E[Phone entry]
  E --> F[OTP verification]
  F --> G[Resolve user and driver]
  C -- Yes --> H[Fetch server account state]
  G --> H
  H --> I{Profile complete?}
  I -- No --> J[Personal details]
  J --> K[Vehicle details]
  K --> L[Required documents]
  L --> M[Submit for review]
  M --> N[Verification pending]
  I -- Yes --> O{Verification approved?}
  N --> H
  O -- No --> P[Show missing/rejected/expired items]
  P --> L
  O -- Yes --> Q{Account active and category enabled?}
  Q -- No --> R[Explain blocked state / support]
  Q -- Yes --> S[Driver Home]
```

Rules: persist draft progress server-side; local draft is not proof of approval. OTP and document verification are real provider/backend flows in Live Mode. Rejected documents show a reason and replacement action. Request Android permissions in context, not all at first launch.

## 3. Duty and availability
```mermaid
flowchart TD
  A[Home: Offline] --> B[Driver taps Go Online]
  B --> C[Check login and account state]
  C --> D{Eligible?}
  D -- No --> E[Show blocking reason]
  D -- Yes --> F{Location permission and GPS ready?}
  F -- No --> G[Explain / request permission]
  G --> F
  F -- Yes --> H[Request online from backend]
  H --> I{Backend confirms?}
  I -- No --> J[Remain offline / error]
  I -- Yes --> K[Start duty location service]
  K --> L[Online / waiting for offer]
  L --> M[Offer, trip or Go Offline]
  M --> N{Active offer or trip?}
  N -- Yes --> O[Resolve under approved policy]
  N -- No --> P[Request backend offline]
  O --> P
  P --> Q[Stop duty tracking and show Offline]
```
If backend is unreachable, show pending/unconfirmed or last confirmed state. Never optimistically claim online. Do not silently abandon active trips during an offline action.

## 4. Offer lifecycle
```mermaid
stateDiagram-v2
  [*] --> Created
  Created --> Offered: dispatch
  Offered --> Accepted: driver accepts before expiry
  Offered --> Rejected: driver rejects
  Offered --> Expired: server expiry
  Offered --> Withdrawn: backend withdraws
  Accepted --> [*]
  Rejected --> [*]
  Expired --> [*]
  Withdrawn --> [*]
```
On an incoming event: deduplicate by event/offer ID; fetch offer from API; verify current driver and actionable status; calculate countdown from server expiry using server-time offset; require explicit accept/reject; refresh state on app resume. Never queue an offline accept.

## 5. Trip lifecycle
```mermaid
stateDiagram-v2
  [*] --> Assigned
  Assigned --> EnRouteToPickup: start navigation / API event
  EnRouteToPickup --> ArrivedAtPickup: driver confirms
  ArrivedAtPickup --> InProgress: permitted start action
  InProgress --> Completed: backend validates
  Assigned --> Cancelled: approved cancellation
  EnRouteToPickup --> Cancelled: approved cancellation
  ArrivedAtPickup --> Cancelled: approved cancellation
  InProgress --> Disputed: exceptional issue
  Completed --> [*]
  Cancelled --> [*]
  Disputed --> [*]
```
The API contract must define the exact persisted states. Any proximity/customer checks are backend policy, not fabricated client checks.

## 6. Trip-screen action matrix
| State | Main action | Secondary actions | Server result |
|---|---|---|---|
| Assigned | Start navigation | Help/contact if configured | Remains assigned or transitions per API |
| En-route | Mark arrived | Navigate, help, contact | Arrival event |
| Arrived | Start trip | Help/cancel if permitted | Trip started |
| In progress | Complete trip | SOS/support | Completion validation and final fare |
| Completed | View receipt | Report issue if feature exists | Read-only receipt |

## 7. Order history and earnings
```mermaid
flowchart TD
  A[Orders tab] --> B[All / Completed / Cancelled]
  B --> C[Search or date filter]
  C --> D[Order detail]
  D --> E[Receipt and final earning breakdown]
  F[Earnings tab] --> G[Today / Week / Month]
  G --> H[Breakdown by type]
  H --> I[Transaction detail]
  I --> J[Wallet / payout status]
```
Displayed totals are backend-derived. Client may format returned values but must not create final accounting records.

## 8. Wallet and payout
1. Fetch ledger-derived available/pending/settled summary.
2. Driver enters amount and confirms approved masked payout account.
3. Client sends request with an idempotency key.
4. Backend validates available balance, limits, account status and pending requests.
5. Backend creates a `Requested`/`Processing` payout.
6. Verified provider callback/reconciliation updates status.
7. Show Paid only after settlement evidence; show Failed with safe reason/next step otherwise.

## 9. Document flow
```mermaid
flowchart TD
  A[Documents] --> B[Choose document]
  B --> C[Capture / Select file]
  C --> D[Preview and validate]
  D --> E[Upload to private storage]
  E --> F[Register metadata]
  F --> G[Under Review]
  G --> H{Reviewer result}
  H -- Approved --> I[Approved / expiry tracked]
  H -- Rejected --> J[Reason and replace]
  H -- More information --> K[Request evidence]
  J --> B
  K --> B
```

## 10. Rewards and referrals
Fetch applicable campaigns from backend with qualification, progress, valid dates and terms version. Work events trigger server evaluation; client never awards a reward. Referral code is generated server-side; qualification applies approved anti-fraud rules. Every reward credit has a unique source reference.

## 11. Safety and support
SOS uses deliberate confirmation/hold interaction while remaining accessible in emergencies. Send minimum necessary incident/trip context. If network is unavailable, explicitly state that the SOS request is not confirmed and expose configured emergency alternatives where approved. Never state help was contacted without acknowledgement. Ticket creation returns a ticket ID and status.

## 12. Failure and recovery
- **Network lost during offer:** offer remains server-timed; on reconnect fetch status; expired offers are non-actionable.
- **Network lost during trip:** preserve last known state, visibly mark unsynced actions, reconcile before next irreversible action; queue only explicitly designed idempotent events.
- **Process killed:** fetch session, driver state, active offer and active trip from server; don't trust local booleans.
- **Location unavailable:** show permission/GPS guidance; block online when live location is required; never use stale point as current.
- **Duplicate push/realtime:** deduplicate and fetch current resource.

## 13. Navigation guards
An active offer remains reachable until resolved; an active trip is recoverable from any entry point; payout/document submissions resist double taps; logout during active work follows a safe backend-confirmed policy.
