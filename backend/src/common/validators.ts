import { z } from "zod";
import { AppError, ErrorCodes } from "./errors.js";

const RAW_AADHAAR_REGEX = /\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/;
const INDIAN_PHONE_E164_REGEX = /^\+91[6-9]\d{9}$/;
const VEHICLE_REG_REGEX = /^[A-Z]{2}\d{1,2}[A-Z]{0,3}\d{4}$/;
const IFSC_REGEX = /^[A-Z]{4}0[A-Z0-9]{6}$/;
const BANK_ACCOUNT_REGEX = /^\d{9,18}$/;
const PIN_CODE_REGEX = /^[1-9]\d{5}$/;

export function containsRawAadhaarDigits(value: string | null | undefined): boolean {
  if (!value) return false;
  return RAW_AADHAAR_REGEX.test(value.trim());
}

export function assertPrivateObjectKey(objectKey: string): void {
  const clean = objectKey.trim();
  if (!clean || /^https?:\/\//i.test(clean) || clean.includes("..")) {
    throw new AppError({
      httpStatus: 422,
      code: ErrorCodes.INVALID_INPUT,
      message: "Storage object key must be a non-empty private storage path, not a public URL."
    });
  }
}

export function normalizeVehicleRegistration(raw: string): string {
  const normalized = raw.toUpperCase().replace(/[\s-]/g, "");
  if (!VEHICLE_REG_REGEX.test(normalized)) {
    throw new AppError({
      httpStatus: 422,
      code: ErrorCodes.INVALID_INPUT,
      message: "Invalid Indian vehicle registration format (expected e.g. RJ14AB1234)."
    });
  }
  return normalized;
}

export function maskPhone(phoneE164: string): string {
  const digits = phoneE164.replace(/\D/g, "");
  const last4 = digits.slice(-4).padStart(4, "0");
  return `+91 XXXXX X${last4}`;
}

export function maskVehicleReg(normalizedReg: string): string {
  if (normalizedReg.length <= 4) return normalizedReg;
  return `${normalizedReg.slice(0, 4)}••${normalizedReg.slice(-4)}`;
}

export function maskBankAccountLast4(accountNumber: string): string {
  return accountNumber.slice(-4);
}

export const VerifySessionSchema = z
  .object({
    idToken: z.string().min(10).optional(),
    firebaseIdToken: z.string().min(10).optional(),
    phoneE164: z.string().regex(INDIAN_PHONE_E164_REGEX).optional().nullable()
  })
  .refine((v) => Boolean(v.idToken || v.firebaseIdToken), {
    message: "Either idToken or firebaseIdToken is required."
  });

export const DriverProfileSchema = z.object({
  fullName: z.string().trim().min(2).max(120),
  cityCode: z.string().trim().min(2).max(16),
  preferredLanguage: z.enum(["en", "hi"]).default("en"),
  dateOfBirth: z.string().trim().max(32).optional().nullable(),
  gender: z.string().trim().max(24).optional().nullable(),
  addressLine: z.string().trim().max(255).optional().nullable(),
  stateCode: z.string().trim().max(16).optional().nullable(),
  pinCode: z
    .string()
    .trim()
    .regex(PIN_CODE_REGEX)
    .optional()
    .nullable(),
  profilePhotoObjectKey: z.string().trim().max(512).optional().nullable()
});

export const VehicleDetailsSchema = z.object({
  serviceTypeCode: z.enum(["BIKE", "AUTO", "CAB", "DELIVERY"]),
  registrationNumber: z.string().trim().min(6).max(24),
  vehicleModel: z.string().trim().min(2).max(64),
  brand: z.string().trim().max(64).default(""),
  color: z.string().trim().max(32).default(""),
  year: z.number().int().min(1995).max(2035).optional().nullable(),
  vehiclePhotoObjectKey: z.string().trim().max(512).optional().nullable()
});

export const DocumentUploadIntentSchema = z.object({
  documentType: z.enum([
    "DRIVING_LICENSE",
    "VEHICLE_RC",
    "VEHICLE_INSURANCE",
    "PERMIT",
    "PAN_CARD",
    "AADHAAR_COMPLIANT_REF",
    "PROFILE_PHOTO"
  ]),
  mimeType: z.enum(["image/jpeg", "image/png", "image/webp", "application/pdf"]),
  sizeBytes: z.number().int().min(1).max(10 * 1024 * 1024),
  checksumSha256: z.string().max(128).optional().nullable()
});

export const ConfirmDocumentUploadSchema = z.object({
  documentId: z.string().trim().min(1),
  storageObjectKey: z.string().trim().min(4).max(512),
  expiresOnUtc: z.string().trim().optional().nullable(),
  maskedReference: z.string().trim().max(64).optional().nullable()
});

