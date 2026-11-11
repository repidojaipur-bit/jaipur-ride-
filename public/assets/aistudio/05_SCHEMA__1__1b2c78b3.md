# Database Schema — Drive Partner

**Document ID:** DP-DB-001 | **Version:** 1.0 | **Database:** PostgreSQL + PostGIS

## 1. Data principles
- PostgreSQL is transactional source of truth.
- UUID/opaque primary keys; UTC timestamps.
- Money is integer minor units (INR paise) plus ISO currency code.
- State transitions are enforced by backend domain logic and database constraints.
- Ledger records are append-only; corrections use reversals/adjustments.
- File bytes live in private object storage; SQL stores metadata/object keys.
- Minimize PII. Do not store raw Aadhaar by default; use a compliant verification provider/reference and masked values.
- Retention/deletion policy requires legal and operational review.

## 2. Baseline enums (illustrative SQL)
```sql
CREATE TYPE account_status AS ENUM ('APPLICANT','PENDING_REVIEW','ACTIVE','SUSPENDED','REJECTED','CLOSED');
CREATE TYPE service_category AS ENUM ('BIKE','AUTO','CAB','DELIVERY');
CREATE TYPE document_status AS ENUM ('NOT_SUBMITTED','UPLOADED','UNDER_REVIEW','APPROVED','REJECTED','EXPIRED');
CREATE TYPE duty_status AS ENUM ('OFFLINE','GOING_ONLINE','ONLINE','GOING_OFFLINE','BLOCKED');
CREATE TYPE offer_status AS ENUM ('CREATED','OFFERED','ACCEPTED','REJECTED','EXPIRED','WITHDRAWN');
CREATE TYPE trip_status AS ENUM ('ASSIGNED','EN_ROUTE_TO_PICKUP','ARRIVED_AT_PICKUP','IN_PROGRESS','COMPLETED','CANCELLED','DISPUTED');
CREATE TYPE payout_status AS ENUM ('REQUESTED','PROCESSING','PAID','FAILED','CANCELLED','REVERSED');
CREATE TYPE ticket_status AS ENUM ('OPEN','IN_PROGRESS','WAITING_FOR_DRIVER','RESOLVED','CLOSED');
```
Enums may be replaced with normalized lookup/state tables if required for future evolution.

## 3. Identity and driver

### `users`
`id UUID PK`, `firebase_uid VARCHAR UNIQUE NOT NULL`, `phone_e164 VARCHAR NOT NULL` (restricted/encrypted or tokenized as appropriate), `display_name VARCHAR`, `preferred_language VARCHAR(10)`, `status account_status`, `created_at`, `updated_at`, `last_login_at TIMESTAMPTZ`.

### `drivers`
`id UUID PK`, `user_id UUID UNIQUE FK users`, `driver_code VARCHAR UNIQUE`, `date_of_birth DATE NULL` (only if required), `gender VARCHAR NULL` (optional), address fields (restricted PII), `profile_photo_object_key VARCHAR`, `verification_status VARCHAR`, `activation_status VARCHAR`, `created_at`, `updated_at`.

### `vehicles`
`id UUID PK`, `driver_id UUID FK drivers`, `category service_category`, `registration_number_normalized VARCHAR`, `registration_number_masked VARCHAR`, `make`, `model`, `color`, `year SMALLINT`, `status VARCHAR`, timestamps. Add a jurisdiction-appropriate uniqueness rule for active vehicle registrations.

### `driver_category_permissions`
`driver_id UUID FK`, `category service_category`, `enabled BOOLEAN`, `reason_code VARCHAR`, `valid_from/valid_until TIMESTAMPTZ`, composite PK `(driver_id, category)`.

## 4. Documents and verification

