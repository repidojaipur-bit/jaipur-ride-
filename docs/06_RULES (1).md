# Business Rules and State Machines — Drive Partner

**Document ID:** DP-RULES-001 | **Version:** 1.0

These rules are authoritative. The UI may prevalidate or explain them, but the backend must enforce them.

## 1. Identity and account
- **AUTH-001:** Only a backend-verified identity token authenticates protected API calls.
- **AUTH-002:** Internal user/driver ID and role are resolved server-side; never trust client-supplied role or driver ID.
- **AUTH-003:** OTP rate limits, expiry and abuse protection are enforced by identity provider/backend.
- **ACC-001:** New registrations begin as Applicant/Pending Review, not Active.
- **ACC-002:** Suspended, rejected or closed accounts cannot go online.
- **ACC-003:** Reactivation requires an authorized, audited operation.

## 2. Verification and vehicle
- **KYC-001:** Required documents are configurable by launch jurisdiction and service category.
- **KYC-002:** Mandatory documents must be approved and unexpired before online eligibility.
- **KYC-003:** Rejection requires reason code and timestamp; replacement retains review history.
- **KYC-004:** Do not collect/store raw Aadhaar by default. Use a compliant verification flow; retain minimum necessary reference/status/masked data unless a reviewed lawful requirement dictates otherwise.
- **KYC-005:** Vehicle category must be enabled for the zone and driver must hold required approvals.
- **KYC-006:** Document review actions are role-restricted and audited.

## 3. Duty and availability
- **DUTY-001:** Online eligibility requires Active account, valid mandatory documents, active vehicle, enabled category/zone and required location readiness.
- **DUTY-002:** Backend is authoritative; client switch remains Pending until acknowledged.
- **DUTY-003:** MVP permits at most one active work session and one active trip per driver.
- **DUTY-004:** A location older than the configured freshness threshold makes a driver ineligible for new dispatch.
- **DUTY-005:** Offline means no new offers. Stop location updates when offline and no active trip/approved need requires them.
- **DUTY-006:** Driver cannot silently abandon an active trip by going offline; apply configured finish/cancel/operations handover policy.
- **DUTY-007:** Service-area preferences narrow eligible zones but never override legal/category/safety eligibility.

## 4. Offers
- **OFFER-001:** Each offer has unique ID, order ID, driver ID, sequence, server expiry and quote snapshot.
- **OFFER-002:** Driver explicitly accepts or rejects. Never auto-accept.
- **OFFER-003:** Accept is valid only if status is Offered, server time is before expiry, driver is eligible/online and order is unassigned.
- **OFFER-004:** Acceptance is a database transaction/conditional update so only one driver wins.
- **OFFER-005:** Accept/reject is idempotent using a request key.
- **OFFER-006:** Duplicate event delivery cannot create duplicate offer UI or assignment.
- **OFFER-007:** Expired, rejected, withdrawn or already assigned offers cannot be accepted.
- **OFFER-008:** Driver sees an estimate and components. Actual final amount may vary only under disclosed fare rules and must be itemized.
- **OFFER-009:** Expose only the order information needed to perform the job.

## 5. Order and trip transitions
| From | To | Authorized condition |
|---|---|---|
| Unassigned | Assigned | Dispatch/atomic acceptance |
| Assigned | En-route to pickup | Assigned driver action/API event |
| En-route | Arrived | Driver action; optional configured location plausibility check |
| Arrived | In progress | Driver action with required checks |
| In progress | Completed | Backend validates completion and pricing facts |
| Eligible pre-start state | Cancelled | Driver/ops/customer under approved cancellation policy |
| In progress | Disputed | Approved incident/dispute path |

- **TRIP-001:** Backend rejects transitions not allowed for current state and actor.
- **TRIP-002:** Every state transition writes an immutable trip event.
- **TRIP-003:** Retries use idempotency keys; duplicate completion cannot create duplicate earnings.
- **TRIP-004:** Active trip is restored from server after app restart/login.
- **TRIP-005:** Cancellation stores reason, actor and policy outcome.
- **TRIP-006:** Route distance/time are estimates unless specifically contracted otherwise.

