import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { describe, it } from "node:test";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

describe("PostgreSQL + PostGIS Forward Migration Schema Checks", () => {
  it("defines all DP-DB-001 enums, spatial GiST indexes, single-active-trip partial index, and immutable ledger trigger", async () => {
    const sqlPath = path.resolve(__dirname, "../migrations/001_initial_schema.sql");
    const sql = await readFile(sqlPath, "utf8");

    const expectedSnippets = [
      "CREATE EXTENSION IF NOT EXISTS postgis",
      "CREATE TYPE account_status AS ENUM ('APPLICANT', 'PENDING_REVIEW', 'ACTIVE', 'SUSPENDED', 'REJECTED', 'CLOSED')",
      "CREATE TYPE service_category AS ENUM ('BIKE', 'AUTO', 'CAB', 'DELIVERY')",
      "CREATE TYPE document_status AS ENUM ('NOT_SUBMITTED', 'UPLOADED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED')",
      "CREATE TYPE duty_status AS ENUM ('OFFLINE', 'GOING_ONLINE', 'ONLINE', 'GOING_OFFLINE', 'BLOCKED')",
      "CREATE TYPE offer_status AS ENUM ('CREATED', 'OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')",
      "CREATE TYPE trip_status AS ENUM ('ASSIGNED', 'EN_ROUTE_TO_PICKUP', 'ARRIVED_AT_PICKUP', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'DISPUTED')",
      "CREATE TYPE payout_status AS ENUM ('REQUESTED', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED', 'REVERSED')",
      "CREATE TYPE ticket_status AS ENUM ('OPEN', 'IN_PROGRESS', 'WAITING_FOR_DRIVER', 'RESOLVED', 'CLOSED')",
      "idx_trips_one_active_per_driver",
      "trg_ledger_entries_immutable",
      "chk_object_key_not_public_url",
      "chk_masked_ref_no_raw_aadhaar",
      "USING GIST (boundary)",
      "USING GIST (last_location)"
    ];

    for (const snippet of expectedSnippets) {
      assert.equal(sql.includes(snippet), true, `Expected SQL migration to include: ${snippet}`);
    }
  });

  it("defines Supabase auth_uid, insurance_records, admin_settings, and PostgreSQL RLS policies in 002", async () => {
    const sql002Path = path.resolve(__dirname, "../migrations/002_supabase_auth_and_rls.sql");
    const sql002 = await readFile(sql002Path, "utf8");

    const expected002 = [
      "ADD COLUMN IF NOT EXISTS auth_uid",
      "CREATE TABLE IF NOT EXISTS insurance_records",
      "CREATE TABLE IF NOT EXISTS admin_settings",
      "ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY",
      "CREATE OR REPLACE FUNCTION current_driver_id_for_auth()",
      "p_ticket_messages_driver_visible",
      "is_internal_note = FALSE"
    ];

    for (const snippet of expected002) {
      assert.equal(sql002.includes(snippet), true, `Expected 002 migration to include: ${snippet}`);
    }
  });
});
