-- Drive Partner Forward Migration 002: Supabase Auth, Storage, Missing Domain Tables & RLS
-- Extends 001_initial_schema.sql for Supabase PostgreSQL + PostGIS + Auth + Storage.

-- 1. Decouple users.auth_uid from Firebase while preserving non-destructive compatibility
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_uid VARCHAR(128),
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(32) NOT NULL DEFAULT 'supabase';

UPDATE users
SET auth_uid = COALESCE(auth_uid, firebase_uid)
WHERE auth_uid IS NULL;

ALTER TABLE users
    ALTER COLUMN firebase_uid DROP NOT NULL;

DO $$ BEGIN
    ALTER TABLE users ADD CONSTRAINT uq_users_auth_uid UNIQUE (auth_uid);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE INDEX IF NOT EXISTS idx_users_auth_uid ON users(auth_uid);

-- 2. Additional Section 25 Operational Tables (`insurance_records` & `admin_settings`)
CREATE TABLE IF NOT EXISTS insurance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    provider_name VARCHAR(120) NOT NULL,
    policy_reference VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'UNCONFIRMED', -- UNCONFIRMED | CONFIRMED | EXPIRED | REVOKED
    disclaimer_text TEXT NOT NULL,
    valid_from TIMESTAMPTZ NULL,
    valid_until TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_insurance_records_driver
    ON insurance_records(driver_id, status, valid_until DESC);

CREATE TABLE IF NOT EXISTS admin_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    setting_value JSONB NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO admin_settings (setting_key, setting_value, description)
VALUES
    ('payouts.live_enabled', 'false'::jsonb, 'Controls whether real driver payout withdrawal requests are accepted'),
    ('payouts.limits_minor', '{"minimumPayoutMinor":10000,"maximumPayoutMinor":2500000,"currency":"INR"}'::jsonb, 'Server payout floor/ceiling limits in minor paise'),
    ('safety.sos_responder_enabled', 'false'::jsonb, 'Indicates whether external emergency responder webhook is configured'),
    ('dispatch.offer_ttl_seconds', '30'::jsonb, 'Default dispatch offer invitation expiration window in seconds')
ON CONFLICT (setting_key) DO NOTHING;

-- 3. Private KYC Storage Bucket Metadata Enforcement
-- Storage objects live in Supabase Storage private bucket (`drive-partner-kyc-private`).
-- `chk_object_key_not_public_url` from 001 guarantees no public URLs are written to driver_documents.

-- 4. PostgreSQL Row-Level Security (RLS) for Direct Supabase / PostgREST Defense-in-Depth
-- Note: Fastify server connects with privileged service database role (`postgres`), while
-- Supabase anon/authenticated roles are strictly bounded by `auth.uid()::text`.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE drivers ENABLE ROW LEVEL SECURITY;
ALTER TABLE vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE driver_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE driver_presence ENABLE ROW LEVEL SECURITY;
ALTER TABLE driver_zone_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_offers ENABLE ROW LEVEL SECURITY;
ALTER TABLE trips ENABLE ROW LEVEL SECURITY;
ALTER TABLE earning_statements ENABLE ROW LEVEL SECURITY;
ALTER TABLE wallet_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE payout_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE driver_rewards ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE emergency_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE safety_incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE insurance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_settings ENABLE ROW LEVEL SECURITY;

-- Helper function mapping Supabase JWT `sub` (`auth.uid()`) to internal `drivers.id`
CREATE OR REPLACE FUNCTION current_driver_id_for_auth()
RETURNS UUID
LANGUAGE SQL
STABLE
AS $$
    SELECT d.id
    FROM drivers d
    JOIN users u ON u.id = d.user_id
    WHERE u.auth_uid = NULLIF(current_setting('request.jwt.claim.sub', TRUE), '')
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION current_user_id_for_auth()
RETURNS UUID
LANGUAGE SQL
STABLE
AS $$
    SELECT id
    FROM users
    WHERE auth_uid = NULLIF(current_setting('request.jwt.claim.sub', TRUE), '')
    LIMIT 1;
$$;

DO $$ BEGIN
    CREATE POLICY p_users_self_select ON users FOR SELECT
        USING (auth_uid = NULLIF(current_setting('request.jwt.claim.sub', TRUE), ''));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_drivers_self_select ON drivers FOR SELECT
        USING (id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_vehicles_self_select ON vehicles FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_docs_self_select ON driver_documents FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_presence_self_select ON driver_presence FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_zone_prefs_self_select ON driver_zone_preferences FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_offers_self_select ON order_offers FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_trips_self_select ON trips FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_statements_self_select ON earning_statements FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_wallets_self_select ON wallet_accounts FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_ledger_self_select ON ledger_entries FOR SELECT
        USING (
            account_id IN (
                SELECT id FROM wallet_accounts WHERE driver_id = current_driver_id_for_auth()
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_payout_accounts_self_select ON payout_accounts FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_payouts_self_select ON payouts FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_rewards_self_select ON driver_rewards FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_notifications_self_select ON notifications FOR SELECT
        USING (user_id = current_user_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_notification_prefs_self_select ON notification_preferences FOR SELECT
        USING (user_id = current_user_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_tickets_self_select ON support_tickets FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_ticket_messages_driver_visible ON support_messages FOR SELECT
        USING (
            is_internal_note = FALSE AND
            ticket_id IN (SELECT id FROM support_tickets WHERE driver_id = current_driver_id_for_auth())
        );
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_emergency_contacts_self_select ON emergency_contacts FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_safety_incidents_self_select ON safety_incidents FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE POLICY p_insurance_records_self_select ON insurance_records FOR SELECT
        USING (driver_id = current_driver_id_for_auth());
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