## 6. Fare and earnings
- **FARE-001:** Backend calculates quote/final fare using versioned effective-dated configuration.
- **FARE-002:** Currency is explicit; INR uses integer paise.
- **FARE-003:** Pre-acceptance amount is labelled Estimated unless approved terms make it guaranteed.
- **FARE-004:** Incentive is separate from base earnings and shown only when eligibility is confirmed.
- **FARE-005:** Final earning statement stores pricing/rule snapshot.
- **FARE-006:** Client cannot submit authoritative final fare.
- **FARE-007:** Adjustments require a reason, authorized actor and ledger posting.

## 7. Wallet and payout
- **WALLET-001:** Balance is derived from posted ledger entries or a transactionally maintained, reconciled projection.
- **WALLET-002:** Client cannot write balances or ledger entries.
- **WALLET-003:** Posted ledger entries are immutable; corrections use reversals/adjustments.
- **WALLET-004:** Every posting has unique source reference/idempotency key.
- **PAY-001:** Payout validates available balance, verified account, configured limits and pending requests.
- **PAY-002:** Payout is Pending/Processing until verified provider response/reconciliation.
- **PAY-003:** Webhooks are signature-verified, deduplicated and replay-safe.
- **PAY-004:** Only confirmed settlement evidence can set Paid/Successful.
- **PAY-005:** Failed/reversed payout has a reason and correct ledger/hold correction.
- **PAY-006:** Demo Mode cannot initiate production payouts.

## 8. Incentives and referrals
- **INC-001:** Campaign has dates, categories/zones, qualification, reward, caps, terms version and status.
- **INC-002:** Eligibility is computed server-side from qualifying events.
- **INC-003:** Reward posts once per qualifying source and driver.
- **INC-004:** UI shows actual conditions, progress basis and eligibility; no unsupported guaranteed-income wording.
- **REF-001:** Referral code is generated server-side and belongs to an eligible referrer.
- **REF-002:** Self-referral, duplicate identity and abuse signals are evaluated by server rules.
- **REF-003:** Reward credits only after qualification and review conditions pass.

## 9. Location and privacy
- **LOC-001:** Collect location only for declared purpose with required notice/permission.
- **LOC-002:** No duty tracking offline unless active trip or reviewed safety need justifies limited collection.
- **LOC-003:** Do not put precise location or identity in push payloads/analytics labels.
- **LOC-004:** Validate freshness and accuracy before dispatch use.
- **LOC-005:** Retention is configured and approved; delete/anonymize after expiry where legally permitted.
- **PRIV-001:** KYC, phone, bank and location access is least-privilege and audited.
- **PRIV-002:** Privacy policy/permission text must match actual processing.

## 10. Safety and support
- **SAFE-001:** Show SOS acknowledgement only after backend/provider confirms receipt.
- **SAFE-002:** If network fails, state request was not confirmed and expose approved alternatives.
- **SAFE-003:** Insurance coverage amount/wording must be backed by a current policy and approved text.
- **SUP-001:** Driver can view only own tickets/messages.
- **SUP-002:** Internal staff notes are never returned through driver APIs.
- **SUP-003:** Ticket status changes are audited.

## 11. Demo/live separation
- **MODE-001:** Demo Mode has synthetic fixtures and persistent DEMO label.
- **MODE-002:** Demo backend/data store cannot mutate production orders, wallets, payouts, referrals or KYC.
- **MODE-003:** Missing Live Mode integration shows unavailable/disabled, never simulated success.
- **MODE-004:** Build flavor, endpoint and data source agree; no client toggle may create a fake/live hybrid.

## 12. Backend-configured values
Service categories, document requirements, zone eligibility, stale-location threshold, offer TTL, pricing, cancellations, incentives, referral qualification, payout limits/schedule and support contacts must be configurable, effective-dated and audited. Never hardcode invented launch policy values in the client.
