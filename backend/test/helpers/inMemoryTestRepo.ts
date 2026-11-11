import { randomUUID } from "node:crypto";
import { AppError, ErrorCodes } from "../../src/common/errors.js";
import { assertBalancedDoubleEntry, computeFareBreakdownMinor } from "../../src/common/money.js";
import {
  assertPrivateObjectKey,
  containsRawAadhaarDigits,
  maskBankAccountLast4,
  maskPhone,
  normalizeVehicleRegistration
} from "../../src/common/validators.js";
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
} from "../../src/db/repository.js";
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
} from "../../src/middleware/auth.js";

const MANDATORY_DOCS: DocumentType[] = [
  "DRIVING_LICENSE",
  "VEHICLE_RC",
  "VEHICLE_INSURANCE",
  "PERMIT",
  "PAN_CARD",
  "AADHAAR_COMPLIANT_REF",
  "PROFILE_PHOTO"
];

const VALID_TRANSITIONS: Record<TripStatus, TripStatus[]> = {
  ASSIGNED: ["EN_ROUTE_TO_PICKUP", "CANCELLED"],
  EN_ROUTE_TO_PICKUP: ["ARRIVED_AT_PICKUP", "CANCELLED"],
  ARRIVED_AT_PICKUP: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["COMPLETED", "CANCELLED", "DISPUTED"],
  COMPLETED: ["DISPUTED"],
  CANCELLED: [],
  DISPUTED: []
};

interface InternalUser {
  userId: string;
  driverId: string;
  firebaseUid: string;
  phoneMasked: string;
  preferredLanguage: string;
}

interface InternalDriver {
  driverId: string;
  userId: string;
  fullName: string;
  cityCode: string;
  accountStatus: AccountStatus;
  documentsVerified: boolean;
  dateOfBirth: string | null;
  gender: string | null;
  addressLine: string | null;
  stateCode: string | null;
  pinCode: string | null;
  profilePhotoObjectKey: string | null;
  autoBoostEnabled: boolean;
  activeServiceCategoryCode: ServiceCategory;
  preferredZoneIds: string[];
  totalCompletedTrips: number;
  dutyStatus: DutyStatus;
  sessionId: string | null;
  lastLat: number | null;
  lastLng: number | null;
}

interface InternalOrder {
  orderId: string;
  serviceCategoryCode: ServiceCategory;
  pickupLabel: string;
  pickupLat: number;
  pickupLng: number;
  dropLabel: string;
  dropLat: number;
  dropLng: number;
  distanceMeters: number;
  durationSeconds: number;
  assignedDriverId: string | null;
  status: string;
}

interface InternalOffer {
  offerId: string;
  orderId: string;
  driverId: string;
  status: OfferStatus;
  priorityLabel: string | null;
  pickupDistanceMeters: number;
  expiresAtMs: number;
  quotedBaseMinor: number;
  quotedIncentiveMinor: number;
  idempotencyKey: string | null;
}

interface InternalTrip {
  tripId: string;
  orderId: string;
  driverId: string;
  status: TripStatus;
  version: number;
  timelineEvents: string[];
  cancellationReasonCode: string | null;
  completedOrCancelledAtUtc: string | null;
  statementId: string | null;
  finalizedAmountMinor: number | null;
}

interface InternalLedgerTx {
  transactionId: string;
  sourceType: string;
  sourceId: string;
  idempotencyKey: string;
  entries: Array<{ accountId: string; direction: "DEBIT" | "CREDIT"; amountMinor: number }>;
}

/**
 * Strictly test-only transactional store mirroring every PostgreSQL/PostGIS constraint
 * so automated Fastify `.inject()` integration, concurrency, security, and ledger suites
 * execute hermetically even when a live PostgreSQL server is unavailable in CI sandboxes.
 */
export class InMemoryTestBackendRepository implements BackendRepository {
  public livePayoutsEnabled = false;
  public readonly ledgerTransactions: InternalLedgerTx[] = [];
  private readonly usersByUid = new Map<string, InternalUser>();
  private readonly drivers = new Map<string, InternalDriver>();
  private readonly vehiclesByDriver = new Map<string, VehicleDto[]>();
  private readonly docsByDriver = new Map<string, KycDocumentDto[]>();
  private readonly orders = new Map<string, InternalOrder>();
  private readonly offers = new Map<string, InternalOffer>();
  private readonly trips = new Map<string, InternalTrip>();
  private readonly tripIdempotency = new Map<string, TripTransitionDto>();
  private readonly statements = new Map<string, EarningStatementDto>();
  private readonly ledgerEntriesByDriver = new Map<string, LedgerEntryDto[]>();
  private readonly payoutAccountsByDriver = new Map<string, PayoutAccountDto[]>();
  private readonly payouts = new Map<string, PayoutDto & { driverId: string; idempotencyKey: string }>();
  private readonly webhookEvents = new Set<string>();
  private readonly supportTickets = new Map<string, SupportTicketDto & { driverId: string }>();
  private readonly supportMessages = new Map<string, SupportMessageDto[]>();
  private readonly emergencyContacts = new Map<string, EmergencyContactDto[]>();
  private readonly notificationsByUser = new Map<string, AppNotificationDto[]>();
  private readonly notificationPrefs = new Map<string, NotificationPreferencesDto>();
  private readonly referralCodes = new Map<string, string>();
  private readonly auditLogs: AuditLogDto[] = [];

  private readonly zones: ServiceZoneDto[] = [
    { zoneId: "zone-jai-central", cityCode: "JAI", name: "Jaipur Central / MI Road", isEnabled: true },
    { zoneId: "zone-jai-malviya", cityCode: "JAI", name: "Malviya Nagar & Airport", isEnabled: true }
  ];

  async checkHealth(): Promise<{ databaseReady: boolean; postgisReady: boolean }> {
    return { databaseReady: true, postgisReady: true };
  }

