-- Drive Partner Forward Migration 001
-- Source of Truth: docs/05_SCHEMA (1).md (DP-DB-001)
-- Forward-only schema migration for PostgreSQL 16 + PostGIS 3.4+

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Baseline Enums (05_SCHEMA Section 2)
DO $$ BEGIN
    CREATE TYPE account_status AS ENUM ('APPLICANT', 'PENDING_REVIEW', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE service_category AS ENUM ('BIKE', 'AUTO', 'CAB', 'DELIVERY');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE document_status AS ENUM ('NOT_SUBMITTED', 'UPLOADED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE duty_status AS ENUM ('OFFLINE', 'GOING_ONLINE', 'ONLINE', 'GOING_OFFLINE', 'BLOCKED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE offer_status AS ENUM ('CREATED', 'OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE trip_status AS ENUM ('ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE payout_status AS ENUM ('REQUESTED', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED', 'REVERSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE ticket_status AS ENUM ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_DRIVER', 'RESOLVED', 'CLOSED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

-- 2. Identity and Driver (05_SCHEMA Section 3)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) UNIQUE NOT NULL,
    phone_e164 VARCHAR(20) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    display_name VARCHAR(120),
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'en',
    status account_status NOT NULL DEFAULT 'APPLICANT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    driver_code VARCHAR(32) UNIQUE NOT NULL,
    full_name VARCHAR(120) NOT NULL DEFAULT '',
    city_code VARCHAR(16) NOT NULL DEFAULT '',
    date_of_birth DATE NULL,
    gender VARCHAR(24) NULL,
    address_line VARCHAR(255) NULL,
    state_code VARCHAR(16) NULL,
    pin_code VARCHAR(10) NULL,
    profile_photo_object_key VARCHAR(512) NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'APPLICANT',
    activation_status account_status NOT NULL DEFAULT 'APPLICANT',
    documents_verified BOOLEAN NOT NULL DEFAULT FALSE,
    auto_boost_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    active_service_category service_category NOT NULL DEFAULT 'BIKE',
    rating REAL NULL,
    total_completed_trips INTEGER NOT NULL DEFAULT 0,
    acceptance_rate REAL NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    category service_category NOT NULL,
    registration_number_normalized VARCHAR(24) NOT NULL,
    registration_number_masked VARCHAR(32) NOT NULL,
    make VARCHAR(64) NOT NULL DEFAULT '',
    model VARCHAR(64) NOT NULL DEFAULT '',
    color VARCHAR(32) NOT NULL DEFAULT '',
    year SMALLINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    vehicle_photo_object_key VARCHAR(512) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_vehicles_active_reg_unique
    ON vehicles (registration_number_normalized)
    WHERE status <> 'RETIRED';

CREATE TABLE IF NOT EXISTS driver_category_permissions (
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    category service_category NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(64) NULL,
    valid_from TIMESTAMPTZ NULL,
    valid_until TIMESTAMPTZ NULL,
    PRIMARY KEY (driver_id, category)
);

-- 3. Documents and Verification (05_SCHEMA Section 4)
CREATE TABLE IF NOT EXISTS driver_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    vehicle_id UUID NULL REFERENCES vehicles(id) ON DELETE SET NULL,
    document_type VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL DEFAULT '',
    mime_type VARCHAR(64) NOT NULL DEFAULT 'image/jpeg',
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    checksum VARCHAR(128) NULL,
    masked_reference VARCHAR(64) NULL,
    status document_status NOT NULL DEFAULT 'NOT_SUBMITTED',
    issued_on DATE NULL,
    expires_on DATE NULL,
    submitted_at TIMESTAMPTZ NULL,
    reviewed_by UUID NULL,
    reviewed_at TIMESTAMPTZ NULL,
    rejection_reason_code VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (driver_id, document_type),
    CONSTRAINT chk_object_key_not_public_url CHECK (
        object_key = '' OR (object_key NOT LIKE 'http://%' AND object_key NOT LIKE 'https://%')
    ),
    CONSTRAINT chk_masked_ref_no_raw_aadhaar CHECK (
        masked_reference IS NULL OR masked_reference !~ '\d{4}[[:space:]-]?\d{4}[[:space:]-]?\d{4}'
    )
);

CREATE TABLE IF NOT EXISTS verification_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES driver_documents(id) ON DELETE CASCADE,
    actor_user_id UUID NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128) NULL,
    notes_restricted TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Service Areas & Live Presence (05_SCHEMA Section 5)
CREATE TABLE IF NOT EXISTS service_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_code VARCHAR(64) UNIQUE NOT NULL,
    name VARCHAR(128) NOT NULL,
    city_code VARCHAR(16) NOT NULL,
    boundary GEOGRAPHY(MULTIPOLYGON, 4326) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_service_zones_boundary_gist ON service_zones USING GIST (boundary);

CREATE TABLE IF NOT EXISTS driver_zone_preferences (
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    zone_id UUID NOT NULL REFERENCES service_zones(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (driver_id, zone_id)
);

CREATE TABLE IF NOT EXISTS driver_presence (
    driver_id UUID PRIMARY KEY REFERENCES drivers(id) ON DELETE CASCADE,
    status duty_status NOT NULL DEFAULT 'OFFLINE',
    active_category service_category NULL,
    last_location GEOGRAPHY(POINT, 4326) NULL,
    location_accuracy_m REAL NULL,
    location_recorded_at TIMESTAMPTZ NULL,
    last_heartbeat_at TIMESTAMPTZ NULL,
    session_id UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_driver_presence_location_gist ON driver_presence USING GIST (last_location);

CREATE TABLE IF NOT EXISTS driver_location_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    trip_id UUID NULL,
    point GEOGRAPHY(POINT, 4326) NOT NULL,
    accuracy_m REAL NOT NULL,
    speed_mps REAL NULL,
    bearing_deg REAL NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. Pricing, Orders, Offers & Trips (05_SCHEMA Sections 6 & 7)
CREATE TABLE IF NOT EXISTS fare_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category service_category NOT NULL,
    zone_id UUID NULL REFERENCES service_zones(id) ON DELETE SET NULL,
    version VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_until TIMESTAMPTZ NULL,
    config JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_order_code VARCHAR(32) UNIQUE NOT NULL,
    service_category service_category NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_DISPATCH',
    pickup_label VARCHAR(160) NOT NULL,
    pickup_point GEOGRAPHY(POINT, 4326) NOT NULL,
    drop_label VARCHAR(160) NOT NULL,
    drop_point GEOGRAPHY(POINT, 4326) NOT NULL,
    estimated_distance_m INTEGER NOT NULL CHECK (estimated_distance_m >= 0),
    estimated_duration_s INTEGER NOT NULL CHECK (estimated_duration_s >= 0),
    fare_quote_id UUID NULL,
    assigned_driver_id UUID NULL REFERENCES drivers(id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_assigned_driver_created
    ON orders (assigned_driver_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fare_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NULL REFERENCES orders(id) ON DELETE CASCADE,
    driver_id UUID NULL REFERENCES drivers(id) ON DELETE SET NULL,
    fare_config_id UUID NOT NULL REFERENCES fare_configs(id),
    estimated_amount_minor BIGINT NOT NULL CHECK (estimated_amount_minor >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    breakdown JSONB NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    status offer_status NOT NULL DEFAULT 'OFFERED',
    offer_sequence INTEGER NOT NULL DEFAULT 1,
    priority_label VARCHAR(64) NULL,
    pickup_distance_m INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    quoted_base_amount_minor BIGINT NOT NULL CHECK (quoted_base_amount_minor >= 0),
    quoted_incentive_amount_minor BIGINT NOT NULL DEFAULT 0 CHECK (quoted_incentive_amount_minor >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    quote_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(128) NULL,
    responded_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (order_id, driver_id, offer_sequence)
);

CREATE INDEX IF NOT EXISTS idx_order_offers_driver_status_exp
    ON order_offers (driver_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_order_offers_order_status
    ON order_offers (order_id, status);

CREATE TABLE IF NOT EXISTS trips (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID UNIQUE NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    status trip_status NOT NULL DEFAULT 'ASSIGNED',
    version BIGINT NOT NULL DEFAULT 1,
    requires_start_pin BOOLEAN NOT NULL DEFAULT FALSE,
    start_pin_hash VARCHAR(128) NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    en_route_at TIMESTAMPTZ NULL,
    arrived_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    fare_version_id UUID NULL REFERENCES fare_configs(id),
    final_amount_minor BIGINT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    cancellation_reason_code VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Enforce strict single-active-trip invariant per driver at DB level (05_SCHEMA Section 11)
CREATE UNIQUE INDEX IF NOT EXISTS idx_trips_one_active_per_driver
    ON trips (driver_id)
    WHERE status IN ('ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'IN_PROGRESS');

CREATE TABLE IF NOT EXISTS trip_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id UUID NULL,
    idempotency_key VARCHAR(128) NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_trip_events_idempotency
    ON trip_events (trip_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS earning_statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    trip_id UUID NULL REFERENCES trips(id) ON DELETE SET NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    fare_config_version VARCHAR(32) NOT NULL,
    quote_snapshot_id UUID NULL,
    base_fare_minor BIGINT NOT NULL DEFAULT 0,
    distance_fare_minor BIGINT NOT NULL DEFAULT 0,
    time_fare_minor BIGINT NOT NULL DEFAULT 0,
    incentive_minor BIGINT NOT NULL DEFAULT 0,
    platform_fee_minor BIGINT NOT NULL DEFAULT 0,
    tax_minor BIGINT NOT NULL DEFAULT 0,
    gross_amount_minor BIGINT NOT NULL,
    adjustment_amount_minor BIGINT NOT NULL DEFAULT 0,
    net_amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'FINALIZED',
    calculation_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (driver_id, source_type, source_id)
);

-- 6. Wallet, Double-Entry Append-Only Ledger & Payouts (05_SCHEMA Section 8)
CREATE TABLE IF NOT EXISTS wallet_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID UNIQUE NULL REFERENCES drivers(id) ON DELETE CASCADE,
    account_kind VARCHAR(32) NOT NULL DEFAULT 'DRIVER_WALLET',
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ledger_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    description VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'POSTED',
    statement_id UUID NULL REFERENCES earning_statements(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, source_id)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES wallet_accounts(id) ON DELETE RESTRICT,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_created
    ON ledger_entries (account_id, created_at DESC);

CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are strictly append-only and cannot be modified or deleted.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ledger_entries_immutable ON ledger_entries;
CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TABLE IF NOT EXISTS payout_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    provider_token VARCHAR(128) UNIQUE NOT NULL,
    account_holder_masked VARCHAR(128) NOT NULL,
    bank_name VARCHAR(128) NULL,
    account_last4 VARCHAR(8) NOT NULL,
    ifsc_code VARCHAR(16) NOT NULL,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'VERIFIED',
    is_default BOOLEAN NOT NULL DEFAULT TRUE,
    idempotency_key VARCHAR(128) UNIQUE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    payout_account_id UUID NOT NULL REFERENCES payout_accounts(id) ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status payout_status NOT NULL DEFAULT 'REQUESTED',
    provider_reference VARCHAR(128) NULL,
    idempotency_key VARCHAR(128) UNIQUE NOT NULL,
    failure_code VARCHAR(128) NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ NULL,
    settled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS provider_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_event_id)
);

-- 7. Campaigns & Referrals (05_SCHEMA Section 9)
CREATE TABLE IF NOT EXISTS incentive_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(160) NOT NULL,
    category VARCHAR(64) NOT NULL DEFAULT 'DAILY_INCENTIVE',
    service_category service_category NULL,
    zone_id UUID NULL REFERENCES service_zones(id),
    rules JSONB NOT NULL,
    reward_config JSONB NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    terms_version VARCHAR(32) NOT NULL,
    terms_summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS driver_campaign_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES incentive_campaigns(id) ON DELETE CASCADE,
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    progress JSONB NOT NULL DEFAULT '{"currentCount":0,"isQualified":false}'::jsonb,
    eligibility_status VARCHAR(32) NOT NULL DEFAULT 'ELIGIBLE',
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (campaign_id, driver_id)
);

CREATE TABLE IF NOT EXISTS driver_rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    campaign_id UUID NULL REFERENCES incentive_campaigns(id),
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(32) NOT NULL DEFAULT 'CREDITED',
    ledger_transaction_id UUID NULL REFERENCES ledger_transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_type, source_id, driver_id)
);

CREATE TABLE IF NOT EXISTS referral_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_driver_id UUID UNIQUE NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    code VARCHAR(32) UNIQUE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referral_code_id UUID NOT NULL REFERENCES referral_codes(id) ON DELETE CASCADE,
    referred_user_id UUID NULL REFERENCES users(id),
    referred_driver_id UUID UNIQUE NULL REFERENCES drivers(id),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    qualification_summary VARCHAR(255) NOT NULL DEFAULT 'Awaiting qualifying completed trips',
    qualified_at TIMESTAMPTZ NULL,
    reward_id UUID NULL REFERENCES driver_rewards(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. Notifications, Support, Safety & Audit Logs (05_SCHEMA Section 10)
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dedupe_key VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    title_key VARCHAR(160) NOT NULL,
    body_key VARCHAR(512) NOT NULL,
    deep_link_route VARCHAR(64) NULL,
    safe_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    read_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, dedupe_key)
);

CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    order_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    earnings_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    campaign_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    category VARCHAR(64) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status ticket_status NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    idempotency_key VARCHAR(128) UNIQUE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS support_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    sender_type VARCHAR(32) NOT NULL,
    sender_id UUID NULL,
    message TEXT NOT NULL,
    attachment_object_key VARCHAR(512) NULL,
    is_internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(128) UNIQUE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS emergency_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    phone_e164 VARCHAR(20) NOT NULL,
    phone_masked VARCHAR(32) NOT NULL,
    relationship VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS safety_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    trip_id UUID NULL REFERENCES trips(id) ON DELETE SET NULL,
    incident_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    responder_reference VARCHAR(128) NULL,
    location GEOGRAPHY(POINT, 4326) NULL,
    description TEXT NULL,
    idempotency_key VARCHAR(128) UNIQUE NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ NULL,
    closed_at TIMESTAMPTZ NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NULL,
    request_id VARCHAR(128) NULL,
    before_hash VARCHAR(128) NULL,
    after_hash VARCHAR(128) NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