### `driver_documents`
`id UUID PK`, `driver_id UUID FK`, `vehicle_id UUID NULL FK`, `document_type VARCHAR`, `object_key VARCHAR`, `mime_type VARCHAR`, `file_size_bytes BIGINT`, `checksum VARCHAR`, `masked_reference VARCHAR`, `status document_status`, `issued_on DATE`, `expires_on DATE`, `submitted_at TIMESTAMPTZ`, `reviewed_by UUID`, `reviewed_at TIMESTAMPTZ`, `rejection_reason_code VARCHAR`, timestamps.

### `verification_events`
`id UUID PK`, `document_id UUID FK`, `actor_user_id UUID NULL`, `from_status VARCHAR`, `to_status VARCHAR`, `reason_code VARCHAR`, `notes_restricted TEXT`, `created_at TIMESTAMPTZ`.

## 5. Service areas and location

### `service_zones`
`id UUID PK`, `name VARCHAR`, `city_code VARCHAR`, `boundary GEOGRAPHY(MULTIPOLYGON,4326)` (or reviewed geometry equivalent), `enabled BOOLEAN`, `config JSONB`, timestamps.

### `driver_zone_preferences`
`driver_id UUID FK`, `zone_id UUID FK`, `enabled BOOLEAN`, timestamps, composite PK `(driver_id, zone_id)`.

### `driver_presence`
`driver_id UUID PK FK drivers`, `status duty_status`, `active_category service_category NULL`, `last_location GEOGRAPHY(POINT,4326) NULL`, `location_accuracy_m REAL NULL`, `location_recorded_at TIMESTAMPTZ NULL`, `last_heartbeat_at TIMESTAMPTZ NULL`, `session_id UUID NULL`, `updated_at TIMESTAMPTZ`.

`driver_presence` is current state, not a permanent location trail.

### `driver_location_events` (optional, policy-gated)
`id UUID PK`, `driver_id UUID FK`, `trip_id UUID NULL`, `point GEOGRAPHY(POINT,4326)`, `accuracy_m REAL`, `speed_mps REAL NULL`, `bearing_deg REAL NULL`, `recorded_at`, `received_at TIMESTAMPTZ`. Enable only when needed; partition/expire under a reviewed retention policy.

## 6. Orders, offers and trips

### `orders`
`id UUID PK`, `public_order_code VARCHAR UNIQUE`, `service_category service_category`, `status VARCHAR`, `pickup_label VARCHAR`, `pickup_point GEOGRAPHY(POINT,4326)`, `drop_label VARCHAR`, `drop_point GEOGRAPHY(POINT,4326)`, `estimated_distance_m INTEGER`, `estimated_duration_s INTEGER`, `fare_quote_id UUID NULL`, `assigned_driver_id UUID NULL FK drivers`, `assigned_at TIMESTAMPTZ`, timestamps and `completed_at`.

Customer identity/contact fields belong in separately protected records or a masking-provider integration. Do not expose customer PII in broad driver lists.

### `order_offers`
`id UUID PK`, `order_id UUID FK`, `driver_id UUID FK`, `status offer_status`, `offer_sequence INTEGER`, `expires_at TIMESTAMPTZ`, `quoted_base_amount_minor BIGINT`, `quoted_incentive_amount_minor BIGINT DEFAULT 0`, `currency CHAR(3) DEFAULT 'INR'`, `quote_snapshot JSONB`, `responded_at`, `created_at`. Unique `(order_id, driver_id, offer_sequence)`; indexes `(driver_id,status,expires_at)` and `(order_id,status)`.

### `trips`
`id UUID PK`, `order_id UUID UNIQUE FK`, `driver_id UUID FK`, `status trip_status`, `accepted_at`, `en_route_at`, `arrived_at`, `started_at`, `completed_at`, `cancelled_at TIMESTAMPTZ`, `fare_version_id UUID NULL`, `final_amount_minor BIGINT NULL`, `currency CHAR(3)`, `cancellation_reason_code VARCHAR NULL`, timestamps.