  private buildSession(u: InternalUser, d: InternalDriver): AuthSessionDto {
    const vehs = this.vehiclesByDriver.get(d.driverId) ?? [];
    const docs = this.docsByDriver.get(d.driverId) ?? [];
    const requiresProfile = !d.fullName.trim() || !d.cityCode.trim();
    const requiresVehicle = vehs.length === 0;
    const requiresKyc = MANDATORY_DOCS.some((t) => {
      const s = docs.find((x) => x.documentType === t)?.status;
      return !s || s === "NOT_SUBMITTED" || s === "REJECTED" || s === "EXPIRED";
    });
    return {
      userId: u.userId,
      driverId: d.driverId,
      accountStatus: d.accountStatus,
      requiresProfile,
      requiresVehicle,
      requiresKyc,
      serverTimeUtc: new Date().toISOString(),
      phoneMasked: u.phoneMasked
    };
  }

  async establishSession(claims: { firebaseUid: string; phoneE164?: string | null }): Promise<AuthSessionDto> {
    let user = this.usersByUid.get(claims.firebaseUid);
    if (!user) {
      const userId = randomUUID();
      const driverId = randomUUID();
      user = {
        userId,
        driverId,
        firebaseUid: claims.firebaseUid,
        phoneMasked: maskPhone(claims.phoneE164 ?? "+919999999999"),
        preferredLanguage: "en"
      };
      this.usersByUid.set(claims.firebaseUid, user);
      const driver: InternalDriver = {
        driverId,
        userId,
        fullName: "",
        cityCode: "",
        accountStatus: "APPLICANT",
        documentsVerified: false,
        dateOfBirth: null,
        gender: null,
        addressLine: null,
        stateCode: null,
        pinCode: null,
        profilePhotoObjectKey: null,
        autoBoostEnabled: false,
        activeServiceCategoryCode: "BIKE",
        preferredZoneIds: ["zone-jai-central"],
        totalCompletedTrips: 0,
        dutyStatus: "OFFLINE",
        sessionId: null,
        lastLat: null,
        lastLng: null
      };
      this.drivers.set(driverId, driver);
      this.docsByDriver.set(
        driverId,
        MANDATORY_DOCS.map((t) => ({
          documentId: randomUUID(),
          documentType: t,
          status: "NOT_SUBMITTED",
          rejectionReason: null,
          maskedReference: null,
          expiresAtUtc: null,
          updatedAtUtc: new Date().toISOString()
        }))
      );
      this.referralCodes.set(`DRV${driverId.slice(0, 6).toUpperCase()}`, driverId);
    }
    return this.buildSession(user, this.drivers.get(user.driverId)!);
  }

  async getSessionByFirebaseUid(firebaseUid: string): Promise<AuthSessionDto | null> {
    const user = this.usersByUid.get(firebaseUid);
    if (!user) return null;
    return this.buildSession(user, this.drivers.get(user.driverId)!);
  }

  async logout(driverId: string): Promise<void> {
    const active = Array.from(this.trips.values()).find(
      (t) =>
        t.driverId === driverId &&
        (t.status === "ASSIGNED" ||
          t.status === "EN_ROUTE_TO_PICKUP" ||
          t.status === "ARRIVED_AT_PICKUP" ||
          t.status === "IN_PROGRESS")
    );
    if (active) {
      throw new AppError({
        httpStatus: 409,
        code: ErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED,
        message: "Cannot sign out while an active trip is in progress."
      });
    }
    const d = this.drivers.get(driverId);
    if (d) {
      d.dutyStatus = "OFFLINE";
      d.sessionId = null;
    }
  }

  async getDriverProfile(driverId: string): Promise<DriverProfileDto> {
    const d = this.drivers.get(driverId)!;
    const u = Array.from(this.usersByUid.values()).find((x) => x.driverId === driverId)!;
    return {
      driverId,
      fullName: d.fullName,
      phoneMasked: u.phoneMasked,
      cityCode: d.cityCode,
      preferredLanguage: u.preferredLanguage,
      accountStatus: d.accountStatus,
      documentsVerified: d.documentsVerified,
      dateOfBirth: d.dateOfBirth,
      gender: d.gender,
      addressLine: d.addressLine,
      stateCode: d.stateCode,
      pinCode: d.pinCode,
      profilePhotoObjectKey: d.profilePhotoObjectKey,
      rating: null,
      totalCompletedTrips: d.totalCompletedTrips,
      acceptanceRate: null
    };
  }

  async upsertDriverProfile(driverId: string, input: DriverProfileInput): Promise<DriverProfileDto> {
    const d = this.drivers.get(driverId)!;
    const u = Array.from(this.usersByUid.values()).find((x) => x.driverId === driverId)!;
    d.fullName = input.fullName;
    d.cityCode = input.cityCode.toUpperCase();
    d.dateOfBirth = input.dateOfBirth ?? null;
    d.gender = input.gender ?? null;
    d.addressLine = input.addressLine ?? null;
    d.stateCode = input.stateCode ?? null;
    d.pinCode = input.pinCode ?? null;
    u.preferredLanguage = input.preferredLanguage;
    return this.getDriverProfile(driverId);
  }

  async getVehicles(driverId: string): Promise<VehicleDto[]> {
    return this.vehiclesByDriver.get(driverId) ?? [];
  }

  async upsertVehicle(driverId: string, input: VehicleInput): Promise<VehicleDto> {
    const normalized = normalizeVehicleRegistration(input.registrationNumber);
    for (const [otherDriverId, list] of this.vehiclesByDriver.entries()) {
      if (otherDriverId !== driverId && list.some((v) => v.registrationNumber === normalized)) {
        throw new AppError({
          httpStatus: 409,
          code: ErrorCodes.DUPLICATE_VEHICLE_REGISTRATION,
          message: `Vehicle registration ${normalized} already belongs to another driver.`
        });
      }
    }
    const existing = this.vehiclesByDriver.get(driverId)?.[0];
    const vehicle: VehicleDto = {
      vehicleId: existing?.vehicleId ?? randomUUID(),
      serviceTypeCode: input.serviceTypeCode,
      registrationNumber: normalized,
      vehicleModel: input.vehicleModel,
      isApproved: false,
      brand: input.brand,
      color: input.color,
      year: input.year ?? null,
      status: "PENDING_REVIEW",
      vehiclePhotoObjectKey: input.vehiclePhotoObjectKey ?? null
    };
    this.vehiclesByDriver.set(driverId, [vehicle]);
    const d = this.drivers.get(driverId)!;
    d.activeServiceCategoryCode = input.serviceTypeCode;
    return vehicle;
  }