export const UpdateDutyStatusSchema = z.object({
  idempotencyKey: z.string().trim().min(4).max(128),
  latitude: z.number().finite().min(-90).max(90).optional().nullable(),
  longitude: z.number().finite().min(-180).max(180).optional().nullable(),
  accuracyMeters: z.number().finite().min(0).max(5000).optional().nullable(),
  capturedAtUtc: z.string().trim().optional().nullable()
});

export const LocationPingSchema = z.object({
  latitude: z.number().finite().min(-90).max(90),
  longitude: z.number().finite().min(-180).max(180),
  accuracyMeters: z.number().finite().min(0).max(500),
  bearingDegrees: z.number().finite().min(0).max(360).optional().nullable(),
  speedMps: z.number().finite().min(0).max(80).optional().nullable(),
  capturedAtUtc: z.string().trim().min(10),
  dutySessionId: z.string().trim().optional().nullable()
});

export const DriverPreferencesSchema = z.object({
  preferredZoneIds: z.array(z.string().trim().min(1)).max(50),
  autoBoostEnabled: z.boolean(),
  activeServiceCategoryCode: z.enum(["BIKE", "AUTO", "CAB", "DELIVERY"])
});

export const AcceptOfferSchema = z.object({
  idempotencyKey: z.string().trim().min(4).max(128),
  clientAcceptedAtUtc: z.string().trim().optional().nullable()
});

export const RejectOfferSchema = z.object({
  reasonCode: z.string().trim().max(64).optional().nullable(),
  idempotencyKey: z.string().trim().min(4).max(128)
});

export const TripTransitionSchema = z.object({
  targetStatus: z.enum([
    "ASSIGNED",
    "EN_ROUTE_TO_PICKUP",
    "ARRIVED_AT_PICKUP",
    "IN_PROGRESS",
    "COMPLETED",
    "CANCELLED",
    "DISPUTED"
  ]),
  expectedVersion: z.number().int().min(1),
  idempotencyKey: z.string().trim().min(4).max(128),
  reasonCode: z.string().trim().max(64).optional().nullable(),
  latitude: z.number().finite().min(-90).max(90).optional().nullable(),
  longitude: z.number().finite().min(-180).max(180).optional().nullable(),
  verificationPin: z.string().trim().max(12).optional().nullable()
});

export const CreatePayoutAccountSchema = z.object({
  accountHolderName: z.string().trim().min(2).max(120),
  accountNumber: z.string().trim().regex(BANK_ACCOUNT_REGEX),
  ifscCode: z.string().trim().toUpperCase().regex(IFSC_REGEX),
  idempotencyKey: z.string().trim().min(4).max(128)
});

export const RequestPayoutSchema = z.object({
  payoutAccountToken: z.string().trim().min(4).max(128),
  amountMinor: z.number().int().positive(),
  idempotencyKey: z.string().trim().min(4).max(128)
});

export const ApplyReferralSchema = z.object({
  referralCode: z.string().trim().min(3).max(32),
  idempotencyKey: z.string().trim().min(4).max(128)
});

export const EmergencyContactSchema = z.object({
  name: z.string().trim().min(2).max(120),
  phoneNumber: z.string().trim().min(10).max(20),
  relationship: z.string().trim().min(2).max(64),
  idempotencyKey: z.string().trim().max(128).default("")
});

export const SosTriggerSchema = z.object({
  latitude: z.number().finite().min(-90).max(90),
  longitude: z.number().finite().min(-180).max(180),
  activeTripId: z.string().trim().optional().nullable(),
  idempotencyKey: z.string().trim().min(4).max(128)
});

export const IncidentReportSchema = z.object({
  category: z.string().trim().min(2).max(64),
  description: z.string().trim().min(5).max(2000),
  relatedTripId: z.string().trim().optional().nullable(),
  idempotencyKey: z.string().trim().max(128).default("")
});

export const CreateSupportTicketSchema = z.object({
  category: z.string().trim().min(2).max(64),
  subject: z.string().trim().min(3).max(200),
  description: z.string().trim().min(5).max(4000),
  attachmentStorageKeys: z.array(z.string().trim()).max(10).default([]),
  idempotencyKey: z.string().trim().max(128).default("")
});

export const CreateSupportMessageSchema = z.object({
  body: z.string().trim().min(1).max(2000),
  attachmentStorageKeys: z.array(z.string().trim()).max(5).default([]),
  idempotencyKey: z.string().trim().max(128).default("")
});

export const NotificationPreferencesSchema = z.object({
  orderAlertsEnabled: z.boolean(),
  earningsAlertsEnabled: z.boolean(),
  campaignAlertsEnabled: z.boolean()
});