### `trip_events`
`id UUID PK`, `trip_id UUID FK`, `event_type VARCHAR`, `from_status VARCHAR NULL`, `to_status VARCHAR NULL`, `actor_type VARCHAR`, `actor_id UUID NULL`, `idempotency_key VARCHAR NULL`, `metadata JSONB`, `created_at TIMESTAMPTZ`. Unique `(trip_id,idempotency_key)` when key exists.

## 7. Pricing and earnings

### `fare_configs`
`id UUID PK`, `category service_category`, `zone_id UUID NULL FK`, `version VARCHAR`, `currency CHAR(3)`, `effective_from/effective_until TIMESTAMPTZ`, `config JSONB` (base, distance, time, minimum, waiting, cancellation, tax and approved components), `status VARCHAR`, `created_at`, `created_by`.

### `fare_quotes`
`id UUID PK`, `order_id UUID NULL FK`, `driver_id UUID NULL FK`, `fare_config_id UUID FK`, `estimated_amount_minor BIGINT`, `currency CHAR(3)`, `breakdown JSONB`, `expires_at TIMESTAMPTZ NULL`, `created_at`.

### `earning_statements`
`id UUID PK`, `driver_id UUID FK`, `trip_id UUID NULL FK`, `source_type VARCHAR`, `source_id UUID`, `gross_amount_minor BIGINT`, `adjustment_amount_minor BIGINT DEFAULT 0`, `net_amount_minor BIGINT`, `currency CHAR(3)`, `status VARCHAR`, `calculation_snapshot JSONB`, `created_at`. Add a unique source/driver constraint to prevent duplicate statements.

## 8. Wallet, ledger and payout

### `wallet_accounts`
`id UUID PK`, `driver_id UUID UNIQUE FK`, `currency CHAR(3)`, `status VARCHAR`, timestamps.

### `ledger_transactions`
`id UUID PK`, `source_type VARCHAR`, `source_id UUID`, `idempotency_key VARCHAR UNIQUE`, `description VARCHAR`, `status VARCHAR`, `created_at TIMESTAMPTZ`. Add unique `(source_type,source_id)` where one posting is permitted.

### `ledger_entries`
`id UUID PK`, `transaction_id UUID FK`, `account_id UUID` (driver wallet or platform/clearing account), `direction VARCHAR CHECK IN ('DEBIT','CREDIT')`, `amount_minor BIGINT CHECK > 0`, `currency CHAR(3)`, `created_at`. Use double-entry or a formally reconciled ledger; entries for each transaction/currency must balance. Never update/delete posted entries.

### `payout_accounts`
`id UUID PK`, `driver_id UUID FK`, `provider_token VARCHAR` (not raw bank credential), `account_holder_masked VARCHAR`, `bank_name VARCHAR NULL`, `account_last4 VARCHAR NULL`, `ifsc_masked VARCHAR NULL`, `verification_status VARCHAR`, `is_default BOOLEAN`, timestamps.

### `payouts`
`id UUID PK`, `driver_id UUID FK`, `payout_account_id UUID FK`, `amount_minor BIGINT CHECK > 0`, `currency CHAR(3)`, `status payout_status`, `provider_reference VARCHAR NULL`, `idempotency_key VARCHAR UNIQUE`, `failure_code VARCHAR NULL`, `requested_at`, `processed_at`, `settled_at TIMESTAMPTZ`, timestamps.

### `provider_webhook_events`
`id UUID PK`, `provider VARCHAR`, `provider_event_id VARCHAR`, `signature_valid BOOLEAN`, `payload_hash VARCHAR`, `processed_at TIMESTAMPTZ`, `created_at`. Unique `(provider,provider_event_id)`.

## 9. Campaigns and referrals

### `incentive_campaigns`
`id UUID PK`, `name VARCHAR`, `category service_category NULL`, `zone_id UUID NULL`, `rules JSONB`, `reward_config JSONB`, `starts_at/ends_at TIMESTAMPTZ`, `status VARCHAR`, `terms_version VARCHAR`, timestamps.