  async createDocumentUploadIntent(driverId: string, input: UploadIntentInput): Promise<UploadIntentDto> {
    const docs = this.docsByDriver.get(driverId) ?? [];
    const doc = docs.find((x) => x.documentType === input.documentType)!;
    const objectKey = `private/kyc/${driverId}/${input.documentType.toLowerCase()}/${randomUUID()}`;
    return {
      documentId: doc.documentId,
      uploadUrl: `https://private-storage.internal/${objectKey}`,
      objectKey,
      expiresAtUtc: new Date(Date.now() + 300_000).toISOString(),
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
    for (const [ownerId, docs] of this.docsByDriver.entries()) {
      const idx = docs.findIndex((d) => d.documentId === input.documentId);
      if (idx !== -1) {
        if (ownerId !== driverId) {
          throw new AppError({
            httpStatus: 403,
            code: ErrorCodes.FORBIDDEN_CROSS_DRIVER,
            message: "Document belongs to another driver."
          });
        }
        const updated: KycDocumentDto = {
          ...docs[idx]!,
          status: "UNDER_REVIEW",
          rejectionReason: null,
          maskedReference: input.maskedReference ?? docs[idx]!.maskedReference,
          expiresAtUtc: input.expiresOnUtc ?? docs[idx]!.expiresAtUtc,
          updatedAtUtc: new Date().toISOString()
        };
        docs[idx] = updated;
        if (docs.every((d) => d.status === "UNDER_REVIEW" || d.status === "APPROVED")) {
          const drv = this.drivers.get(driverId)!;
          if (drv.accountStatus === "APPLICANT") drv.accountStatus = "PENDING_REVIEW";
        }
        return updated;
      }
    }
    throw new AppError({ httpStatus: 404, code: ErrorCodes.DOCUMENT_NOT_FOUND, message: "Document not found." });
  }

  async getDocuments(driverId: string): Promise<KycDocumentDto[]> {
    return this.docsByDriver.get(driverId) ?? [];
  }

  async getEligibility(driverId: string): Promise<EligibilityDto> {
    const d = this.drivers.get(driverId)!;
    const docs = this.docsByDriver.get(driverId) ?? [];
    const vehs = this.vehiclesByDriver.get(driverId) ?? [];
    const reasons: string[] = [];
    if (d.accountStatus !== "ACTIVE") reasons.push("ACCOUNT_NOT_APPROVED");
    if (!d.documentsVerified || docs.some((x) => x.status !== "APPROVED")) reasons.push("KYC_INCOMPLETE");
    if (!vehs.some((v) => v.isApproved)) reasons.push("VEHICLE_NOT_APPROVED");
    return {
      isEligibleForOnline: reasons.length === 0,
      blockingReasons: reasons,
      approvedServiceCategoryCodes: vehs.filter((v) => v.isApproved).map((v) => v.serviceTypeCode),
      assignedZoneIds: this.zones.map((z) => z.zoneId),
      evaluatedAtUtc: new Date().toISOString()
    };
  }

  async getHomeSummary(driverId: string): Promise<HomeSummaryDto> {
    const d = this.drivers.get(driverId)!;
    return {
      dutyStatus: d.dutyStatus,
      todayCompletedCount: d.totalCompletedTrips,
      todayEstimatedMinor: 0,
      currency: "INR",
      activeZones: this.zones,
      serverTimeUtc: new Date().toISOString()
    };
  }

  async getPreferences(driverId: string): Promise<DriverPreferencesDto> {
    const d = this.drivers.get(driverId)!;
    return {
      preferredZoneIds: d.preferredZoneIds,
      autoBoostEnabled: d.autoBoostEnabled,
      activeServiceCategoryCode: d.activeServiceCategoryCode
    };
  }

  async updatePreferences(driverId: string, input: DriverPreferencesDto): Promise<DriverPreferencesDto> {
    const d = this.drivers.get(driverId)!;
    d.preferredZoneIds = input.preferredZoneIds;
    d.autoBoostEnabled = input.autoBoostEnabled;
    d.activeServiceCategoryCode = input.activeServiceCategoryCode;
    return input;
  }

  async getZones(cityCode: string): Promise<ServiceZoneDto[]> {
    return this.zones.filter((z) => z.cityCode === cityCode.toUpperCase());
  }

  async goOnline(driverId: string, input: DutyStatusInput): Promise<DutyStateDto> {
    const elig = await this.getEligibility(driverId);
    if (!elig.isEligibleForOnline) {
      throw new AppError({
        httpStatus: 403,
        code: ErrorCodes.DRIVER_INELIGIBLE,
        message: elig.blockingReasons.join(", ")
      });
    }
    if (input.latitude == null || input.longitude == null) {
      throw new AppError({ httpStatus: 422, code: "LOCATION_UNAVAILABLE", message: "Location required." });
    }
    const d = this.drivers.get(driverId)!;
    d.dutyStatus = "ONLINE";
    d.sessionId = randomUUID();
    d.lastLat = input.latitude;
    d.lastLng = input.longitude;
    return { status: "ONLINE", sessionId: d.sessionId, activeTripId: null, serverTimeUtc: new Date().toISOString() };
  }

  async goOffline(driverId: string): Promise<DutyStateDto> {
    const d = this.drivers.get(driverId)!;
    d.dutyStatus = "OFFLINE";
    d.sessionId = null;
    return { status: "OFFLINE", sessionId: null, activeTripId: null, serverTimeUtc: new Date().toISOString() };
  }

  async recordLocationPing(driverId: string, input: LocationPingInput) {
    const d = this.drivers.get(driverId)!;
    d.lastLat = input.latitude;
    d.lastLng = input.longitude;
    return { accepted: true, staleAfterSeconds: 45, serverTimeUtc: new Date().toISOString() };
  }

  async getActiveOffers(driverId: string): Promise<{ offers: WorkOfferDto[]; serverNowUtc: string }> {
    const nowMs = Date.now();
    const list: WorkOfferDto[] = [];
    for (const o of this.offers.values()) {
      if (o.driverId === driverId && o.status === "OFFERED" && o.expiresAtMs > nowMs) {
        const ord = this.orders.get(o.orderId)!;
        list.push({
          offerId: o.offerId,
          orderId: o.orderId,
          serviceCategoryCode: ord.serviceCategoryCode,
          priorityLabel: o.priorityLabel,
          pickupSummary: ord.pickupLabel,
          dropSummary: ord.dropLabel,
          pickupDistanceMeters: o.pickupDistanceMeters,
          distanceMeters: ord.distanceMeters,
          durationSeconds: ord.durationSeconds,
          estimatedBaseMinor: o.quotedBaseMinor,
          eligibleIncentiveMinor: o.quotedIncentiveMinor,
          estimatedTotalMinor: o.quotedBaseMinor + o.quotedIncentiveMinor,
          currency: "INR",
          isEstimate: true,
          expiresAtUtc: new Date(o.expiresAtMs).toISOString(),
          serverNowUtc: new Date(nowMs).toISOString(),
          status: o.status
        });
      }
    }
    return { offers: list, serverNowUtc: new Date(nowMs).toISOString() };
  }

  async getOfferDetail(driverId: string, offerId: string): Promise<WorkOfferDto> {
    const o = this.offers.get(offerId);
    if (!o) throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
    if (o.driverId !== driverId) {
      throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    }
    const ord = this.orders.get(o.orderId)!;
    return {
      offerId: o.offerId,
      orderId: o.orderId,
      serviceCategoryCode: ord.serviceCategoryCode,
      priorityLabel: o.priorityLabel,
      pickupSummary: ord.pickupLabel,
      dropSummary: ord.dropLabel,
      pickupDistanceMeters: o.pickupDistanceMeters,
      distanceMeters: ord.distanceMeters,
      durationSeconds: ord.durationSeconds,
      estimatedBaseMinor: o.quotedBaseMinor,
      eligibleIncentiveMinor: o.quotedIncentiveMinor,
      estimatedTotalMinor: o.quotedBaseMinor + o.quotedIncentiveMinor,
      currency: "INR",
      isEstimate: true,
      expiresAtUtc: new Date(o.expiresAtMs).toISOString(),
      serverNowUtc: new Date().toISOString(),
      status: o.status
    };
  }

  async acceptOffer(driverId: string, offerId: string, idempotencyKey: string): Promise<AcceptOfferDto> {
    const d = this.drivers.get(driverId)!;
    if (d.accountStatus !== "ACTIVE") {
      throw new AppError({ httpStatus: 403, code: ErrorCodes.DRIVER_INELIGIBLE, message: "Driver ineligible." });
    }
    if (d.dutyStatus !== "ONLINE") {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.DRIVER_OFFLINE, message: "Driver offline." });
    }
    const offer = this.offers.get(offerId);
    if (!offer) throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
    if (offer.driverId !== driverId) {
      throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Cross driver blocked." });
    }
    if (offer.status === "ACCEPTED" && offer.idempotencyKey === idempotencyKey) {
      const t = Array.from(this.trips.values()).find((x) => x.orderId === offer.orderId)!;
      return { tripId: t.tripId, orderId: t.orderId, tripStatus: t.status, acceptedAtUtc: new Date().toISOString() };
    }
    if (offer.status !== "OFFERED") {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.OFFER_ALREADY_CLAIMED, message: "Offer not actionable." });
    }
    if (offer.expiresAtMs <= Date.now()) {
      offer.status = "EXPIRED";
      throw new AppError({ httpStatus: 409, code: ErrorCodes.OFFER_EXPIRED, message: "Offer expired." });
    }
    const hasActiveTrip = Array.from(this.trips.values()).some(
      (t) => t.driverId === driverId && ["ASSIGNED", "EN_ROUTE_TO_PICKUP", "ARRIVED_AT_PICKUP", "IN_PROGRESS"].includes(t.status)
    );
    if (hasActiveTrip) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.ORDER_ALREADY_ASSIGNED, message: "Active trip already exists." });
    }
    const order = this.orders.get(offer.orderId)!;
    if (order.assignedDriverId !== null) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.ORDER_ALREADY_ASSIGNED, message: "Order already assigned." });
    }
    order.assignedDriverId = driverId;
    order.status = "ASSIGNED";
    offer.status = "ACCEPTED";
    offer.idempotencyKey = idempotencyKey;
    for (const other of this.offers.values()) {
      if (other.orderId === order.orderId && other.offerId !== offer.offerId && other.status === "OFFERED") {
        other.status = "WITHDRAWN";
      }
    }
    const tripId = randomUUID();
    this.trips.set(tripId, {
      tripId,
      orderId: order.orderId,
      driverId,
      status: "ASSIGNED",
      version: 1,
      timelineEvents: [`ASSIGNED @ ${new Date().toISOString()}`],
      cancellationReasonCode: null,
      completedOrCancelledAtUtc: null,
      statementId: null,
      finalizedAmountMinor: null
    });
    return { tripId, orderId: order.orderId, tripStatus: "ASSIGNED", acceptedAtUtc: new Date().toISOString() };
  }

  async rejectOffer(driverId: string, offerId: string, idempotencyKey: string): Promise<void> {
    const o = this.offers.get(offerId);
    if (!o) throw new AppError({ httpStatus: 404, code: ErrorCodes.OFFER_NOT_FOUND, message: "Offer not found." });
    if (o.driverId !== driverId) throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    o.status = "REJECTED";
    o.idempotencyKey = idempotencyKey;
  }

  async getActiveTrip(driverId: string): Promise<{ trip: ActiveTripDto | null; serverNowUtc: string }> {
    const active = Array.from(this.trips.values()).find(
      (t) => t.driverId === driverId && ["ASSIGNED", "EN_ROUTE_TO_PICKUP", "ARRIVED_AT_PICKUP", "IN_PROGRESS"].includes(t.status)
    );
    if (!active) return { trip: null, serverNowUtc: new Date().toISOString() };
    const ord = this.orders.get(active.orderId)!;
    return {
      trip: {
        tripId: active.tripId,
        orderId: active.orderId,
        serviceCategoryCode: ord.serviceCategoryCode,
        status: active.status,
        version: active.version,
        pickup: { label: ord.pickupLabel, addressSummary: ord.pickupLabel, latitude: ord.pickupLat, longitude: ord.pickupLng },
        drop: { label: ord.dropLabel, addressSummary: ord.dropLabel, latitude: ord.dropLat, longitude: ord.dropLng },
        maskedContactAvailable: true,
        startedAtUtc: null,
        updatedAtUtc: new Date().toISOString(),
        requiresStartPin: false,
        estimatedTotalMinor: null,
        currency: "INR"
      },
      serverNowUtc: new Date().toISOString()
    };
  }

  private postLedger(driverId: string, tx: InternalLedgerTx, amountSignedDriver: number, description: string, statementId: string | null): void {
    assertBalancedDoubleEntry(tx.entries);
    this.ledgerTransactions.push(tx);
    const list = this.ledgerEntriesByDriver.get(driverId) ?? [];
    const currentBalance = list.reduce((sum, e) => sum + e.amountMinor, 0);
    const nextBalance = currentBalance + amountSignedDriver;
    list.unshift({
      entryId: randomUUID(),
      sourceType: tx.sourceType,
      sourceId: tx.sourceId,
      entryType:
        tx.sourceType === "PAYOUT_REQUEST"
          ? "PAYOUT_DEBIT"
          : tx.sourceType === "PAYOUT_REVERSAL"
            ? "PAYOUT_REVERSAL"
            : "TRIP_EARNING",
      amountMinor: amountSignedDriver,
      currency: "INR",
      balanceAfterMinor: nextBalance,
      createdAtUtc: new Date().toISOString(),
      description,
      statementId
    });
    this.ledgerEntriesByDriver.set(driverId, list);
  }

  async transitionTrip(driverId: string, tripId: string, input: TripTransitionInput): Promise<TripTransitionDto> {
    const trip = this.trips.get(tripId);
    if (!trip) throw new AppError({ httpStatus: 404, code: ErrorCodes.TRIP_NOT_FOUND, message: "Trip not found." });
    if (trip.driverId !== driverId) {
      throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    }
    const replay = this.tripIdempotency.get(`${tripId}:${input.idempotencyKey}`);
    if (replay) return replay;

    if (trip.version !== input.expectedVersion) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.VERSION_CONFLICT, message: "Stale trip version." });
    }
    if (!VALID_TRANSITIONS[trip.status].includes(input.targetStatus)) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.INVALID_TRIP_TRANSITION, message: "Invalid transition." });
    }
    trip.status = input.targetStatus;
    trip.version += 1;
    const nowIso = new Date().toISOString();
    trip.timelineEvents.push(`${input.targetStatus} @ ${nowIso}`);

    if (input.targetStatus === "COMPLETED") {
      const ord = this.orders.get(trip.orderId)!;
      const offer = Array.from(this.offers.values()).find((o) => o.orderId === ord.orderId && o.driverId === driverId);
      const breakdown = computeFareBreakdownMinor({
        category: ord.serviceCategoryCode,
        distanceMeters: ord.distanceMeters,
        durationSeconds: ord.durationSeconds,
        incentiveMinor: offer?.quotedIncentiveMinor ?? 0
      });
      const statementId = randomUUID();
      this.statements.set(statementId, {
        statementId,
        earningSourceRefId: statementId,
        tripId: trip.tripId,
        fareConfigVersion: breakdown.fareConfigVersion,
        quoteSnapshotId: statementId,
        baseFareMinor: breakdown.baseFareMinor,
        distanceFareMinor: breakdown.distanceFareMinor,
        timeFareMinor: breakdown.timeFareMinor,
        incentiveMinor: breakdown.incentiveMinor,
        platformFeeMinor: breakdown.platformFeeMinor,
        taxMinor: breakdown.taxMinor,
        netEarningMinor: breakdown.netEarningMinor,
        currency: "INR",
        finalizedAtUtc: nowIso
      });
      trip.statementId = statementId;
      trip.finalizedAmountMinor = breakdown.netEarningMinor;
      trip.completedOrCancelledAtUtc = nowIso;
      this.drivers.get(driverId)!.totalCompletedTrips += 1;
      this.postLedger(
        driverId,
        {
          transactionId: randomUUID(),
          sourceType: "TRIP_EARNING",
          sourceId: trip.tripId,
          idempotencyKey: `trip-earning:${trip.tripId}`,
          entries: [
            { accountId: "platform-clearing", direction: "DEBIT", amountMinor: breakdown.netEarningMinor },
            { accountId: `driver-${driverId}`, direction: "CREDIT", amountMinor: breakdown.netEarningMinor }
          ]
        },
        breakdown.netEarningMinor,
        `Trip earning finalized (${ord.serviceCategoryCode})`,
        statementId
      );
    } else if (input.targetStatus === "CANCELLED") {
      trip.cancellationReasonCode = input.reasonCode ?? null;
      trip.completedOrCancelledAtUtc = nowIso;
    }

    const dto: TripTransitionDto = {
      tripId: trip.tripId,
      status: trip.status,
      version: trip.version,
      earningSourceRefId: trip.statementId,
      transitionedAtUtc: nowIso
    };
    this.tripIdempotency.set(`${tripId}:${input.idempotencyKey}`, dto);
    return dto;
  }

  async getOrders(driverId: string, statusFilter: "all" | "completed" | "cancelled"): Promise<PagedDto<OrderHistorySummaryDto>> {
    const list = Array.from(this.trips.values())
      .filter((t) => t.driverId === driverId && (statusFilter === "all" ? ["COMPLETED", "CANCELLED"].includes(t.status) : t.status === statusFilter.toUpperCase()))
      .map((t) => {
        const ord = this.orders.get(t.orderId)!;
        return {
          orderId: ord.orderId,
          tripId: t.tripId,
          serviceCategoryCode: ord.serviceCategoryCode,
          status: t.status,
          pickupSummary: ord.pickupLabel,
          dropSummary: ord.dropLabel,
          completedOrCancelledAtUtc: t.completedOrCancelledAtUtc ?? new Date().toISOString(),
          earningStatementId: t.statementId,
          finalizedAmountMinor: t.finalizedAmountMinor,
          currency: "INR" as const
        };
      });
    return { items: list, nextCursor: null, hasMore: false };
  }

  async getOrderDetail(driverId: string, orderId: string): Promise<OrderDetailDto> {
    const t = Array.from(this.trips.values()).find((x) => x.orderId === orderId);
    if (!t) throw new AppError({ httpStatus: 404, code: ErrorCodes.ORDER_NOT_FOUND, message: "Order not found." });
    if (t.driverId !== driverId) throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    const ord = this.orders.get(orderId)!;
    return {
      orderId: ord.orderId,
      tripId: t.tripId,
      serviceCategoryCode: ord.serviceCategoryCode,
      status: t.status,
      pickupSummary: ord.pickupLabel,
      dropSummary: ord.dropLabel,
      distanceMeters: ord.distanceMeters,
      durationSeconds: ord.durationSeconds,
      earningStatementId: t.statementId,
      timelineEvents: t.timelineEvents,
      earningSourceRefId: t.statementId,
      finalizedAmountMinor: t.finalizedAmountMinor,
      currency: "INR",
      cancellationReasonCode: t.cancellationReasonCode,
      completedOrCancelledAtUtc: t.completedOrCancelledAtUtc
    };
  }

  async getEarningsSummary(driverId: string, period: string): Promise<EarningsSummaryDto> {
    const list = Array.from(this.statements.values()).filter((s) =>
      Array.from(this.trips.values()).some((t) => t.tripId === s.tripId && t.driverId === driverId)
    );
    const total = list.reduce((a, s) => a + s.netEarningMinor, 0);
    const inc = list.reduce((a, s) => a + s.incentiveMinor, 0);
    return {
      period,
      baseMinor: total - inc,
      incentiveMinor: inc,
      referralMinor: 0,
      adjustmentMinor: 0,
      finalizedTotalMinor: total,
      currency: "INR",
      lastUpdatedUtc: new Date().toISOString()
    };
  }

  async getEarningsTransactions(driverId: string): Promise<PagedDto<LedgerEntryDto>> {
    return { items: this.ledgerEntriesByDriver.get(driverId) ?? [], nextCursor: null, hasMore: false };
  }

  async getEarningStatement(driverId: string, statementId: string): Promise<EarningStatementDto> {
    const s = this.statements.get(statementId);
    if (!s) throw new AppError({ httpStatus: 404, code: ErrorCodes.STATEMENT_NOT_FOUND, message: "Not found." });
    const t = this.trips.get(s.tripId);
    if (!t || t.driverId !== driverId) {
      throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    }
    return s;
  }

  async getWallet(driverId: string): Promise<WalletSummaryDto> {
    const entries = this.ledgerEntriesByDriver.get(driverId) ?? [];
    const available = entries.reduce((sum, e) => sum + e.amountMinor, 0);
    const myPayouts = Array.from(this.payouts.values()).filter((p) => p.driverId === driverId);
    const pending = myPayouts.filter((p) => p.status === "PROCESSING" || p.status === "REQUESTED").reduce((s, p) => s + p.amountMinor, 0);
    const settled = myPayouts.filter((p) => p.status === "PAID").reduce((s, p) => s + p.amountMinor, 0);
    return {
      availableMinor: Math.max(0, available),
      pendingMinor: pending,
      settledMinor: settled,
      todayEarningMinor: (await this.getEarningsSummary(driverId, "today")).finalizedTotalMinor,
      currency: "INR",
      minimumPayoutMinor: 1000,
      maximumPayoutMinor: 2500000,
      livePayoutsEnabled: this.livePayoutsEnabled,
      derivedAtUtc: new Date().toISOString()
    };
  }

  async getPayoutAccounts(driverId: string): Promise<PayoutAccountDto[]> {
    return this.payoutAccountsByDriver.get(driverId) ?? [];
  }

  async addPayoutAccount(driverId: string, input: CreatePayoutAccountInput): Promise<PayoutAccountDto> {
    const acct: PayoutAccountDto = {
      payoutAccountToken: `pa_${randomUUID().slice(0, 12)}`,
      accountLabel: input.accountHolderName,
      maskedAccountNumber: `•••• ${maskBankAccountLast4(input.accountNumber)}`,
      ifscCode: input.ifscCode.toUpperCase(),
      status: "VERIFIED"
    };
    const list = this.payoutAccountsByDriver.get(driverId) ?? [];
    list.unshift(acct);
    this.payoutAccountsByDriver.set(driverId, list);
    return acct;
  }

  async requestPayout(driverId: string, input: RequestPayoutInput): Promise<PayoutDto> {
    if (!this.livePayoutsEnabled) {
      throw new AppError({ httpStatus: 503, code: ErrorCodes.LIVE_PAYOUTS_DISABLED, message: "Live payouts disabled." });
    }
    const existing = Array.from(this.payouts.values()).find((p) => p.idempotencyKey === input.idempotencyKey);
    if (existing) return existing;

    const accts = this.payoutAccountsByDriver.get(driverId) ?? [];
    if (!accts.some((a) => a.payoutAccountToken === input.payoutAccountToken && a.status === "VERIFIED")) {
      throw new AppError({ httpStatus: 422, code: ErrorCodes.INVALID_PAYOUT_ACCOUNT, message: "Invalid payout account." });
    }
    const wallet = await this.getWallet(driverId);
    if (wallet.availableMinor < input.amountMinor) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.INSUFFICIENT_BALANCE, message: "Insufficient balance." });
    }
    const payoutId = randomUUID();
    const record: PayoutDto & { driverId: string; idempotencyKey: string } = {
      payoutId,
      driverId,
      idempotencyKey: input.idempotencyKey,
      payoutAccountToken: input.payoutAccountToken,
      amountMinor: input.amountMinor,
      currency: "INR",
      status: "PROCESSING",
      requestedAtUtc: new Date().toISOString(),
      settledAtUtc: null,
      failureReason: null
    };
    this.payouts.set(payoutId, record);
    this.postLedger(
      driverId,
      {
        transactionId: randomUUID(),
        sourceType: "PAYOUT_REQUEST",
        sourceId: payoutId,
        idempotencyKey: `payout-debit:${payoutId}`,
        entries: [
          { accountId: `driver-${driverId}`, direction: "DEBIT", amountMinor: input.amountMinor },
          { accountId: "platform-clearing", direction: "CREDIT", amountMinor: input.amountMinor }
        ]
      },
      -input.amountMinor,
      "Payout requested",
      null
    );
    return record;
  }

  async getPayouts(driverId: string): Promise<PagedDto<PayoutDto>> {
    return {
      items: Array.from(this.payouts.values()).filter((p) => p.driverId === driverId),
      nextCursor: null,
      hasMore: false
    };
  }

  async processPayoutWebhook(
    provider: string,
    providerEventId: string,
    _payloadHash: string,
    payoutId: string,
    outcome: "PAID" | "FAILED",
    failureCode?: string | null
  ): Promise<PayoutDto> {
    const p = this.payouts.get(payoutId);
    if (!p) throw new AppError({ httpStatus: 404, code: "PAYOUT_NOT_FOUND", message: "Not found." });
    const dedupeKey = `${provider}:${providerEventId}`;
    if (this.webhookEvents.has(dedupeKey)) return p;
    this.webhookEvents.add(dedupeKey);

    if (outcome === "PAID") {
      p.status = "PAID";
      p.settledAtUtc = new Date().toISOString();
      return p;
    }
    p.status = "FAILED";
    p.failureReason = failureCode ?? "FAILED";
    this.postLedger(
      p.driverId,
      {
        transactionId: randomUUID(),
        sourceType: "PAYOUT_REVERSAL",
        sourceId: p.payoutId,
        idempotencyKey: `payout-reversal:${p.payoutId}`,
        entries: [
          { accountId: "platform-clearing", direction: "DEBIT", amountMinor: p.amountMinor },
          { accountId: `driver-${p.driverId}`, direction: "CREDIT", amountMinor: p.amountMinor }
        ]
      },
      p.amountMinor,
      "Payout failure reversal",
      null
    );
    return p;
  }

  async getCampaigns(): Promise<CampaignDto[]> {
    return [];
  }

  async getReferralSummary(driverId: string): Promise<ReferralSummaryDto> {
    const code = Array.from(this.referralCodes.entries()).find(([, id]) => id === driverId)?.[0] ?? "DRV000";
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

  async applyReferralCode(driverId: string, _userId: string, referralCode: string): Promise<ApplyReferralDto> {
    const owner = this.referralCodes.get(referralCode.toUpperCase());
    if (!owner) throw new AppError({ httpStatus: 404, code: ErrorCodes.REFERRAL_CODE_NOT_FOUND, message: "Not found." });
    if (owner === driverId) {
      throw new AppError({ httpStatus: 409, code: ErrorCodes.SELF_REFERRAL_FORBIDDEN, message: "Self referral blocked." });
    }
    return { referralId: randomUUID(), status: "PENDING", message: "Attributed." };
  }

  async getEmergencyContacts(driverId: string): Promise<EmergencyContactDto[]> {
    return this.emergencyContacts.get(driverId) ?? [];
  }

  async addEmergencyContact(driverId: string, input: EmergencyContactInput): Promise<EmergencyContactDto> {
    const dto: EmergencyContactDto = {
      contactId: randomUUID(),
      name: input.name,
      phoneMasked: maskPhone(input.phoneNumber),
      relationship: input.relationship
    };
    const list = this.emergencyContacts.get(driverId) ?? [];
    list.unshift(dto);
    this.emergencyContacts.set(driverId, list);
    return dto;
  }

  async createSosIncident(
    _driverId: string,
    _input: SosTriggerInput,
    responderOutcome: { acknowledged: boolean; responderReference?: string | null }
  ): Promise<SosAlertDto> {
    return {
      alertId: randomUUID(),
      status: responderOutcome.acknowledged ? "ACKNOWLEDGED" : "FAILED",
      responderReference: responderOutcome.responderReference ?? null,
      acknowledgedAtUtc: responderOutcome.acknowledged ? new Date().toISOString() : null,
      message: responderOutcome.acknowledged ? "Acknowledged" : "Unconfirmed"
    };
  }

  async submitIncidentReport(_driverId: string, _input: IncidentReportInput): Promise<IncidentReportDto> {
    return { incidentId: randomUUID(), status: "OPEN", createdAtUtc: new Date().toISOString() };
  }

  async getInsuranceStatus(): Promise<InsuranceStatusDto> {
    return { hasConfirmedPolicy: false, providerName: null, policyReference: null, approvedDisclaimerText: "Unverified" };
  }

  async getSupportTickets(driverId: string): Promise<PagedDto<SupportTicketDto>> {
    return {
      items: Array.from(this.supportTickets.values()).filter((t) => t.driverId === driverId),
      nextCursor: null,
      hasMore: false
    };
  }

  async createSupportTicket(driverId: string, input: CreateSupportTicketInput): Promise<SupportTicketDto> {
    for (const k of input.attachmentStorageKeys) assertPrivateObjectKey(k);
    const now = new Date().toISOString();
    const ticket: SupportTicketDto & { driverId: string } = {
      ticketId: randomUUID(),
      driverId,
      category: input.category,
      subject: input.subject,
      status: "OPEN",
      createdAtUtc: now,
      updatedAtUtc: now
    };
    this.supportTickets.set(ticket.ticketId, ticket);
    return ticket;
  }

  addInternalSupportNoteForTest(ticketId: string, note: string): void {
    const msgs = this.supportMessages.get(ticketId) ?? [];
    msgs.push({
      messageId: randomUUID(),
      senderRole: "INTERNAL_AGENT",
      body: note,
      attachmentIds: [],
      isInternalNote: true,
      sentAtUtc: new Date().toISOString()
    });
    this.supportMessages.set(ticketId, msgs);
  }

  async getSupportMessages(driverId: string, ticketId: string): Promise<SupportMessageDto[]> {
    const t = this.supportTickets.get(ticketId);
    if (!t) throw new AppError({ httpStatus: 404, code: ErrorCodes.TICKET_NOT_FOUND, message: "Not found." });
    if (t.driverId !== driverId) throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    return (this.supportMessages.get(ticketId) ?? []).filter((m) => !m.isInternalNote);
  }

  async sendSupportMessage(driverId: string, ticketId: string, input: CreateSupportMessageInput): Promise<SupportMessageDto> {
    const t = this.supportTickets.get(ticketId);
    if (!t) throw new AppError({ httpStatus: 404, code: ErrorCodes.TICKET_NOT_FOUND, message: "Not found." });
    if (t.driverId !== driverId) throw new AppError({ httpStatus: 403, code: ErrorCodes.FORBIDDEN_CROSS_DRIVER, message: "Forbidden." });
    const msg: SupportMessageDto = {
      messageId: randomUUID(),
      senderRole: "DRIVER",
      body: input.body,
      attachmentIds: input.attachmentStorageKeys,
      isInternalNote: false,
      sentAtUtc: new Date().toISOString()
    };
    const msgs = this.supportMessages.get(ticketId) ?? [];
    msgs.push(msg);
    this.supportMessages.set(ticketId, msgs);
    return msg;
  }

  async getNotifications(userId: string): Promise<PagedDto<AppNotificationDto>> {
    return { items: this.notificationsByUser.get(userId) ?? [], nextCursor: null, hasMore: false };
  }

  async markNotificationRead(userId: string, notificationId: string): Promise<void> {
    const list = this.notificationsByUser.get(userId) ?? [];
    const found = list.find((n) => n.notificationId === notificationId);
    if (found) found.isRead = true;
  }

  async getNotificationPreferences(userId: string): Promise<NotificationPreferencesDto> {
    return this.notificationPrefs.get(userId) ?? { orderAlertsEnabled: true, earningsAlertsEnabled: true, campaignAlertsEnabled: true };
  }

  async updateNotificationPreferences(userId: string, input: NotificationPreferencesDto): Promise<NotificationPreferencesDto> {
    this.notificationPrefs.set(userId, input);
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
    const docs = this.docsByDriver.get(driverId) ?? [];
    const doc = docs.find((d) => d.documentId === documentId);
    if (!doc) throw new AppError({ httpStatus: 404, code: ErrorCodes.DOCUMENT_NOT_FOUND, message: "Not found." });
    doc.status = decision;
    doc.rejectionReason = decision === "REJECTED" ? (reason ?? "Rejected") : null;
    const d = this.drivers.get(driverId)!;
    const allApproved = MANDATORY_DOCS.every((t) => docs.find((x) => x.documentType === t)?.status === "APPROVED");
    d.documentsVerified = allApproved;
    d.accountStatus = allApproved ? "ACTIVE" : decision === "REJECTED" ? "REJECTED" : "PENDING_REVIEW";
    if (allApproved) {
      for (const v of this.vehiclesByDriver.get(driverId) ?? []) {
        v.isApproved = true;
        v.status = "APPROVED";
      }
    }
    this.auditLogs.unshift({
      logId: randomUUID(),
      actorId,
      actorRole: "ADMIN",
      action: `DOCUMENT_${decision}`,
      resourceType: "driver_document",
      resourceId: documentId,
      requestId: requestId ?? null,
      createdAtUtc: new Date().toISOString()
    });
    const u = Array.from(this.usersByUid.values()).find((x) => x.driverId === driverId)!;
    return this.buildSession(u, d);
  }

  async adminCreateAndOfferOrder(
    actorId: string,
    input: AdminCreateOrderInput,
    requestId?: string
  ): Promise<{ orderId: string; offerId: string; driverId: string }> {
    let existingOrder = Array.from(this.orders.values()).find(
      (o) => o.pickupLabel === input.pickupLabel && o.dropLabel === input.dropLabel && o.assignedDriverId === null
    );
    if (!existingOrder) {
      const orderId = randomUUID();
      existingOrder = {
        orderId,
        serviceCategoryCode: input.serviceCategoryCode,
        pickupLabel: input.pickupLabel,
        pickupLat: input.pickupLat,
        pickupLng: input.pickupLng,
        dropLabel: input.dropLabel,
        dropLat: input.dropLat,
        dropLng: input.dropLng,
        distanceMeters: input.distanceMeters,
        durationSeconds: input.durationSeconds,
        assignedDriverId: null,
        status: "PENDING_DISPATCH"
      };
      this.orders.set(orderId, existingOrder);
    }
    const breakdown = computeFareBreakdownMinor({
      category: input.serviceCategoryCode,
      distanceMeters: input.distanceMeters,
      durationSeconds: input.durationSeconds,
      incentiveMinor: input.incentiveMinor ?? 0
    });
    const offerId = randomUUID();
    this.offers.set(offerId, {
      offerId,
      orderId: existingOrder.orderId,
      driverId: input.driverId,
      status: "OFFERED",
      priorityLabel: input.priorityLabel ?? null,
      pickupDistanceMeters: 850,
      expiresAtMs: Date.now() + (input.expiresInSeconds ?? 30) * 1000,
      quotedBaseMinor: breakdown.netEarningMinor - breakdown.incentiveMinor,
      quotedIncentiveMinor: breakdown.incentiveMinor,
      idempotencyKey: null
    });
    this.auditLogs.unshift({
      logId: randomUUID(),
      actorId,
      actorRole: "ADMIN",
      action: "DISPATCH_OFFER_CREATED",
      resourceType: "order_offer",
      resourceId: offerId,
      requestId: requestId ?? null,
      createdAtUtc: new Date().toISOString()
    });
    return { orderId: existingOrder.orderId, offerId, driverId: input.driverId };
  }

  async adminGetAuditLogs(): Promise<AuditLogDto[]> {
    return this.auditLogs;
  }
}
