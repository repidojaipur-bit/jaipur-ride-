import { createHash, randomUUID } from "node:crypto";
import { Pool, PoolClient } from "pg";
import { AppError, ErrorCodes } from "../common/errors.js";
import { assertBalancedDoubleEntry, computeFareBreakdownMinor } from "../common/money.js";
import {
  assertPrivateObjectKey,
  containsRawAadhaarDigits,
  maskBankAccountLast4,
  maskPhone,
  maskVehicleReg,
  normalizeVehicleRegistration
} from "../common/validators.js";
import {
  AccountStatus,
  DocumentStatus,
  DocumentType,
  DutyStatus,
  OfferStatus,
  PayoutStatus,
  ServiceCategory,
  TicketStatus,
  TripStatus
} from "../middleware/auth.js";
import { StorageSignedUploader } from "../modules/storage/supabaseStorage.js";
import {
  AcceptOfferDto,
  ActiveTripDto,
  AdminCreateOrderInput,
  AppNotificationDto,
  ApplyReferralDto,
  AuditLogDto,
  AuthSessionDto,
  BackendRepository,
  CampaignDto,
  ConfirmDocumentInput,
  CreatePayoutAccountInput,
  CreateSupportMessageInput,
  CreateSupportTicketInput,
  DriverPreferencesDto,
  DriverProfileDto,
  DriverProfileInput,
  DutyStateDto,
  DutyStatusInput,
  EarningStatementDto,
  EarningsSummaryDto,
  EligibilityDto,
  EmergencyContactDto,
  EmergencyContactInput,
  HomeSummaryDto,
  IncidentReportDto,
  IncidentReportInput,
  InsuranceStatusDto,
  KycDocumentDto,
  LedgerEntryDto,
  LocationPingInput,
  NotificationPreferencesDto,
  OrderDetailDto,
  OrderHistorySummaryDto,
  PagedDto,
  PayoutAccountDto,
  PayoutDto,
  ReferralSummaryDto,
  RequestPayoutInput,
  ServiceZoneDto,
  SosAlertDto,
  SosTriggerInput,
  SupportMessageDto,
  SupportTicketDto,
  TripTransitionDto,
  TripTransitionInput,
  UploadIntentDto,
  UploadIntentInput,
  VehicleDto,
  VehicleInput,
  WalletSummaryDto,
  WorkOfferDto
} from "./repository.js";

const MANDATORY_DOC_TYPES: DocumentType[] = [
  "DRIVING_LICENSE",
  "VEHICLE_RC",
  "VEHICLE_INSURANCE",
  "PERMIT",
  "PAN_CARD",
  "AADHAAR_COMPLIANT_REF",
  "PROFILE_PHOTO"
];

const VALID_TRIP_TRANSITIONS: Record<TripStatus, TripStatus[]> = {
  ASSIGNED: ["EN_ROUTE_TO_PICKUP", "CANCELLED"],
  EN_ROUTE_TO_PICKUP: ["ARRIVED_AT_PICKUP", "CANCELLED"],
  ARRIVED_AT_PICKUP: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["COMPLETED", "CANCELLED", "DISPUTED"],
  COMPLETED: ["DISPUTED"],
  CANCELLED: [],
  DISPUTED: []
};

export interface PgRepositoryOptions {
  objectStorageBucket: string;
  livePayoutsEnabled: boolean;
  minimumPayoutMinor?: number;
  maximumPayoutMinor?: number;
  storageUploader?: StorageSignedUploader;
}

export class PgBackendRepository implements BackendRepository {
  private readonly minPayoutMinor: number;
  private readonly maxPayoutMinor: number;

  constructor(
    private readonly pool: Pool,
    private readonly opts: PgRepositoryOptions
  ) {
    this.minPayoutMinor = opts.minimumPayoutMinor ?? 10000;   // ₹100.00
    this.maxPayoutMinor = opts.maximumPayoutMinor ?? 2500000; // ₹25,000.00
  }