### `driver_campaign_progress`
`id UUID PK`, `campaign_id UUID FK`, `driver_id UUID FK`, `progress JSONB`, `eligibility_status VARCHAR`, `evaluated_at TIMESTAMPTZ`, unique `(campaign_id,driver_id)`.

### `driver_rewards`
`id UUID PK`, `driver_id UUID FK`, `campaign_id UUID NULL FK`, `source_type VARCHAR`, `source_id UUID`, `amount_minor BIGINT`, `currency CHAR(3)`, `status VARCHAR`, `ledger_transaction_id UUID NULL FK`, `created_at`. Unique `(source_type,source_id,driver_id)`.

### `referral_codes`
`id UUID PK`, `owner_driver_id UUID FK`, `code VARCHAR UNIQUE`, `status VARCHAR`, `created_at`, `expires_at TIMESTAMPTZ`.

### `referrals`
`id UUID PK`, `referral_code_id UUID FK`, `referred_user_id UUID NULL FK`, `referred_driver_id UUID NULL FK`, `status VARCHAR`, `qualified_at TIMESTAMPTZ`, `reward_id UUID NULL FK`, timestamps. Add one-attribution and anti-self-referral constraints per approved policy.

## 10. Notifications, support, safety and audit

### `notifications`
`id UUID PK`, `user_id UUID FK`, `type VARCHAR`, `title_key VARCHAR`, `body_key VARCHAR`, `safe_payload JSONB`, `read_at TIMESTAMPTZ NULL`, `created_at TIMESTAMPTZ`.

### `notification_outbox`
`id UUID PK`, `event_type VARCHAR`, `aggregate_id UUID`, `payload JSONB`, `attempt_count INTEGER DEFAULT 0`, `available_at TIMESTAMPTZ`, `sent_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ`.

### `support_tickets`
`id UUID PK`, `driver_id UUID FK`, `category VARCHAR`, `subject VARCHAR`, `description TEXT`, `status ticket_status`, `priority VARCHAR`, timestamps, `resolved_at TIMESTAMPTZ`.

### `support_messages`
`id UUID PK`, `ticket_id UUID FK`, `sender_type VARCHAR`, `sender_id UUID NULL`, `message TEXT`, `attachment_object_key VARCHAR NULL`, `created_at TIMESTAMPTZ`.

### `safety_incidents`
`id UUID PK`, `driver_id UUID FK`, `trip_id UUID NULL FK`, `incident_type VARCHAR`, `status VARCHAR`, `location GEOGRAPHY(POINT,4326) NULL`, `description TEXT NULL`, `created_at`, `acknowledged_at`, `closed_at TIMESTAMPTZ`.

### `audit_logs`
`id UUID PK`, `actor_id UUID NULL`, `actor_role VARCHAR`, `action VARCHAR`, `resource_type VARCHAR`, `resource_id UUID NULL`, `request_id VARCHAR NULL`, `before_hash VARCHAR NULL`, `after_hash VARCHAR NULL`, `metadata JSONB`, `created_at TIMESTAMPTZ`. Never copy raw secrets or full PII into metadata.

## 11. Indexes and constraints
- Unique Firebase UID and normalized registration identifiers as policy permits.
- Orders `(assigned_driver_id,created_at DESC)`.
- Offers `(driver_id,status,expires_at)` and `(order_id,status)`.
- Enforce one active trip per driver via carefully designed partial constraint/transaction.
- GiST indexes on service-zone boundaries/current locations.
- Ledger entries `(account_id,created_at)`.
- Unique idempotency keys for offer actions, payout, reward posting and provider events.
- Foreign keys/check constraints for valid states and positive amounts.

## 12. Retention and migrations
Define retention for precise location, documents, support attachments, notifications and audit logs after legal/operational review. Use partitioning and deletion/anonymization jobs where appropriate. Authorize every file access through backend and short-lived signed access. Schema above is a logical baseline, not production-ready SQL; finalize encryption, partitioning, ledger balancing, data lengths and jurisdictional constraints before release. Use forward migrations only.