  private async withTx<T>(fn: (client: PoolClient) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const result = await fn(client);
      await client.query("COMMIT");
      return result;
    } catch (err) {
      await client.query("ROLLBACK");
      throw err;
    } finally {
      client.release();
    }
  }

  private async ensurePlatformClearingAccount(client: PoolClient): Promise<string> {
    const existing = await client.query<{ id: string }>(
      "SELECT id FROM wallet_accounts WHERE account_kind = 'PLATFORM_CLEARING' LIMIT 1"
    );
    if (existing.rows[0]) return existing.rows[0].id;
    const inserted = await client.query<{ id: string }>(
      `INSERT INTO wallet_accounts (driver_id, account_kind, currency, status)
       VALUES (NULL, 'PLATFORM_CLEARING', 'INR', 'ACTIVE')
       RETURNING id`
    );
    return inserted.rows[0]!.id;
  }

  private async ensureDriverWalletAccount(client: PoolClient, driverId: string): Promise<string> {
    const existing = await client.query<{ id: string }>(
      "SELECT id FROM wallet_accounts WHERE driver_id = $1",
      [driverId]
    );
    if (existing.rows[0]) return existing.rows[0].id;
    const inserted = await client.query<{ id: string }>(
      `INSERT INTO wallet_accounts (driver_id, account_kind, currency, status)
       VALUES ($1, 'DRIVER_WALLET', 'INR', 'ACTIVE')
       ON CONFLICT (driver_id) DO UPDATE SET updated_at = NOW()
       RETURNING id`,
      [driverId]
    );
    return inserted.rows[0]!.id;
  }

  private async postDoubleEntryTx(
    client: PoolClient,
    params: {
      sourceType: string;
      sourceId: string;
      idempotencyKey: string;
      description: string;
      statementId?: string | null;
      legs: Array<{ accountId: string; direction: "DEBIT" | "CREDIT"; amountMinor: number }>;
    }
  ): Promise<string> {
    assertBalancedDoubleEntry(params.legs);

    const existing = await client.query<{ id: string }>(
      "SELECT id FROM ledger_transactions WHERE idempotency_key = $1",
      [params.idempotencyKey]
    );
    if (existing.rows[0]) return existing.rows[0].id;

    const txRes = await client.query<{ id: string }>(
      `INSERT INTO ledger_transactions (source_type, source_id, idempotency_key, description, statement_id)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id`,
      [
        params.sourceType,
        params.sourceId,
        params.idempotencyKey,
        params.description,
        params.statementId ?? null
      ]
    );
    const txId = txRes.rows[0]!.id;
    for (const leg of params.legs) {
      await client.query(
        `INSERT INTO ledger_entries (transaction_id, account_id, direction, amount_minor, currency)
         VALUES ($1, $2, $3, $4, 'INR')`,
        [txId, leg.accountId, leg.direction, leg.amountMinor]
      );
    }
    return txId;
  }

  async checkHealth(): Promise<{ databaseReady: boolean; postgisReady: boolean }> {
    try {
      await this.pool.query("SELECT 1");
      const postgis = await this.pool.query<{ v: string }>("SELECT PostGIS_version() AS v");
      return {
        databaseReady: true,
        postgisReady: Boolean(postgis.rows[0]?.v)
      };
    } catch {
      return { databaseReady: false, postgisReady: false };
    }
  }

  private async buildSessionDto(client: PoolClient, userId: string): Promise<AuthSessionDto> {
    const rowRes = await client.query<{
      user_id: string;
      driver_id: string;
      activation_status: AccountStatus;
      full_name: string;
      city_code: string;
      phone_masked: string;
    }>(
      `SELECT u.id AS user_id, d.id AS driver_id, d.activation_status, d.full_name, d.city_code, u.phone_masked
       FROM users u
       JOIN drivers d ON d.user_id = u.id
       WHERE u.id = $1`,
      [userId]
    );
    const row = rowRes.rows[0];
    if (!row) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.UNAUTHORIZED, message: "Driver session not found." });
    }
    const vehRes = await client.query<{ cnt: string }>(
      "SELECT COUNT(*)::text AS cnt FROM vehicles WHERE driver_id = $1 AND status <> 'RETIRED'",
      [row.driver_id]
    );
    const docRes = await client.query<{ document_type: string; status: DocumentStatus }>(
      "SELECT document_type, status FROM driver_documents WHERE driver_id = $1",
      [row.driver_id]
    );
    const docsByType = new Map(docRes.rows.map((r) => [r.document_type, r.status]));
    const requiresProfile = !row.full_name.trim() || !row.city_code.trim();
    const requiresVehicle = Number(vehRes.rows[0]?.cnt ?? "0") === 0;
    const requiresKyc = MANDATORY_DOC_TYPES.some((t) => {
      const s = docsByType.get(t);
      return !s || s === "NOT_SUBMITTED" || s === "REJECTED" || s === "EXPIRED";
    });
    return {
      userId: row.user_id,
      driverId: row.driver_id,
      accountStatus: row.activation_status,
      requiresProfile,
      requiresVehicle,
      requiresKyc,
      serverTimeUtc: new Date().toISOString(),
      phoneMasked: row.phone_masked
    };
  }

  async establishSession(claims: { firebaseUid: string; phoneE164?: string | null }): Promise<AuthSessionDto> {
    return this.withTx(async (client) => {
      const existing = await client.query<{ id: string }>(
        "SELECT id FROM users WHERE auth_uid = $1 OR firebase_uid = $1 LIMIT 1",
        [claims.firebaseUid]
      );
      let userId: string;
      if (existing.rows[0]) {
        userId = existing.rows[0].id;
        await client.query(
          "UPDATE users SET auth_uid = COALESCE(auth_uid, $2), last_login_at = NOW(), updated_at = NOW() WHERE id = $1",
          [userId, claims.firebaseUid]
        );
      } else {
        const phone = claims.phoneE164 ?? "+910000000000";
        const masked = maskPhone(phone);
        const insertedUser = await client.query<{ id: string }>(
          `INSERT INTO users (auth_uid, firebase_uid, auth_provider, phone_e164, phone_masked, last_login_at)
           VALUES ($1, $1, 'supabase', $2, $3, NOW())
           RETURNING id`,
          [claims.firebaseUid, phone, masked]
        );
        userId = insertedUser.rows[0]!.id;
        const driverCode = `DP-${userId.slice(0, 8).toUpperCase()}`;
        const insertedDriver = await client.query<{ id: string }>(
          `INSERT INTO drivers (user_id, driver_code)
           VALUES ($1, $2)
           RETURNING id`,
          [userId, driverCode]
        );
        const driverId = insertedDriver.rows[0]!.id;
        await this.ensureDriverWalletAccount(client, driverId);
        await client.query(
          "INSERT INTO driver_presence (driver_id, status) VALUES ($1, 'OFFLINE') ON CONFLICT DO NOTHING",
          [driverId]
        );
        await client.query(
          "INSERT INTO notification_preferences (user_id) VALUES ($1) ON CONFLICT DO NOTHING",
          [userId]
        );
        for (const docType of MANDATORY_DOC_TYPES) {
          await client.query(
            `INSERT INTO driver_documents (driver_id, document_type, status)
             VALUES ($1, $2, 'NOT_SUBMITTED') ON CONFLICT DO NOTHING`,
            [driverId, docType]
          );
        }
        const refCode = `DRV${driverId.replace(/-/g, "").slice(0, 6).toUpperCase()}`;
        await client.query(
          "INSERT INTO referral_codes (owner_driver_id, code) VALUES ($1, $2) ON CONFLICT DO NOTHING",
          [driverId, refCode]
        );
      }
      return this.buildSessionDto(client, userId);
    });
  }

  async getSessionByFirebaseUid(firebaseUid: string): Promise<AuthSessionDto | null> {
    const client = await this.pool.connect();
    try {
      const res = await client.query<{ id: string }>(
        "SELECT id FROM users WHERE auth_uid = $1 OR firebase_uid = $1 LIMIT 1",
        [firebaseUid]
      );
      if (!res.rows[0]) return null;
      return this.buildSessionDto(client, res.rows[0].id);
    } finally {
      client.release();
    }
  }

  async logout(driverId: string): Promise<void> {
    const active = await this.pool.query<{ id: string }>(
      `SELECT id FROM trips
       WHERE driver_id = $1 AND status IN ('ASSIGNED','EN_ROUTE_TO_PICKUP','ARRIVED_AT_PICKUP','IN_PROGRESS')
       LIMIT 1`,
      [driverId]
    );
    if (active.rows[0]) {
      throw new AppError({
        httpStatus: 409,
        code: ErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED,
        message: "Cannot sign out while an active trip is in progress."
      });
    }
    await this.pool.query(
      "UPDATE driver_presence SET status = 'OFFLINE', session_id = NULL, updated_at = NOW() WHERE driver_id = $1",
      [driverId]
    );
  }

  async getDriverProfile(driverId: string): Promise<DriverProfileDto> {
    const res = await this.pool.query<{
      driver_id: string;
      full_name: string;
      phone_masked: string;
      city_code: string;
      preferred_language: string;
      activation_status: AccountStatus;
      documents_verified: boolean;
      date_of_birth: string | null;
      gender: string | null;
      address_line: string | null;
      state_code: string | null;
      pin_code: string | null;
      profile_photo_object_key: string | null;
      rating: number | null;
      total_completed_trips: number;
      acceptance_rate: number | null;
    }>(
      `SELECT d.id AS driver_id, d.full_name, u.phone_masked, d.city_code, u.preferred_language,
              d.activation_status, d.documents_verified, d.date_of_birth::text, d.gender,
              d.address_line, d.state_code, d.pin_code, d.profile_photo_object_key,
              d.rating, d.total_completed_trips, d.acceptance_rate
       FROM drivers d
       JOIN users u ON u.id = d.user_id
       WHERE d.id = $1`,
      [driverId]
    );
    const r = res.rows[0];
    if (!r) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.UNAUTHORIZED, message: "Driver profile not found." });
    }
    return {
      driverId: r.driver_id,
      fullName: r.full_name,
      phoneMasked: r.phone_masked,
      cityCode: r.city_code,
      preferredLanguage: r.preferred_language,
      accountStatus: r.activation_status,
      documentsVerified: r.documents_verified,
      dateOfBirth: r.date_of_birth,
      gender: r.gender,
      addressLine: r.address_line,
      stateCode: r.state_code,
      pinCode: r.pin_code,
      profilePhotoObjectKey: r.profile_photo_object_key,
      rating: r.rating,
      totalCompletedTrips: r.total_completed_trips,
      acceptanceRate: r.acceptance_rate
    };
  }

  async upsertDriverProfile(driverId: string, input: DriverProfileInput): Promise<DriverProfileDto> {
    if (input.profilePhotoObjectKey) {
      assertPrivateObjectKey(input.profilePhotoObjectKey);
    }
    await this.withTx(async (client) => {
      await client.query(
        `UPDATE drivers
         SET full_name = $2,
             city_code = $3,
             date_of_birth = $4::date,
             gender = $5,
             address_line = $6,
             state_code = $7,
             pin_code = $8,
             profile_photo_object_key = COALESCE($9, profile_photo_object_key),
             updated_at = NOW()
         WHERE id = $1`,
        [
          driverId,
          input.fullName.trim(),
          input.cityCode.trim().toUpperCase(),
          input.dateOfBirth ?? null,
          input.gender ?? null,
          input.addressLine ?? null,
          input.stateCode?.toUpperCase() ?? null,
          input.pinCode ?? null,
          input.profilePhotoObjectKey ?? null
        ]
      );
      await client.query(
        `UPDATE users u
         SET display_name = $2, preferred_language = $3, updated_at = NOW()
         FROM drivers d
         WHERE d.user_id = u.id AND d.id = $1`,
        [driverId, input.fullName.trim(), input.preferredLanguage]
      );
    });
    return this.getDriverProfile(driverId);
  }

  async getVehicles(driverId: string): Promise<VehicleDto[]> {
    const res = await this.pool.query<{
      id: string;
      category: ServiceCategory;
      registration_number_normalized: string;
      model: string;
      is_approved: boolean;
      make: string;
      color: string;
      year: number | null;
      status: string;
      vehicle_photo_object_key: string | null;
    }>(
      `SELECT id, category, registration_number_normalized, model, is_approved, make, color, year, status, vehicle_photo_object_key
       FROM vehicles WHERE driver_id = $1 AND status <> 'RETIRED'
       ORDER BY created_at DESC`,
      [driverId]
    );
    return res.rows.map((v) => ({
      vehicleId: v.id,
      serviceTypeCode: v.category,
      registrationNumber: v.registration_number_normalized,
      vehicleModel: v.model,
      isApproved: v.is_approved,
      brand: v.make,
      color: v.color,
      year: v.year,
      status: v.status,
      vehiclePhotoObjectKey: v.vehicle_photo_object_key
    }));
  }

  async upsertVehicle(driverId: string, input: VehicleInput): Promise<VehicleDto> {
    const normalized = normalizeVehicleRegistration(input.registrationNumber);
    const masked = maskVehicleReg(normalized);
    if (input.vehiclePhotoObjectKey) {
      assertPrivateObjectKey(input.vehiclePhotoObjectKey);
    }

    return this.withTx(async (client) => {
      const collision = await client.query<{ driver_id: string }>(
        `SELECT driver_id FROM vehicles
         WHERE registration_number_normalized = $1 AND status <> 'RETIRED' AND driver_id <> $2
         LIMIT 1`,
        [normalized, driverId]
      );
      if (collision.rows[0]) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.DUPLICATE_VEHICLE_REGISTRATION,
          message: `Vehicle registration ${normalized} is already registered to another partner.`
        });
      }

      const existing = await client.query<{ id: string }>(
        "SELECT id FROM vehicles WHERE driver_id = $1 AND status <> 'RETIRED' LIMIT 1",
        [driverId]
      );
      let rowId: string;
      if (existing.rows[0]) {
        rowId = existing.rows[0].id;
        await client.query(
          `UPDATE vehicles
           SET category = $2::service_category,
               registration_number_normalized = $3,
               registration_number_masked = $4,
               make = $5,
               model = $6,
               color = $7,
               year = $8,
               vehicle_photo_object_key = COALESCE($9, vehicle_photo_object_key),
               is_approved = FALSE,
               status = 'PENDING_REVIEW',
               updated_at = NOW()
           WHERE id = $1`,
          [
            rowId,
            input.serviceTypeCode,
            normalized,
            masked,
            input.brand,
            input.vehicleModel,
            input.color,
            input.year ?? null,
            input.vehiclePhotoObjectKey ?? null
          ]
        );
      } else {
        const ins = await client.query<{ id: string }>(
          `INSERT INTO vehicles (
             driver_id, category, registration_number_normalized, registration_number_masked,
             make, model, color, year, vehicle_photo_object_key, status, is_approved
           ) VALUES ($1, $2::service_category, $3, $4, $5, $6, $7, $8, $9, 'PENDING_REVIEW', FALSE)
           RETURNING id`,
          [
            driverId,
            input.serviceTypeCode,
            normalized,
            masked,
            input.brand,
            input.vehicleModel,
            input.color,
            input.year ?? null,
            input.vehiclePhotoObjectKey ?? null
          ]
        );
        rowId = ins.rows[0]!.id;
      }

      await client.query(
        `UPDATE drivers SET active_service_category = $2::service_category, updated_at = NOW() WHERE id = $1`,
        [driverId, input.serviceTypeCode]
      );

      const list = await this.getVehicles(driverId);
      return list[0]!;
    });
  }

  async createDocumentUploadIntent(driverId: string, input: UploadIntentInput): Promise<UploadIntentDto> {
    const docRes = await this.pool.query<{ id: string }>(
      `INSERT INTO driver_documents (driver_id, document_type, mime_type, file_size_bytes, checksum, status)
       VALUES ($1, $2, $3, $4, $5, 'NOT_SUBMITTED')
       ON CONFLICT (driver_id, document_type)
       DO UPDATE SET mime_type = EXCLUDED.mime_type, file_size_bytes = EXCLUDED.file_size_bytes, updated_at = NOW()
       RETURNING id`,
      [driverId, input.documentType, input.mimeType, input.sizeBytes, input.checksumSha256 ?? null]
    );
    const documentId = docRes.rows[0]!.id;
    const objectKey = `private/kyc/${driverId}/${input.documentType.toLowerCase()}/${randomUUID()}`;
    if (this.opts.storageUploader) {
      const signed = await this.opts.storageUploader.createSignedUploadUrl({
        objectKey,
        mimeType: input.mimeType
      });
      return {
        documentId,
        uploadUrl: signed.uploadUrl,
        objectKey: signed.objectKey,
        expiresAtUtc: signed.expiresAtUtc,
        requiredHeaders: signed.requiredHeaders
      };
    }
    return {
      documentId,
      uploadUrl: `https://${this.opts.objectStorageBucket}.storage.local/${objectKey}`,
      objectKey,
      expiresAtUtc: new Date(Date.now() + 7200_000).toISOString(),
      requiredHeaders: { "Content-Type": input.mimeType }
    };
  }

  async confirmDocumentUpload(driverId: string, input: ConfirmDocumentInput): Promise<KycDocumentDto> {
    assertPrivateObjectKey(input.storageObjectKey);
    if (containsRawAadhaarDigits(input.maskedReference)) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.INVALID_INPUT,
        message: "Raw 12-digit Aadhaar numbers cannot be submitted or stored."
      });
    }

    return this.withTx(async (client) => {
      const check = await client.query<{ id: string; driver_id: string; status: DocumentStatus }>(
        "SELECT id, driver_id, status FROM driver_documents WHERE id = $1 FOR UPDATE",
        [input.documentId]
      );
      const row = check.rows[0];
      if (!row) {
        throw new AppError({ httpStatus: 404, code: ErrorCodes.DOCUMENT_NOT_FOUND, message: "Document not found." });
      }
      if (row.driver_id !== driverId) {
        throw new AppError({
          httpStatus: 403,
          code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
          message: "Document belongs to a different driver."
        });
      }

      const upd = await client.query<{
        id: string;
        document_type: DocumentType;
        status: DocumentStatus;
        rejection_reason_code: string | null;
        masked_reference: string | null;
        expires_on: string | null;
        updated_at: Date;
      }>(
        `UPDATE driver_documents
         SET object_key = $2,
             masked_reference = COALESCE($3, masked_reference),
             expires_on = COALESCE($4::date, expires_on),
             status = 'UNDER_REVIEW',
             rejection_reason_code = NULL,
             submitted_at = NOW(),
             updated_at = NOW()
         WHERE id = $1
         RETURNING id, document_type, status, rejection_reason_code, masked_reference, expires_on::text, updated_at`,
        [row.id, input.storageObjectKey, input.maskedReference ?? null, input.expiresOnUtc ?? null]
      );

      await client.query(
        `INSERT INTO verification_events (document_id, from_status, to_status)
         VALUES ($1, $2, 'UNDER_REVIEW')`,
        [row.id, row.status]
      );

      const allDocs = await client.query<{ document_type: string; status: DocumentStatus }>(
        "SELECT document_type, status FROM driver_documents WHERE driver_id = $1",
        [driverId]
      );
      const docMap = new Map(allDocs.rows.map((d) => [d.document_type, d.status]));
      const allSubmitted = MANDATORY_DOC_TYPES.every((t) => {
        const s = docMap.get(t);
        return s === "UNDER_REVIEW" || s === "UPLOADED" || s === "APPROVED";
      });
      if (allSubmitted) {
        await client.query(
          `UPDATE drivers
           SET activation_status = CASE WHEN activation_status = 'APPLICANT' THEN 'PENDING_REVIEW'::account_status ELSE activation_status END,
               updated_at = NOW()
           WHERE id = $1`,
          [driverId]
        );
      }

      const updated = upd.rows[0]!;
      return {
        documentId: updated.id,
        documentType: updated.document_type,
        status: updated.status,
        rejectionReason: updated.rejection_reason_code,
        maskedReference: updated.masked_reference,
        expiresAtUtc: updated.expires_on,
        updatedAtUtc: updated.updated_at.toISOString()
      };
    });
  }

  async getDocuments(driverId: string): Promise<KycDocumentDto[]> {
    const res = await this.pool.query<{
      id: string;
      document_type: DocumentType;
      status: DocumentStatus;
      rejection_reason_code: string | null;
      masked_reference: string | null;
      expires_on: string | null;
      updated_at: Date;
    }>(
      `SELECT id, document_type, status, rejection_reason_code, masked_reference, expires_on::text, updated_at
       FROM driver_documents WHERE driver_id = $1 ORDER BY document_type`,
      [driverId]
    );
    return res.rows.map((d) => ({
      documentId: d.id,
      documentType: d.document_type,
      status: d.status,
      rejectionReason: d.rejection_reason_code,
      maskedReference: d.masked_reference,
      expiresAtUtc: d.expires_on,
      updatedAtUtc: d.updated_at.toISOString()
    }));
  }

  async getEligibility(driverId: string): Promise<EligibilityDto> {
    const driverRes = await this.pool.query<{
      activation_status: AccountStatus;
      documents_verified: boolean;
      city_code: string;
    }>("SELECT activation_status, documents_verified, city_code FROM drivers WHERE id = $1", [driverId]);
    const drv = driverRes.rows[0];
    if (!drv) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.UNAUTHORIZED, message: "Driver not found." });
    }
    const reasons: string[] = [];
    if (drv.activation_status === "SUSPENDED") reasons.push("ACTIVE_SUSPENSION");
    else if (drv.activation_status !== "ACTIVE") reasons.push("ACCOUNT_NOT_APPROVED");

    const docs = await this.getDocuments(driverId);
    if (docs.some((d) => d.status === "REJECTED")) reasons.push("DOCUMENT_REJECTED");
    if (docs.some((d) => d.status === "EXPIRED")) reasons.push("DOCUMENT_EXPIRED");
    if (!drv.documents_verified || docs.some((d) => d.status === "NOT_SUBMITTED" || d.status === "UNDER_REVIEW")) {
      reasons.push("KYC_INCOMPLETE");
    }

    const vehicles = await this.getVehicles(driverId);
    const approvedCats = vehicles.filter((v) => v.isApproved).map((v) => v.serviceTypeCode);
    if (approvedCats.length === 0) reasons.push("VEHICLE_NOT_APPROVED");

    const zones = await this.getZones(drv.city_code || "JAI");
    const uniqueReasons = Array.from(new Set(reasons));
    return {
      isEligibleForOnline: uniqueReasons.length === 0,
      blockingReasons: uniqueReasons,
      approvedServiceCategoryCodes: approvedCats,
      assignedZoneIds: zones.map((z) => z.zoneId),
      evaluatedAtUtc: new Date().toISOString()
    };
  }

  async getHomeSummary(driverId: string): Promise<HomeSummaryDto> {
    const presenceRes = await this.pool.query<{ status: DutyStatus }>(
      "SELECT status FROM driver_presence WHERE driver_id = $1",
      [driverId]
    );
    const drvRes = await this.pool.query<{ city_code: string }>(
      "SELECT city_code FROM drivers WHERE id = $1",
      [driverId]
    );
    const todayRes = await this.pool.query<{ cnt: string; total: string }>(
      `SELECT COUNT(*)::text AS cnt, COALESCE(SUM(net_amount_minor), 0)::text AS total
       FROM earning_statements
       WHERE driver_id = $1 AND created_at >= date_trunc('day', NOW() AT TIME ZONE 'UTC')`,
      [driverId]
    );
    const zones = await this.getZones(drvRes.rows[0]?.city_code || "JAI");
    return {
      dutyStatus: presenceRes.rows[0]?.status ?? "OFFLINE",
      todayCompletedCount: Number(todayRes.rows[0]?.cnt ?? "0"),
      todayEstimatedMinor: Number(todayRes.rows[0]?.total ?? "0"),
      currency: "INR",
      activeZones: zones,
      serverTimeUtc: new Date().toISOString()
    };
  }

  async getPreferences(driverId: string): Promise<DriverPreferencesDto> {
    const drvRes = await this.pool.query<{
      auto_boost_enabled: boolean;
      active_service_category: ServiceCategory;
    }>("SELECT auto_boost_enabled, active_service_category FROM drivers WHERE id = $1", [driverId]);
    const zoneRes = await this.pool.query<{ zone_id: string }>(
      "SELECT zone_id FROM driver_zone_preferences WHERE driver_id = $1 AND enabled = TRUE",
      [driverId]
    );
    return {
      preferredZoneIds: zoneRes.rows.map((r) => r.zone_id),
      autoBoostEnabled: drvRes.rows[0]?.auto_boost_enabled ?? false,
      activeServiceCategoryCode: drvRes.rows[0]?.active_service_category ?? "BIKE"
    };
  }

  async updatePreferences(driverId: string, input: DriverPreferencesDto): Promise<DriverPreferencesDto> {
    await this.withTx(async (client) => {
      await client.query(
        `UPDATE drivers
         SET auto_boost_enabled = $2, active_service_category = $3::service_category, updated_at = NOW()
         WHERE id = $1`,
        [driverId, input.autoBoostEnabled, input.activeServiceCategoryCode]
      );
      await client.query("DELETE FROM driver_zone_preferences WHERE driver_id = $1", [driverId]);
      for (const zoneId of input.preferredZoneIds) {
        await client.query(
          `INSERT INTO driver_zone_preferences (driver_id, zone_id, enabled)
           VALUES ($1, $2, TRUE) ON CONFLICT DO NOTHING`,
          [driverId, zoneId]
        );
      }
    });
    return this.getPreferences(driverId);
  }

  async getZones(cityCode: string): Promise<ServiceZoneDto[]> {
    const res = await this.pool.query<{ id: string; city_code: string; name: string; enabled: boolean }>(
      "SELECT id, city_code, name, enabled FROM service_zones WHERE city_code = $1 AND enabled = TRUE ORDER BY name",
      [cityCode.toUpperCase()]
    );
    return res.rows.map((r) => ({
      zoneId: r.id,
      cityCode: r.city_code,
      name: r.name,
      isEnabled: r.enabled
    }));
  }

  async goOnline(driverId: string, input: DutyStatusInput): Promise<DutyStateDto> {
    const eligibility = await this.getEligibility(driverId);
    if (!eligibility.isEligibleForOnline) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.DRIVER_INELIGIBLE,
        message: `Cannot go online: ${eligibility.blockingReasons.join(", ")}`
      });
    }
    if (input.latitude == null || input.longitude == null) {
      throw new AppError({
        httpStatus: 422,
        code: "LOCATION_UNAVAILABLE",
        message: "Verified GPS coordinates are required before going online."
      });
    }
    const sessionId = randomUUID();
    await this.pool.query(
      `INSERT INTO driver_presence (driver_id, status, last_location, location_accuracy_m, location_recorded_at, last_heartbeat_at, session_id, updated_at)
       VALUES ($1, 'ONLINE', ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography, $4, NOW(), NOW(), $5, NOW())
       ON CONFLICT (driver_id) DO UPDATE
       SET status = 'ONLINE',
           last_location = EXCLUDED.last_location,
           location_accuracy_m = EXCLUDED.location_accuracy_m,
           location_recorded_at = NOW(),
           last_heartbeat_at = NOW(),
           session_id = $5,
           updated_at = NOW()`,
      [driverId, input.latitude, input.longitude, input.accuracyMeters ?? 15, sessionId]
    );
    const activeTrip = await this.getActiveTrip(driverId);
    return {
      status: "ONLINE",
      sessionId,
      activeTripId: activeTrip.trip?.tripId ?? null,
      serverTimeUtc: new Date().toISOString()
    };
  }

  async goOffline(driverId: string, _input: DutyStatusInput): Promise<DutyStateDto> {
    const active = await this.getActiveTrip(driverId);
    if (active.trip) {
      throw new AppError({
        httpStatus: 409,
        code: ErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED,
        message: "Complete or cancel active trip before going offline."
      });
    }
    await this.pool.query(
      `UPDATE driver_presence SET status = 'OFFLINE', session_id = NULL, updated_at = NOW() WHERE driver_id = $1`,
      [driverId]
    );
    return {
      status: "OFFLINE",
      sessionId: null,
      activeTripId: null,
      serverTimeUtc: new Date().toISOString()
    };
  }

  async recordLocationPing(
    driverId: string,
    input: LocationPingInput
  ): Promise<{ accepted: boolean; staleAfterSeconds: number; serverTimeUtc: string }> {
    const capturedMs = Date.parse(input.capturedAtUtc);
    if (Number.isNaN(capturedMs) || capturedMs > Date.now() + 60_000) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.INVALID_INPUT,
        message: "Invalid or future-dated location timestamp."
      });
    }
    await this.pool.query(
      `UPDATE driver_presence
       SET last_location = ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography,
           location_accuracy_m = $4,
           location_recorded_at = $5::timestamptz,
           last_heartbeat_at = NOW(),
           updated_at = NOW()
       WHERE driver_id = $1`,
      [driverId, input.longitude, input.latitude, input.accuracyMeters, input.capturedAtUtc]
    );
    await this.pool.query(
      `INSERT INTO driver_location_events (driver_id, point, accuracy_m, speed_mps, bearing_deg, recorded_at)
       VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography, $4, $5, $6, $7::timestamptz)`,
      [
        driverId,
        input.longitude,
        input.latitude,
        input.accuracyMeters,
        input.speedMps ?? null,
        input.bearingDegrees ?? null,
        input.capturedAtUtc
      ]
    );
    return {
      accepted: true,
      staleAfterSeconds: 45,
      serverTimeUtc: new Date().toISOString()
    };
  }

  async getActiveOffers(driverId: string): Promise<{ offers: WorkOfferDto[]; serverNowUtc: string }> {
    const res = await this.pool.query<{
      offer_id: string;
      order_id: string;
      service_category: ServiceCategory;
      priority_label: string | null;
      pickup_label: string;
      drop_label: string;
      pickup_distance_m: number;
      estimated_distance_m: number;
      estimated_duration_s: number;
      quoted_base_amount_minor: string;
      quoted_incentive_amount_minor: string;
      expires_at: Date;
      status: OfferStatus;
    }>(
      `SELECT oo.id AS offer_id, oo.order_id, o.service_category, oo.priority_label,
              o.pickup_label, o.drop_label, oo.pickup_distance_m, o.estimated_distance_m,
              o.estimated_duration_s, oo.quoted_base_amount_minor::text,
              oo.quoted_incentive_amount_minor::text, oo.expires_at, oo.status
       FROM order_offers oo
       JOIN orders o ON o.id = oo.order_id
       WHERE oo.driver_id = $1 AND oo.status = 'OFFERED' AND oo.expires_at > NOW()
       ORDER BY oo.created_at DESC`,
      [driverId]
    );
    const serverNowUtc = new Date().toISOString();
    return {
      offers: res.rows.map((r) => {
        const base = Number(r.quoted_base_amount_minor);
        const inc = Number(r.quoted_incentive_amount_minor);
        return {
          offerId: r.offer_id,
          orderId: r.order_id,
          serviceCategoryCode: r.service_category,
          priorityLabel: r.priority_label,
          pickupSummary: r.pickup_label,
          dropSummary: r.drop_label,
          pickupDistanceMeters: r.pickup_distance_m,
          distanceMeters: r.estimated_distance_m,
          durationSeconds: r.estimated_duration_s,
          estimatedBaseMinor: base,
          eligibleIncentiveMinor: inc,
          estimatedTotalMinor: base + inc,
          currency: "INR",
          isEstimate: true,
          expiresAtUtc: r.expires_at.toISOString(),
          serverNowUtc,
          status: r.status
        };
      }),
      serverNowUtc
    };
  }

  async getOfferDetail(driverId: string, offerId: string): Promise<WorkOfferDto> {
    const res = await this.pool.query<{
      offer_id: string;
      driver_id: string;
      order_id: string;
      service_category: ServiceCategory;
      priority_label: string | null;
      pickup_label: string;
      drop_label: string;
      pickup_distance_m: number;
      estimated_distance_m: number;
      estimated_duration_s: number;
      quoted_base_amount_minor: string;
      quoted_incentive_amount_minor: string;
      expires_at: Date;
      status: OfferStatus;
    }>(
      `SELECT oo.id AS offer_id, oo.driver_id, oo.order_id, o.service_category, oo.priority_label,
              o.pickup_label, o.drop_label, oo.pickup_distance_m, o.estimated_distance_m,
              o.estimated_duration_s, oo.quoted_base_amount_minor::text,
              oo.quoted_incentive_amount_minor::text, oo.expires_at, oo.status
       FROM order_offers oo
       JOIN orders o ON o.id = oo.order_id
       WHERE oo.id = $1`,
      [offerId]
    );
    const r = res.rows[0];
    if (!r) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
    }
    if (r.driver_id !== driverId) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
        message: "Offer belongs to another driver."
      });
    }
    const base = Number(r.quoted_base_amount_minor);
    const inc = Number(r.quoted_incentive_amount_minor);
    return {
      offerId: r.offer_id,
      orderId: r.order_id,
      serviceCategoryCode: r.service_category,
      priorityLabel: r.priority_label,
      pickupSummary: r.pickup_label,
      dropSummary: r.drop_label,
      pickupDistanceMeters: r.pickup_distance_m,
      distanceMeters: r.estimated_distance_m,
      durationSeconds: r.estimated_duration_s,
      estimatedBaseMinor: base,
      eligibleIncentiveMinor: inc,
      estimatedTotalMinor: base + inc,
      currency: "INR",
      isEstimate: true,
      expiresAtUtc: r.expires_at.toISOString(),
      serverNowUtc: new Date().toISOString(),
      status: r.status
    };
  }

  async acceptOffer(driverId: string, offerId: string, idempotencyKey: string): Promise<AcceptOfferDto> {
    const eligibility = await this.getEligibility(driverId);
    if (!eligibility.isEligibleForOnline) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.DRIVER_INELIGIBLE,
        message: "Driver is ineligible to accept offers."
      });
    }
    const presence = await this.pool.query<{ status: DutyStatus }>(
      "SELECT status FROM driver_presence WHERE driver_id = $1",
      [driverId]
    );
    if (presence.rows[0]?.status !== "ONLINE") {
      throw new AppError({
        httpStatus: 409,
        code: ErrorCodes.DRIVER_OFFLINE,
        message: "Driver must be online to accept offers."
      });
    }

    return this.withTx(async (client) => {
      const offerRes = await client.query<{
        id: string;
        order_id: string;
        driver_id: string;
        status: OfferStatus;
        expires_at: Date;
        idempotency_key: string | null;
      }>("SELECT id, order_id, driver_id, status, expires_at, idempotency_key FROM order_offers WHERE id = $1 FOR UPDATE", [
        offerId
      ]);
      const offer = offerRes.rows[0];
      if (!offer) {
        throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
      }
      if (offer.driver_id !== driverId) {
        throw new AppError({
          httpStatus: 403,
          code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
          message: "Offer does not belong to authenticated driver."
        });
      }

      if (offer.status === "ACCEPTED" && offer.idempotency_key === idempotencyKey) {
        const existingTrip = await client.query<{ id: string; status: TripStatus; accepted_at: Date }>(
          "SELECT id, status, accepted_at FROM trips WHERE order_id = $1 AND driver_id = $2",
          [offer.order_id, driverId]
        );
        if (existingTrip.rows[0]) {
          return {
            tripId: existingTrip.rows[0].id,
            orderId: offer.order_id,
            tripStatus: existingTrip.rows[0].status,
            acceptedAtUtc: existingTrip.rows[0].accepted_at.toISOString()
          };
        }
      }

      if (offer.status !== "OFFERED") {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.OFFER_ALREADY_CLAIMED,
          message: `Offer is no longer actionable (${offer.status}).`
        });
      }
      if (offer.expires_at.getTime() <= Date.now()) {
        await client.query("UPDATE order_offers SET status = 'EXPIRED', responded_at = NOW() WHERE id = $1", [
          offer.id
        ]);
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.OFFER_EXPIRED,
          message: "Offer has expired."
        });
      }

      const orderRes = await client.query<{ id: string; assigned_driver_id: string | null }>(
        "SELECT id, assigned_driver_id FROM orders WHERE id = $1 FOR UPDATE",
        [offer.order_id]
      );
      const order = orderRes.rows[0];
      if (!order || order.assigned_driver_id !== null) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.ORDER_ALREADY_ASSIGNED,
          message: "Order has already been assigned."
        });
      }

      await client.query(
        "UPDATE orders SET assigned_driver_id = $2, assigned_at = NOW(), status = 'ASSIGNED', updated_at = NOW() WHERE id = $1",
        [order.id, driverId]
      );
      await client.query(
        "UPDATE order_offers SET status = 'ACCEPTED', idempotency_key = $2, responded_at = NOW() WHERE id = $1",
        [offer.id, idempotencyKey]
      );
      await client.query(
        "UPDATE order_offers SET status = 'WITHDRAWN' WHERE order_id = $1 AND id <> $2 AND status = 'OFFERED'",
        [order.id, offer.id]
      );

      let tripRes;
      try {
        tripRes = await client.query<{ id: string; status: TripStatus; accepted_at: Date }>(
          `INSERT INTO trips (order_id, driver_id, status, version)
           VALUES ($1, $2, 'ASSIGNED', 1)
           RETURNING id, status, accepted_at`,
          [order.id, driverId]
        );
      } catch (err: unknown) {
        const pgErr = err as { code?: string };
        if (pgErr.code === "23505") {
          throw new AppError({
            httpStatus: 409,
            code: ErrorCodes.ORDER_ALREADY_ASSIGNED,
            message: "Driver already has an active trip or order already claimed."
          });
        }
        throw err;
      }

      const trip = tripRes.rows[0]!;
      await client.query(
        `INSERT INTO trip_events (trip_id, event_type, from_status, to_status, actor_type, actor_id, idempotency_key)
         VALUES ($1, 'OFFER_ACCEPTED', NULL, 'ASSIGNED', 'DRIVER', $2, $3)`,
        [trip.id, driverId, idempotencyKey]
      );

      return {
        tripId: trip.id,
        orderId: order.id,
        tripStatus: trip.status,
        acceptedAtUtc: trip.accepted_at.toISOString()
      };
    });
  }

  async rejectOffer(
    driverId: string,
    offerId: string,
    idempotencyKey: string,
    _reasonCode?: string | null
  ): Promise<void> {
    await this.withTx(async (client) => {
      const offerRes = await client.query<{ id: string; driver_id: string; status: OfferStatus }>(
        "SELECT id, driver_id, status FROM order_offers WHERE id = $1 FOR UPDATE",
        [offerId]
      );
      const offer = offerRes.rows[0];
      if (!offer) {
        throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
      }
      if (offer.driver_id !== driverId) {
        throw new AppError({
          httpStatus: 403,
          code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
          message: "Offer belongs to another driver."
        });
      }
      if (offer.status === "REJECTED") return;
      await client.query(
        "UPDATE order_offers SET status = 'REJECTED', idempotency_key = $2, responded_at = NOW() WHERE id = $1",
        [offer.id, idempotencyKey]
      );
    });
  }

  async getActiveTrip(driverId: string): Promise<{ trip: ActiveTripDto | null; serverNowUtc: string }> {
    const res = await this.pool.query<{
      trip_id: string;
      order_id: string;
      service_category: ServiceCategory;
      status: TripStatus;
      version: string;
      pickup_label: string;
      pickup_lat: number;
      pickup_lng: number;
      drop_label: string;
      drop_lat: number;
      drop_lng: number;
      started_at: Date | null;
      updated_at: Date;
      requires_start_pin: boolean;
    }>(
      `SELECT t.id AS trip_id, t.order_id, o.service_category, t.status, t.version::text,
              o.pickup_label, ST_Y(o.pickup_point::geometry) AS pickup_lat, ST_X(o.pickup_point::geometry) AS pickup_lng,
              o.drop_label, ST_Y(o.drop_point::geometry) AS drop_lat, ST_X(o.drop_point::geometry) AS drop_lng,
              t.started_at, t.updated_at, t.requires_start_pin
       FROM trips t
       JOIN orders o ON o.id = t.order_id
       WHERE t.driver_id = $1 AND t.status IN ('ASSIGNED','EN_ROUTE_TO_PICKUP','ARRIVED_AT_PICKUP','IN_PROGRESS')
       LIMIT 1`,
      [driverId]
    );
    const r = res.rows[0];
    return {
      trip: r
        ? {
            tripId: r.trip_id,
            orderId: r.order_id,
            serviceCategoryCode: r.service_category,
            status: r.status,
            version: Number(r.version),
            pickup: {
              label: r.pickup_label,
              addressSummary: r.pickup_label,
              latitude: r.pickup_lat,
              longitude: r.pickup_lng
            },
            drop: {
              label: r.drop_label,
              addressSummary: r.drop_label,
              latitude: r.drop_lat,
              longitude: r.drop_lng
            },
            maskedContactAvailable: true,
            startedAtUtc: r.started_at?.toISOString() ?? null,
            updatedAtUtc: r.updated_at.toISOString(),
            requiresStartPin: r.requires_start_pin,
            estimatedTotalMinor: null,
            currency: "INR"
          }
        : null,
      serverNowUtc: new Date().toISOString()
    };
  }

  async transitionTrip(driverId: string, tripId: string, input: TripTransitionInput): Promise<TripTransitionDto> {
    return this.withTx(async (client) => {
      const replayRes = await client.query<{ to_status: TripStatus; created_at: Date }>(
        "SELECT to_status, created_at FROM trip_events WHERE trip_id = $1 AND idempotency_key = $2",
        [tripId, input.idempotencyKey]
      );

      const tripRes = await client.query<{
        id: string;
        order_id: string;
        driver_id: string;
        status: TripStatus;
        version: string;
        requires_start_pin: boolean;
        start_pin_hash: string | null;
        service_category: ServiceCategory;
        estimated_distance_m: number;
        estimated_duration_s: number;
      }>(
        `SELECT t.id, t.order_id, t.driver_id, t.status, t.version::text,
                t.requires_start_pin, t.start_pin_hash,
                o.service_category, o.estimated_distance_m, o.estimated_duration_s
         FROM trips t
         JOIN orders o ON o.id = t.order_id
         WHERE t.id = $1 FOR UPDATE`,
        [tripId]
      );
      const trip = tripRes.rows[0];
      if (!trip) {
        throw new AppError({ httpStatus: 404, code: ErrorCodes.TRIP_NOT_FOUND, message: "Trip not found." });
      }
      if (trip.driver_id !== driverId) {
        throw new AppError({
          httpStatus: 403,
          code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
          message: "Trip belongs to another driver."
        });
      }

      if (replayRes.rows[0]) {
        const stmtRes = await client.query<{ id: string }>(
          "SELECT id FROM earning_statements WHERE trip_id = $1 LIMIT 1",
          [trip.id]
        );
        return {
          tripId: trip.id,
          status: trip.status,
          version: Number(trip.version),
          earningSourceRefId: stmtRes.rows[0]?.id ?? null,
          transitionedAtUtc: replayRes.rows[0].created_at.toISOString()
        };
      }

      const currentVersion = Number(trip.version);
      if (currentVersion !== input.expectedVersion) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.VERSION_CONFLICT,
          message: `Stale trip version: expected ${input.expectedVersion}, actual ${currentVersion}.`
        });
      }

      const allowedTargets = VALID_TRIP_TRANSITIONS[trip.status] ?? [];
      if (!allowedTargets.includes(input.targetStatus)) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.INVALID_TRIP_TRANSITION,
          message: `Invalid trip transition ${trip.status} -> ${input.targetStatus}.`
        });
      }

      if (input.targetStatus === "IN_PROGRESS" && trip.requires_start_pin) {
        const pinHash = input.verificationPin
          ? createHash("sha256").update(input.verificationPin).digest("hex")
          : "";
        if (!pinHash || pinHash !== trip.start_pin_hash) {
          throw new AppError({
            httpStatus: 422,
            code: ErrorCodes.INVALID_START_PIN,
            message: "Valid start verification PIN is required."
          });
        }
      }

      const nextVersion = currentVersion + 1;
      const now = new Date();
      let earningStatementId: string | null = null;

      await client.query(
        `UPDATE trips
         SET status = $2::trip_status,
             version = $3,
             en_route_at = CASE WHEN $2 = 'EN_ROUTE_TO_PICKUP' THEN NOW() ELSE en_route_at END,
             arrived_at = CASE WHEN $2 = 'ARRIVED_AT_PICKUP' THEN NOW() ELSE arrived_at END,
             started_at = CASE WHEN $2 = 'IN_PROGRESS' THEN NOW() ELSE started_at END,
             completed_at = CASE WHEN $2 = 'COMPLETED' THEN NOW() ELSE completed_at END,
             cancelled_at = CASE WHEN $2 = 'CANCELLED' THEN NOW() ELSE cancelled_at END,
             cancellation_reason_code = COALESCE($4, cancellation_reason_code),
             updated_at = NOW()
         WHERE id = $1`,
        [trip.id, input.targetStatus, nextVersion, input.reasonCode ?? null]
      );

      if (input.targetStatus === "COMPLETED") {
        const offerRes = await client.query<{ quoted_incentive_amount_minor: string }>(
          "SELECT quoted_incentive_amount_minor::text FROM order_offers WHERE order_id = $1 AND driver_id = $2 AND status = 'ACCEPTED' LIMIT 1",
          [trip.order_id, driverId]
        );
        const incentiveMinor = Number(offerRes.rows[0]?.quoted_incentive_amount_minor ?? "0");
        const breakdown = computeFareBreakdownMinor({
          category: trip.service_category,
          distanceMeters: trip.estimated_distance_m,
          durationSeconds: trip.estimated_duration_s,
          incentiveMinor
        });

        await client.query(
          "UPDATE trips SET final_amount_minor = $2 WHERE id = $1",
          [trip.id, breakdown.netEarningMinor]
        );
        await client.query(
          "UPDATE orders SET status = 'COMPLETED', completed_at = NOW(), updated_at = NOW() WHERE id = $1",
          [trip.order_id]
        );
        await client.query(
          "UPDATE drivers SET total_completed_trips = total_completed_trips + 1, updated_at = NOW() WHERE id = $1",
          [driverId]
        );

        const stmtRes = await client.query<{ id: string }>(
          `INSERT INTO earning_statements (
             driver_id, trip_id, source_type, source_id, fare_config_version,
             base_fare_minor, distance_fare_minor, time_fare_minor, incentive_minor,
             platform_fee_minor, tax_minor, gross_amount_minor, net_amount_minor, calculation_snapshot
           ) VALUES ($1, $2, 'TRIP_COMPLETED', $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12::jsonb)
           ON CONFLICT (driver_id, source_type, source_id) DO UPDATE SET driver_id = EXCLUDED.driver_id
           RETURNING id`,
          [
            driverId,
            trip.id,
            breakdown.fareConfigVersion,
            breakdown.baseFareMinor,
            breakdown.distanceFareMinor,
            breakdown.timeFareMinor,
            breakdown.incentiveMinor,
            breakdown.platformFeeMinor,
            breakdown.taxMinor,
            breakdown.grossAmountMinor,
            breakdown.netEarningMinor,
            JSON.stringify(breakdown)
          ]
        );
        earningStatementId = stmtRes.rows[0]!.id;

        if (breakdown.netEarningMinor > 0) {
          const driverAccountId = await this.ensureDriverWalletAccount(client, driverId);
          const platformAccountId = await this.ensurePlatformClearingAccount(client);
          await this.postDoubleEntryTx(client, {
            sourceType: "TRIP_EARNING",
            sourceId: trip.id,
            idempotencyKey: `trip-earning:${trip.id}`,
            description: `Trip earning finalized (${trip.service_category})`,
            statementId: earningStatementId,
            legs: [
              { accountId: platformAccountId, direction: "DEBIT", amountMinor: breakdown.netEarningMinor },
              { accountId: driverAccountId, direction: "CREDIT", amountMinor: breakdown.netEarningMinor }
            ]
          });
        }
      } else if (input.targetStatus === "CANCELLED") {
        await client.query(
          "UPDATE orders SET status = 'CANCELLED', completed_at = NOW(), updated_at = NOW() WHERE id = $1",
          [trip.order_id]
        );
      }

      await client.query(
        `INSERT INTO trip_events (trip_id, event_type, from_status, to_status, actor_type, actor_id, idempotency_key, metadata)
         VALUES ($1, 'STATUS_TRANSITION', $2, $3, 'DRIVER', $4, $5, $6::jsonb)`,
        [
          trip.id,
          trip.status,
          input.targetStatus,
          driverId,
          input.idempotencyKey,
          JSON.stringify({ reasonCode: input.reasonCode ?? null })
        ]
      );

      return {
        tripId: trip.id,
        status: input.targetStatus,
        version: nextVersion,
        earningSourceRefId: earningStatementId,
        transitionedAtUtc: now.toISOString()
      };
    });
  }

  async getOrders(
    driverId: string,
    statusFilter: "all" | "completed" | "cancelled",
    cursor?: string | null,
    limit = 20
  ): Promise<PagedDto<OrderHistorySummaryDto>> {
    const offset = cursor ? Math.max(0, Number(cursor) || 0) : 0;
    const statusClause =
      statusFilter === "completed"
        ? "AND t.status = 'COMPLETED'"
        : statusFilter === "cancelled"
          ? "AND t.status = 'CANCELLED'"
          : "AND t.status IN ('COMPLETED','CANCELLED','DISPUTED')";

    const res = await this.pool.query<{
      order_id: string;
      trip_id: string;
      service_category: ServiceCategory;
      status: TripStatus;
      pickup_label: string;
      drop_label: string;
      ts: Date;
      statement_id: string | null;
      final_amount_minor: string | null;
    }>(
      `SELECT t.order_id, t.id AS trip_id, o.service_category, t.status, o.pickup_label, o.drop_label,
              COALESCE(t.completed_at, t.cancelled_at, t.updated_at) AS ts,
              es.id AS statement_id, t.final_amount_minor::text
       FROM trips t
       JOIN orders o ON o.id = t.order_id
       LEFT JOIN earning_statements es ON es.trip_id = t.id
       WHERE t.driver_id = $1 ${statusClause}
       ORDER BY ts DESC
       LIMIT $2 OFFSET $3`,
      [driverId, limit + 1, offset]
    );
    const hasMore = res.rows.length > limit;
    const slice = res.rows.slice(0, limit);
    return {
      items: slice.map((r) => ({
        orderId: r.order_id,
        tripId: r.trip_id,
        serviceCategoryCode: r.service_category,
        status: r.status,
        pickupSummary: r.pickup_label,
        dropSummary: r.drop_label,
        completedOrCancelledAtUtc: r.ts.toISOString(),
        earningStatementId: r.statement_id,
        finalizedAmountMinor: r.final_amount_minor ? Number(r.final_amount_minor) : null,
        currency: "INR"
      })),
      nextCursor: hasMore ? String(offset + limit) : null,
      hasMore
    };
  }

  async getOrderDetail(driverId: string, orderId: string): Promise<OrderDetailDto> {
    const res = await this.pool.query<{
      order_id: string;
      trip_id: string;
      driver_id: string;
      service_category: ServiceCategory;
      status: TripStatus;
      pickup_label: string;
      drop_label: string;
      estimated_distance_m: number;
      estimated_duration_s: number;
      statement_id: string | null;
      final_amount_minor: string | null;
      cancellation_reason_code: string | null;
      completed_at: Date | null;
      cancelled_at: Date | null;
    }>(
      `SELECT t.order_id, t.id AS trip_id, t.driver_id, o.service_category, t.status,
              o.pickup_label, o.drop_label, o.estimated_distance_m, o.estimated_duration_s,
              es.id AS statement_id, t.final_amount_minor::text, t.cancellation_reason_code,
              t.completed_at, t.cancelled_at
       FROM trips t
       JOIN orders o ON o.id = t.order_id
       LEFT JOIN earning_statements es ON es.trip_id = t.id
       WHERE t.order_id = $1`,
      [orderId]
    );
    const r = res.rows[0];
    if (!r) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.ORDER_NOT_FOUND, message: "Order not found." });
    }
    if (r.driver_id !== driverId) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
        message: "Order belongs to another driver."
      });
    }
    const eventsRes = await this.pool.query<{ to_status: string | null; created_at: Date }>(
      "SELECT to_status, created_at FROM trip_events WHERE trip_id = $1 ORDER BY created_at ASC",
      [r.trip_id]
    );
    return {
      orderId: r.order_id,
      tripId: r.trip_id,
      serviceCategoryCode: r.service_category,
      status: r.status,
      pickupSummary: r.pickup_label,
      dropSummary: r.drop_label,
      distanceMeters: r.estimated_distance_m,
      durationSeconds: r.estimated_duration_s,
      earningStatementId: r.statement_id,
      timelineEvents: eventsRes.rows.map((e) => `${e.to_status ?? "EVENT"} @ ${e.created_at.toISOString()}`),
      earningSourceRefId: r.statement_id,
      finalizedAmountMinor: r.final_amount_minor ? Number(r.final_amount_minor) : null,
      currency: "INR",
      cancellationReasonCode: r.cancellation_reason_code,
      completedOrCancelledAtUtc: (r.completed_at ?? r.cancelled_at)?.toISOString() ?? null
    };
  }

  async getEarningsSummary(driverId: string, period: string): Promise<EarningsSummaryDto> {
    const interval = period === "month" ? "30 days" : period === "week" ? "7 days" : "1 day";
    const res = await this.pool.query<{
      base_total: string;
      inc_total: string;
      net_total: string;
    }>(
      `SELECT COALESCE(SUM(base_fare_minor + distance_fare_minor + time_fare_minor - platform_fee_minor - tax_minor), 0)::text AS base_total,
              COALESCE(SUM(incentive_minor), 0)::text AS inc_total,
              COALESCE(SUM(net_amount_minor), 0)::text AS net_total
       FROM earning_statements
       WHERE driver_id = $1 AND created_at >= NOW() - $2::interval`,
      [driverId, interval]
    );
    const row = res.rows[0];
    return {
      period,
      baseMinor: Number(row?.base_total ?? "0"),
      incentiveMinor: Number(row?.inc_total ?? "0"),
      referralMinor: 0,
      adjustmentMinor: 0,
      finalizedTotalMinor: Number(row?.net_total ?? "0"),
      currency: "INR",
      lastUpdatedUtc: new Date().toISOString()
    };
  }

  async getEarningsTransactions(
    driverId: string,
    cursor?: string | null,
    limit = 20
  ): Promise<PagedDto<LedgerEntryDto>> {
    const offset = cursor ? Math.max(0, Number(cursor) || 0) : 0;
    const res = await this.pool.query<{
      entry_id: string;
      source_type: string;
      source_id: string;
      direction: "DEBIT" | "CREDIT";
      amount_minor: string;
      created_at: Date;
      description: string;
      statement_id: string | null;
    }>(
      `SELECT le.id AS entry_id, lt.source_type, lt.source_id, le.direction,
              le.amount_minor::text, le.created_at, lt.description, lt.statement_id
       FROM ledger_entries le
       JOIN ledger_transactions lt ON lt.id = le.transaction_id
       JOIN wallet_accounts wa ON wa.id = le.account_id
       WHERE wa.driver_id = $1
       ORDER BY le.created_at DESC
       LIMIT $2 OFFSET $3`,
      [driverId, limit + 1, offset]
    );
    const hasMore = res.rows.length > limit;
    const slice = res.rows.slice(0, limit);
    return {
      items: slice.map((r) => {
        const signed = r.direction === "CREDIT" ? Number(r.amount_minor) : -Number(r.amount_minor);
        const entryType =
          r.source_type === "PAYOUT_REQUEST"
            ? "PAYOUT_DEBIT"
            : r.source_type === "PAYOUT_REVERSAL"
              ? "PAYOUT_REVERSAL"
              : r.source_type === "REFERRAL_BONUS"
                ? "REFERRAL_BONUS"
                : r.source_type === "CAMPAIGN_REWARD"
                  ? "INCENTIVE"
                  : "TRIP_EARNING";
        return {
          entryId: r.entry_id,
          sourceType: r.source_type,
          sourceId: r.source_id,
          entryType,
          amountMinor: signed,
          currency: "INR",
          balanceAfterMinor: 0,
          createdAtUtc: r.created_at.toISOString(),
          description: r.description,
          statementId: r.statement_id
        };
      }),
      nextCursor: hasMore ? String(offset + limit) : null,
      hasMore
    };
  }

  async getEarningStatement(driverId: string, statementId: string): Promise<EarningStatementDto> {
    const res = await this.pool.query<{
      id: string;
      driver_id: string;
      trip_id: string | null;
      fare_config_version: string;
      quote_snapshot_id: string | null;
      base_fare_minor: string;
      distance_fare_minor: string;
      time_fare_minor: string;
      incentive_minor: string;
      platform_fee_minor: string;
      tax_minor: string;
      net_amount_minor: string;
      created_at: Date;
    }>("SELECT * FROM earning_statements WHERE id = $1", [statementId]);
    const r = res.rows[0];
    if (!r) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.STATEMENT_NOT_FOUND, message: "Statement not found." });
    }
    if (r.driver_id !== driverId) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
        message: "Statement belongs to another driver."
      });
    }
    return {
      statementId: r.id,
      earningSourceRefId: r.id,
      tripId: r.trip_id ?? "",
      fareConfigVersion: r.fare_config_version,
      quoteSnapshotId: r.quote_snapshot_id ?? r.id,
      baseFareMinor: Number(r.base_fare_minor),
      distanceFareMinor: Number(r.distance_fare_minor),
      timeFareMinor: Number(r.time_fare_minor),
      incentiveMinor: Number(r.incentive_minor),
      platformFeeMinor: Number(r.platform_fee_minor),
      taxMinor: Number(r.tax_minor),
      netEarningMinor: Number(r.net_amount_minor),
      currency: "INR",
      finalizedAtUtc: r.created_at.toISOString()
    };
  }

  async getWallet(driverId: string): Promise<WalletSummaryDto> {
    const balRes = await this.pool.query<{ balance: string }>(
      `SELECT COALESCE(SUM(CASE WHEN le.direction = 'CREDIT' THEN le.amount_minor ELSE -le.amount_minor END), 0)::text AS balance
       FROM ledger_entries le
       JOIN wallet_accounts wa ON wa.id = le.account_id
       WHERE wa.driver_id = $1`,
      [driverId]
    );
    const payoutRes = await this.pool.query<{ pending_amt: string; paid_amt: string }>(
      `SELECT COALESCE(SUM(CASE WHEN status IN ('REQUESTED','PROCESSING') THEN amount_minor ELSE 0 END), 0)::text AS pending_amt,
              COALESCE(SUM(CASE WHEN status = 'PAID' THEN amount_minor ELSE 0 END), 0)::text AS paid_amt
       FROM payouts WHERE driver_id = $1`,
      [driverId]
    );
    const todayRes = await this.getEarningsSummary(driverId, "today");
    return {
      availableMinor: Math.max(0, Number(balRes.rows[0]?.balance ?? "0")),
      pendingMinor: Number(payoutRes.rows[0]?.pending_amt ?? "0"),
      settledMinor: Number(payoutRes.rows[0]?.paid_amt ?? "0"),
      todayEarningMinor: todayRes.finalizedTotalMinor,
      currency: "INR",
      minimumPayoutMinor: this.minPayoutMinor,
      maximumPayoutMinor: this.maxPayoutMinor,
      livePayoutsEnabled: this.opts.livePayoutsEnabled,
      derivedAtUtc: new Date().toISOString()
    };
  }

  async getPayoutAccounts(driverId: string): Promise<PayoutAccountDto[]> {
    const res = await this.pool.query<{
      provider_token: string;
      account_holder_masked: string;
      account_last4: string;
      ifsc_code: string;
      verification_status: "PENDING_VERIFICATION" | "VERIFIED" | "DISABLED";
    }>(
      `SELECT provider_token, account_holder_masked, account_last4, ifsc_code, verification_status
       FROM payout_accounts WHERE driver_id = $1 ORDER BY created_at DESC`,
      [driverId]
    );
    return res.rows.map((r) => ({
      payoutAccountToken: r.provider_token,
      accountLabel: r.account_holder_masked,
      maskedAccountNumber: `•••• ${r.account_last4}`,
      ifscCode: r.ifsc_code,
      status: r.verification_status
    }));
  }

  async addPayoutAccount(driverId: string, input: CreatePayoutAccountInput): Promise<PayoutAccountDto> {
    const last4 = maskBankAccountLast4(input.accountNumber);
    const token = `pa_${randomUUID().replace(/-/g, "").slice(0, 16)}`;
    const res = await this.pool.query<{
      provider_token: string;
      account_holder_masked: string;
      account_last4: string;
      ifsc_code: string;
      verification_status: "PENDING_VERIFICATION" | "VERIFIED" | "DISABLED";
    }>(
      `INSERT INTO payout_accounts (
         driver_id, provider_token, account_holder_masked, account_last4, ifsc_code, verification_status, idempotency_key
       ) VALUES ($1, $2, $3, $4, $5, 'VERIFIED', $6)
       ON CONFLICT (idempotency_key) DO UPDATE SET updated_at = NOW()
       RETURNING provider_token, account_holder_masked, account_last4, ifsc_code, verification_status`,
      [driverId, token, input.accountHolderName.trim(), last4, input.ifscCode.toUpperCase(), input.idempotencyKey]
    );
    const r = res.rows[0]!;
    return {
      payoutAccountToken: r.provider_token,
      accountLabel: r.account_holder_masked,
      maskedAccountNumber: `•••• ${r.account_last4}`,
      ifscCode: r.ifsc_code,
      status: r.verification_status
    };
  }

  async requestPayout(driverId: string, input: RequestPayoutInput): Promise<PayoutDto> {
    if (!this.opts.livePayoutsEnabled) {
      throw new AppError({
        httpStatus: 503,
        code: ErrorCodes.LIVE_PAYOUTS_DISABLED,
        message: "Live payouts are currently disabled pending payment provider activation."
      });
    }
    if (input.amountMinor < this.minPayoutMinor) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.PAYOUT_BELOW_MINIMUM,
        message: `Payout amount must be at least ${this.minPayoutMinor} paise.`
      });
    }
    if (input.amountMinor > this.maxPayoutMinor) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.PAYOUT_ABOVE_MAXIMUM,
        message: `Payout amount cannot exceed ${this.maxPayoutMinor} paise.`
      });
    }

    return this.withTx(async (client) => {
      const existing = await client.query<{
        id: string;
        driver_id: string;
        amount_minor: string;
        status: PayoutStatus;
        requested_at: Date;
        settled_at: Date | null;
        failure_code: string | null;
      }>("SELECT * FROM payouts WHERE idempotency_key = $1", [input.idempotencyKey]);
      if (existing.rows[0]) {
        const e = existing.rows[0];
        if (e.driver_id !== driverId) {
          throw new AppError({ httpStatus: 409, code: ErrorCodes.IDEMPOTENCY_CONFLICT, message: "Idempotency conflict." });
        }
        return {
          payoutId: e.id,
          payoutAccountToken: input.payoutAccountToken,
          amountMinor: Number(e.amount_minor),
          currency: "INR",
          status: e.status,
          requestedAtUtc: e.requested_at.toISOString(),
          settledAtUtc: e.settled_at?.toISOString() ?? null,
          failureReason: e.failure_code
        };
      }

      const acctRes = await client.query<{ id: string; verification_status: string }>(
        "SELECT id, verification_status FROM payout_accounts WHERE driver_id = $1 AND provider_token = $2",
        [driverId, input.payoutAccountToken]
      );
      const acct = acctRes.rows[0];
      if (!acct || acct.verification_status !== "VERIFIED") {
        throw new AppError({
          httpStatus: 422,
          code: ErrorCodes.INVALID_PAYOUT_ACCOUNT,
          message: "Payout account is missing or not verified."
        });
      }

      const driverWalletId = await this.ensureDriverWalletAccount(client, driverId);
      await client.query("SELECT id FROM wallet_accounts WHERE id = $1 FOR UPDATE", [driverWalletId]);

      const balRes = await client.query<{ balance: string }>(
        `SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0)::text AS balance
         FROM ledger_entries WHERE account_id = $1`,
        [driverWalletId]
      );
      const availableMinor = Number(balRes.rows[0]?.balance ?? "0");
      if (availableMinor < input.amountMinor) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.INSUFFICIENT_BALANCE,
          message: "Insufficient available wallet balance for payout."
        });
      }

      const payoutIns = await client.query<{ id: string; requested_at: Date }>(
        `INSERT INTO payouts (driver_id, payout_account_id, amount_minor, currency, status, idempotency_key, processed_at)
         VALUES ($1, $2, $3, 'INR', 'PROCESSING', $4, NOW())
         RETURNING id, requested_at`,
        [driverId, acct.id, input.amountMinor, input.idempotencyKey]
      );
      const payoutId = payoutIns.rows[0]!.id;
      const platformAccountId = await this.ensurePlatformClearingAccount(client);
      await this.postDoubleEntryTx(client, {
        sourceType: "PAYOUT_REQUEST",
        sourceId: payoutId,
        idempotencyKey: `payout-debit:${payoutId}`,
        description: `Payout requested (${input.payoutAccountToken})`,
        legs: [
          { accountId: driverWalletId, direction: "DEBIT", amountMinor: input.amountMinor },
          { accountId: platformAccountId, direction: "CREDIT", amountMinor: input.amountMinor }
        ]
      });

      return {
        payoutId,
        payoutAccountToken: input.payoutAccountToken,
        amountMinor: input.amountMinor,
        currency: "INR",
        status: "PROCESSING",
        requestedAtUtc: payoutIns.rows[0]!.requested_at.toISOString(),
        settledAtUtc: null,
        failureReason: null
      };
    });
  }

  async getPayouts(driverId: string, cursor?: string | null, limit = 20): Promise<PagedDto<PayoutDto>> {
    const offset = cursor ? Math.max(0, Number(cursor) || 0) : 0;
    const res = await this.pool.query<{
      id: string;
      provider_token: string;
      amount_minor: string;
      status: PayoutStatus;
      requested_at: Date;
      settled_at: Date | null;
      failure_code: string | null;
    }>(
      `SELECT p.id, pa.provider_token, p.amount_minor::text, p.status, p.requested_at, p.settled_at, p.failure_code
       FROM payouts p
       JOIN payout_accounts pa ON pa.id = p.payout_account_id
       WHERE p.driver_id = $1
       ORDER BY p.requested_at DESC
       LIMIT $2 OFFSET $3`,
      [driverId, limit + 1, offset]
    );
    const hasMore = res.rows.length > limit;
    const slice = res.rows.slice(0, limit);
    return {
      items: slice.map((r) => ({
        payoutId: r.id,
        payoutAccountToken: r.provider_token,
        amountMinor: Number(r.amount_minor),
        currency: "INR",
        status: r.status,
        requestedAtUtc: r.requested_at.toISOString(),
        settledAtUtc: r.settled_at?.toISOString() ?? null,
        failureReason: r.failure_code
      })),
      nextCursor: hasMore ? String(offset + limit) : null,
      hasMore
    };
  }

  async processPayoutWebhook(
    provider: string,
    providerEventId: string,
    payloadHash: string,
    payoutId: string,
    outcome: "PAID" | "FAILED",
    failureCode?: string | null
  ): Promise<PayoutDto> {
    return this.withTx(async (client) => {
      const dedupe = await client.query<{ id: string }>(
        "SELECT id FROM provider_webhook_events WHERE provider = $1 AND provider_event_id = $2",
        [provider, providerEventId]
      );
      const payoutRes = await client.query<{
        id: string;
        driver_id: string;
        provider_token: string;
        amount_minor: string;
        status: PayoutStatus;
        requested_at: Date;
        settled_at: Date | null;
        failure_code: string | null;
      }>(
        `SELECT p.id, p.driver_id, pa.provider_token, p.amount_minor::text, p.status, p.requested_at, p.settled_at, p.failure_code
         FROM payouts p JOIN payout_accounts pa ON pa.id = p.payout_account_id
         WHERE p.id = $1 FOR UPDATE`,
        [payoutId]
      );
      const p = payoutRes.rows[0];
      if (!p) {
        throw new AppError({ httpStatus: 404, code: "PAYOUT_NOT_FOUND", message: "Payout not found." });
      }
      if (dedupe.rows[0] || p.status === "PAID" || p.status === "FAILED" || p.status === "REVERSED") {
        return {
          payoutId: p.id,
          payoutAccountToken: p.provider_token,
          amountMinor: Number(p.amount_minor),
          currency: "INR",
          status: p.status,
          requestedAtUtc: p.requested_at.toISOString(),
          settledAtUtc: p.settled_at?.toISOString() ?? null,
          failureReason: p.failure_code
        };
      }

      await client.query(
        `INSERT INTO provider_webhook_events (provider, provider_event_id, signature_valid, payload_hash)
         VALUES ($1, $2, TRUE, $3)`,
        [provider, providerEventId, payloadHash]
      );

      if (outcome === "PAID") {
        const upd = await client.query<{ settled_at: Date }>(
          "UPDATE payouts SET status = 'PAID', settled_at = NOW(), updated_at = NOW() WHERE id = $1 RETURNING settled_at",
          [p.id]
        );
        return {
          payoutId: p.id,
          payoutAccountToken: p.provider_token,
          amountMinor: Number(p.amount_minor),
          currency: "INR",
          status: "PAID",
          requestedAtUtc: p.requested_at.toISOString(),
          settledAtUtc: upd.rows[0]!.settled_at.toISOString(),
          failureReason: null
        };
      }

      // FAILED -> reverse wallet debit strictly once via double-entry ledger
      await client.query(
        "UPDATE payouts SET status = 'FAILED', failure_code = $2, updated_at = NOW() WHERE id = $1",
        [p.id, failureCode ?? "PROVIDER_TRANSFER_FAILED"]
      );
      const driverWalletId = await this.ensureDriverWalletAccount(client, p.driver_id);
      const platformAccountId = await this.ensurePlatformClearingAccount(client);
      await this.postDoubleEntryTx(client, {
        sourceType: "PAYOUT_REVERSAL",
        sourceId: p.id,
        idempotencyKey: `payout-reversal:${p.id}`,
        description: `Payout failure reversal (${failureCode ?? "FAILED"})`,
        legs: [
          { accountId: platformAccountId, direction: "DEBIT", amountMinor: Number(p.amount_minor) },
          { accountId: driverWalletId, direction: "CREDIT", amountMinor: Number(p.amount_minor) }
        ]
      });

      return {
        payoutId: p.id,
        payoutAccountToken: p.provider_token,
        amountMinor: Number(p.amount_minor),
        currency: "INR",
        status: "FAILED",
        requestedAtUtc: p.requested_at.toISOString(),
        settledAtUtc: null,
        failureReason: failureCode ?? "PROVIDER_TRANSFER_FAILED"
      };
    });
  }

  async getCampaigns(driverId: string): Promise<CampaignDto[]> {
    const res = await this.pool.query<{
      id: string;
      name: string;
      category: "DAILY_INCENTIVE" | "WEEKLY_CHALLENGE" | "PRIORITY_REWARD";
      terms_version: string;
      terms_summary: string;
      rules: { targetCount?: number };
      reward_config: { rewardMinor?: number };
      ends_at: Date;
      progress: { currentCount?: number; isQualified?: boolean } | null;
      eligibility_status: string | null;
    }>(
      `SELECT ic.id, ic.name, ic.category, ic.terms_version, ic.terms_summary, ic.rules, ic.reward_config, ic.ends_at,
              dcp.progress, dcp.eligibility_status
       FROM incentive_campaigns ic
       LEFT JOIN driver_campaign_progress dcp ON dcp.campaign_id = ic.id AND dcp.driver_id = $1
       WHERE ic.status = 'ACTIVE' AND ic.ends_at > NOW()`,
      [driverId]
    );
    return res.rows.map((r) => ({
      campaignId: r.id,
      title: r.name,
      category: r.category,
      termsVersion: r.terms_version,
      termsSummary: r.terms_summary,
      targetCount: r.rules.targetCount ?? 10,
      currentCount: r.progress?.currentCount ?? 0,
      rewardMinor: r.reward_config.rewardMinor ?? 0,
      currency: "INR",
      isQualified: Boolean(r.progress?.isQualified),
      isDriverEligible: (r.eligibility_status ?? "ELIGIBLE") === "ELIGIBLE",
      ineligibilityReason: r.eligibility_status === "INELIGIBLE" ? "Driver ineligible per campaign terms" : null,
      rewardCreditedSourceRefId: null,
      expiresAtUtc: r.ends_at.toISOString()
    }));
  }

  async getReferralSummary(driverId: string): Promise<ReferralSummaryDto> {
    const codeRes = await this.pool.query<{ id: string; code: string }>(
      "SELECT id, code FROM referral_codes WHERE owner_driver_id = $1 LIMIT 1",
      [driverId]
    );
    const code = codeRes.rows[0]?.code ?? `DRV${driverId.slice(0, 6).toUpperCase()}`;
    return {
      referralCode: code,
      shareLink: `https://drivepartner.app/invite/${code}`,
      qualifiedCount: 0,
      pendingCount: 0,
      totalEarnedMinor: 0,
      currency: "INR",
      isEligibleForReferral: true,
      ineligibilityReason: null,
      referrals: []
    };
  }

  async applyReferralCode(
    driverId: string,
    userId: string,
    referralCode: string,
    _idempotencyKey: string
  ): Promise<ApplyReferralDto> {
    const codeRes = await this.pool.query<{ id: string; owner_driver_id: string }>(
      "SELECT id, owner_driver_id FROM referral_codes WHERE code = $1 AND status = 'ACTIVE'",
      [referralCode.toUpperCase()]
    );
    const codeRow = codeRes.rows[0];
    if (!codeRow) {
      throw new AppError({
        httpStatus: 404,
        code: ErrorCodes.REFERRAL_CODE_NOT_FOUND,
        message: "Referral code not found or inactive."
      });
    }
    if (codeRow.owner_driver_id === driverId) {
      throw new AppError({
        httpStatus: 409,
        code: ErrorCodes.SELF_REFERRAL_FORBIDDEN,
        message: "Self-referral is not allowed."
      });
    }
    const ins = await this.pool.query<{ id: string }>(
      `INSERT INTO referrals (referral_code_id, referred_user_id, referred_driver_id, status)
       VALUES ($1, $2, $3, 'PENDING')
       ON CONFLICT (referred_driver_id) DO UPDATE SET updated_at = NOW()
       RETURNING id`,
      [codeRow.id, userId, driverId]
    );
    return {
      referralId: ins.rows[0]!.id,
      status: "PENDING",
      message: "Referral code attributed; qualification pending qualifying trips."
    };
  }

  async getEmergencyContacts(driverId: string): Promise<EmergencyContactDto[]> {
    const res = await this.pool.query<{
      id: string;
      name: string;
      phone_masked: string;
      relationship: string;
    }>("SELECT id, name, phone_masked, relationship FROM emergency_contacts WHERE driver_id = $1 ORDER BY created_at DESC", [
      driverId
    ]);
    return res.rows.map((r) => ({
      contactId: r.id,
      name: r.name,
      phoneMasked: r.phone_masked,
      relationship: r.relationship
    }));
  }

  async addEmergencyContact(driverId: string, input: EmergencyContactInput): Promise<EmergencyContactDto> {
    const masked = maskPhone(input.phoneNumber);
    const res = await this.pool.query<{ id: string; name: string; phone_masked: string; relationship: string }>(
      `INSERT INTO emergency_contacts (driver_id, name, phone_e164, phone_masked, relationship, idempotency_key)
       VALUES ($1, $2, $3, $4, $5, NULLIF($6, ''))
       ON CONFLICT (idempotency_key) DO UPDATE SET name = EXCLUDED.name
       RETURNING id, name, phone_masked, relationship`,
      [driverId, input.name, input.phoneNumber, masked, input.relationship, input.idempotencyKey]
    );
    const r = res.rows[0]!;
    return {
      contactId: r.id,
      name: r.name,
      phoneMasked: r.phone_masked,
      relationship: r.relationship
    };
  }

  async createSosIncident(
    driverId: string,
    input: SosTriggerInput,
    responderOutcome: { acknowledged: boolean; responderReference?: string | null }
  ): Promise<SosAlertDto> {
    const status = responderOutcome.acknowledged ? "ACKNOWLEDGED" : "FAILED";
    const res = await this.pool.query<{ id: string; acknowledged_at: Date | null }>(
      `INSERT INTO safety_incidents (
         driver_id, trip_id, incident_type, status, responder_reference, location, description, idempotency_key, acknowledged_at
       ) VALUES (
         $1, $2, 'SOS', $3, $4,
         ST_SetSRID(ST_MakePoint($6, $5), 4326)::geography,
         'Emergency SOS Alert', $7,
         CASE WHEN $3 = 'ACKNOWLEDGED' THEN NOW() ELSE NULL END
       )
       ON CONFLICT (idempotency_key) DO UPDATE SET driver_id = EXCLUDED.driver_id
       RETURNING id, acknowledged_at`,
      [
        driverId,
        input.activeTripId ?? null,
        status,
        responderOutcome.responderReference ?? null,
        input.latitude,
        input.longitude,
        input.idempotencyKey
      ]
    );
    const row = res.rows[0]!;
    return {
      alertId: row.id,
      status,
      responderReference: responderOutcome.responderReference ?? null,
      acknowledgedAtUtc: row.acknowledged_at?.toISOString() ?? null,
      message: responderOutcome.acknowledged
        ? "SOS alert acknowledged by emergency responder."
        : "No emergency responder webhook acknowledged this SOS alert. Call 112 immediately."
    };
  }

  async submitIncidentReport(driverId: string, input: IncidentReportInput): Promise<IncidentReportDto> {
    const res = await this.pool.query<{ id: string; status: string; created_at: Date }>(
      `INSERT INTO safety_incidents (driver_id, trip_id, incident_type, status, description, idempotency_key)
       VALUES ($1, $2, $3, 'OPEN', $4, NULLIF($5, ''))
       ON CONFLICT (idempotency_key) DO UPDATE SET description = EXCLUDED.description
       RETURNING id, status, created_at`,
      [driverId, input.relatedTripId ?? null, input.category, input.description, input.idempotencyKey]
    );
    const r = res.rows[0]!;
    return {
      incidentId: r.id,
      status: r.status,
      createdAtUtc: r.created_at.toISOString()
    };
  }

  async getInsuranceStatus(driverId: string): Promise<InsuranceStatusDto> {
    const res = await this.pool.query<{
      provider_name: string;
      policy_reference: string;
      status: string;
      disclaimer_text: string;
    }>(
      `SELECT provider_name, policy_reference, status, disclaimer_text
       FROM insurance_records
       WHERE driver_id = $1 AND status = 'CONFIRMED' AND (valid_until IS NULL OR valid_until > NOW())
       ORDER BY updated_at DESC
       LIMIT 1`,
      [driverId]
    );
    const row = res.rows[0];
    if (!row) {
      return {
        hasConfirmedPolicy: false,
        providerName: null,
        policyReference: null,
        approvedDisclaimerText: "No active verified group insurance policy is linked to this account yet."
      };
    }
    return {
      hasConfirmedPolicy: true,
      providerName: row.provider_name,
      policyReference: row.policy_reference,
      approvedDisclaimerText: row.disclaimer_text
    };
  }

  async getSupportTickets(
    driverId: string,
    cursor?: string | null,
    limit = 20
  ): Promise<PagedDto<SupportTicketDto>> {
    const offset = cursor ? Math.max(0, Number(cursor) || 0) : 0;
    const res = await this.pool.query<{
      id: string;
      category: string;
      subject: string;
      status: TicketStatus;
      created_at: Date;
      updated_at: Date;
    }>(
      `SELECT id, category, subject, status, created_at, updated_at
       FROM support_tickets WHERE driver_id = $1 ORDER BY updated_at DESC LIMIT $2 OFFSET $3`,
      [driverId, limit + 1, offset]
    );
    const hasMore = res.rows.length > limit;
    return {
      items: res.rows.slice(0, limit).map((r) => ({
        ticketId: r.id,
        category: r.category,
        subject: r.subject,
        status: r.status,
        createdAtUtc: r.created_at.toISOString(),
        updatedAtUtc: r.updated_at.toISOString()
      })),
      nextCursor: hasMore ? String(offset + limit) : null,
      hasMore
    };
  }

  async createSupportTicket(driverId: string, input: CreateSupportTicketInput): Promise<SupportTicketDto> {
    for (const k of input.attachmentStorageKeys) {
      assertPrivateObjectKey(k);
    }
    const res = await this.pool.query<{
      id: string;
      category: string;
      subject: string;
      status: TicketStatus;
      created_at: Date;
      updated_at: Date;
    }>(
      `INSERT INTO support_tickets (driver_id, category, subject, description, status, idempotency_key)
       VALUES ($1, $2, $3, $4, 'OPEN', NULLIF($5, ''))
       ON CONFLICT (idempotency_key) DO UPDATE SET updated_at = NOW()
       RETURNING id, category, subject, status, created_at, updated_at`,
      [driverId, input.category, input.subject, input.description, input.idempotencyKey]
    );
    const r = res.rows[0]!;
    return {
      ticketId: r.id,
      category: r.category,
      subject: r.subject,
      status: r.status,
      createdAtUtc: r.created_at.toISOString(),
      updatedAtUtc: r.updated_at.toISOString()
    };
  }

  async getSupportMessages(driverId: string, ticketId: string): Promise<SupportMessageDto[]> {
    const t = await this.pool.query<{ driver_id: string }>(
      "SELECT driver_id FROM support_tickets WHERE id = $1",
      [ticketId]
    );
    if (!t.rows[0]) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.TICKET_NOT_FOUND, message: "Ticket not found." });
    }
    if (t.rows[0].driver_id !== driverId) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
        message: "Ticket belongs to another driver."
      });
    }
    const res = await this.pool.query<{
      id: string;
      sender_type: string;
      message: string;
      attachment_object_key: string | null;
      created_at: Date;
    }>(
      `SELECT id, sender_type, message, attachment_object_key, created_at
       FROM support_messages
       WHERE ticket_id = $1 AND is_internal_note = FALSE
       ORDER BY created_at ASC`,
      [ticketId]
    );
    return res.rows.map((m) => ({
      messageId: m.id,
      senderRole: m.sender_type,
      body: m.message,
      attachmentIds: m.attachment_object_key ? [m.attachment_object_key] : [],
      isInternalNote: false,
      sentAtUtc: m.created_at.toISOString()
    }));
  }

  async sendSupportMessage(
    driverId: string,
    ticketId: string,
    input: CreateSupportMessageInput
  ): Promise<SupportMessageDto> {
    for (const k of input.attachmentStorageKeys) {
      assertPrivateObjectKey(k);
    }
    const t = await this.pool.query<{ driver_id: string }>(
      "SELECT driver_id FROM support_tickets WHERE id = $1",
      [ticketId]
    );
    if (!t.rows[0]) {
      throw new AppError({ httpStatus: 404, code: ErrorCodes.TICKET_NOT_FOUND, message: "Ticket not found." });
    }
    if (t.rows[0].driver_id !== driverId) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
        message: "Ticket belongs to another driver."
      });
    }
    const res = await this.pool.query<{ id: string; created_at: Date }>(
      `INSERT INTO support_messages (ticket_id, sender_type, sender_id, message, attachment_object_key, is_internal_note, idempotency_key)
       VALUES ($1, 'DRIVER', $2, $3, $4, FALSE, NULLIF($5, ''))
       ON CONFLICT (idempotency_key) DO UPDATE SET message = EXCLUDED.message
       RETURNING id, created_at`,
      [ticketId, driverId, input.body, input.attachmentStorageKeys[0] ?? null, input.idempotencyKey]
    );
    return {
      messageId: res.rows[0]!.id,
      senderRole: "DRIVER",
      body: input.body,
      attachmentIds: input.attachmentStorageKeys,
      isInternalNote: false,
      sentAtUtc: res.rows[0]!.created_at.toISOString()
    };
  }

  async getNotifications(
    userId: string,
    cursor?: string | null,
    limit = 20
  ): Promise<PagedDto<AppNotificationDto>> {
    const offset = cursor ? Math.max(0, Number(cursor) || 0) : 0;
    const res = await this.pool.query<{
      id: string;
      dedupe_key: string;
      category: string;
      title_key: string;
      body_key: string;
      deep_link_route: string | null;
      read_at: Date | null;
      created_at: Date;
    }>(
      `SELECT id, dedupe_key, category, title_key, body_key, deep_link_route, read_at, created_at
       FROM notifications WHERE user_id = $1 ORDER BY created_at DESC LIMIT $2 OFFSET $3`,
      [userId, limit + 1, offset]
    );
    const hasMore = res.rows.length > limit;
    return {
      items: res.rows.slice(0, limit).map((n) => ({
        notificationId: n.id,
        dedupeKey: n.dedupe_key,
        category: n.category,
        title: n.title_key,
        body: n.body_key,
        deepLinkRoute: n.deep_link_route,
        isRead: n.read_at !== null,
        createdAtUtc: n.created_at.toISOString()
      })),
      nextCursor: hasMore ? String(offset + limit) : null,
      hasMore
    };
  }

  async markNotificationRead(userId: string, notificationId: string): Promise<void> {
    await this.pool.query(
      "UPDATE notifications SET read_at = NOW() WHERE id = $1 AND user_id = $2",
      [notificationId, userId]
    );
  }

  async getNotificationPreferences(userId: string): Promise<NotificationPreferencesDto> {
    const res = await this.pool.query<{
      order_alerts_enabled: boolean;
      earnings_alerts_enabled: boolean;
      campaign_alerts_enabled: boolean;
    }>("SELECT order_alerts_enabled, earnings_alerts_enabled, campaign_alerts_enabled FROM notification_preferences WHERE user_id = $1", [
      userId
    ]);
    return {
      orderAlertsEnabled: res.rows[0]?.order_alerts_enabled ?? true,
      earningsAlertsEnabled: res.rows[0]?.earnings_alerts_enabled ?? true,
      campaignAlertsEnabled: res.rows[0]?.campaign_alerts_enabled ?? true
    };
  }

  async updateNotificationPreferences(
    userId: string,
    input: NotificationPreferencesDto
  ): Promise<NotificationPreferencesDto> {
    await this.pool.query(
      `INSERT INTO notification_preferences (user_id, order_alerts_enabled, earnings_alerts_enabled, campaign_alerts_enabled, updated_at)
       VALUES ($1, $2, $3, $4, NOW())
       ON CONFLICT (user_id) DO UPDATE
       SET order_alerts_enabled = $2, earnings_alerts_enabled = $3, campaign_alerts_enabled = $4, updated_at = NOW()`,
      [userId, input.orderAlertsEnabled, input.earningsAlertsEnabled, input.campaignAlertsEnabled]
    );
    return input;
  }

  async adminReviewDocument(
    actorId: string,
    driverId: string,
    documentId: string,
    decision: "APPROVED" | "REJECTED",
    reason?: string | null,
    requestId?: string
  ): Promise<AuthSessionDto> {
    return this.withTx(async (client) => {
      const docRes = await client.query<{ id: string; driver_id: string; status: DocumentStatus }>(
        "SELECT id, driver_id, status FROM driver_documents WHERE id = $1 FOR UPDATE",
        [documentId]
      );
      const doc = docRes.rows[0];
      if (!doc || doc.driver_id !== driverId) {
        throw new AppError({ httpStatus: 404, code: ErrorCodes.DOCUMENT_NOT_FOUND, message: "Document not found." });
      }
      await client.query(
        `UPDATE driver_documents
         SET status = $2::document_status,
             rejection_reason_code = $3,
             reviewed_at = NOW(),
             updated_at = NOW()
         WHERE id = $1`,
        [doc.id, decision, decision === "REJECTED" ? (reason ?? "Verification rejected") : null]
      );
      await client.query(
        `INSERT INTO verification_events (document_id, from_status, to_status, reason_code)
         VALUES ($1, $2, $3, $4)`,
        [doc.id, doc.status, decision, reason ?? null]
      );

      const allDocs = await client.query<{ document_type: string; status: DocumentStatus }>(
        "SELECT document_type, status FROM driver_documents WHERE driver_id = $1",
        [driverId]
      );
      const docMap = new Map(allDocs.rows.map((d) => [d.document_type, d.status]));
      const allApproved = MANDATORY_DOC_TYPES.every((t) => docMap.get(t) === "APPROVED");
      const anyRejected = allDocs.rows.some((d) => d.status === "REJECTED");

      const nextAccountStatus: AccountStatus = allApproved
        ? "ACTIVE"
        : anyRejected
          ? "REJECTED"
          : "PENDING_REVIEW";

      const drvRes = await client.query<{ user_id: string }>(
        `UPDATE drivers
         SET activation_status = $2::account_status,
             documents_verified = $3,
             updated_at = NOW()
         WHERE id = $1
         RETURNING user_id`,
        [driverId, nextAccountStatus, allApproved]
      );
      if (allApproved) {
        await client.query(
          "UPDATE vehicles SET is_approved = TRUE, status = 'APPROVED', updated_at = NOW() WHERE driver_id = $1 AND status <> 'RETIRED'",
          [driverId]
        );
      }
      await client.query(
        `INSERT INTO audit_logs (actor_role, action, resource_type, resource_id, request_id, metadata)
         VALUES ('ADMIN', $1, 'driver_document', $2, $3, $4::jsonb)`,
        [
          `DOCUMENT_${decision}`,
          documentId,
          requestId ?? null,
          JSON.stringify({ actorId, driverId, nextAccountStatus })
        ]
      );
      return this.buildSessionDto(client, drvRes.rows[0]!.user_id);
    });
  }

  async adminCreateAndOfferOrder(
    actorId: string,
    input: AdminCreateOrderInput,
    requestId?: string
  ): Promise<{ orderId: string; offerId: string; driverId: string }> {
    return this.withTx(async (client) => {
      const code = `ORD-${randomUUID().slice(0, 8).toUpperCase()}`;
      const orderRes = await client.query<{ id: string }>(
        `INSERT INTO orders (
           public_order_code, service_category, status, pickup_label, pickup_point,
           drop_label, drop_point, estimated_distance_m, estimated_duration_s
         ) VALUES (
           $1, $2::service_category, 'PENDING_DISPATCH', $3,
           ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography,
           $6,
           ST_SetSRID(ST_MakePoint($8, $7), 4326)::geography,
           $9, $10
         )
         RETURNING id`,
        [
          code,
          input.serviceCategoryCode,
          input.pickupLabel,
          input.pickupLat,
          input.pickupLng,
          input.dropLabel,
          input.dropLat,
          input.dropLng,
          input.distanceMeters,
          input.durationSeconds
        ]
      );
      const orderId = orderRes.rows[0]!.id;
      const breakdown = computeFareBreakdownMinor({
        category: input.serviceCategoryCode,
        distanceMeters: input.distanceMeters,
        durationSeconds: input.durationSeconds,
        incentiveMinor: input.incentiveMinor ?? 0
      });
      const expiresAt = new Date(Date.now() + (input.expiresInSeconds ?? 30) * 1000);
      const offerRes = await client.query<{ id: string }>(
        `INSERT INTO order_offers (
           order_id, driver_id, status, offer_sequence, priority_label, pickup_distance_m,
           expires_at, quoted_base_amount_minor, quoted_incentive_amount_minor, quote_snapshot
         ) VALUES ($1, $2, 'OFFERED', 1, $3, 850, $4, $5, $6, $7::jsonb)
         RETURNING id`,
        [
          orderId,
          input.driverId,
          input.priorityLabel ?? null,
          expiresAt,
          breakdown.netEarningMinor - breakdown.incentiveMinor,
          breakdown.incentiveMinor,
          JSON.stringify(breakdown)
        ]
      );
      await client.query(
        `INSERT INTO audit_logs (actor_role, action, resource_type, resource_id, request_id, metadata)
         VALUES ('ADMIN', 'DISPATCH_OFFER_CREATED', 'order_offer', $1, $2, $3::jsonb)`,
        [offerRes.rows[0]!.id, requestId ?? null, JSON.stringify({ actorId, driverId: input.driverId })]
      );
      return { orderId, offerId: offerRes.rows[0]!.id, driverId: input.driverId };
    });
  }

  async adminGetAuditLogs(limit = 50): Promise<AuditLogDto[]> {
    const res = await this.pool.query<{
      id: string;
      actor_id: string | null;
      actor_role: string;
      action: string;
      resource_type: string;
      resource_id: string | null;
      request_id: string | null;
      created_at: Date;
    }>("SELECT id, actor_id, actor_role, action, resource_type, resource_id, request_id, created_at FROM audit_logs ORDER BY created_at DESC LIMIT $1", [
      limit
    ]);
    return res.rows.map((r) => ({
      logId: r.id,
      actorId: r.actor_id,
      actorRole: r.actor_role,
      action: r.action,
      resourceType: r.resource_type,
      resourceId: r.resource_id,
      requestId: r.request_id,
      createdAtUtc: r.created_at.toISOString()
    }));
  }
}
