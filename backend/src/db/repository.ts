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

export interface AuthSessionDto {
  userId: string;
  driverId: string;
  accountStatus: AccountStatus;
  requiresProfile: boolean;
  requiresVehicle: boolean;
  requiresKyc: boolean;
  serverTimeUtc: string;
  phoneMasked: string;
}

export interface DriverProfileDto {
  driverId: string;
  fullName: string;
  phoneMasked: string;
  cityCode: string;
  preferredLanguage: string;
  accountStatus: AccountStatus;
  documentsVerified: boolean;
  dateOfBirth: string | null;
  gender: string | null;
  addressLine: string | null;
  stateCode: string | null;
  pinCode: string | null;
  profilePhotoObjectKey: string | null;
  rating: number | null;
  totalCompletedTrips: number;
  acceptanceRate: number | null;
}

export interface DriverProfileInput {
  fullName: string;
  cityCode: string;
  preferredLanguage: "en" | "hi";
  dateOfBirth?: string | null;
  gender?: string | null;
  addressLine?: string | null;
  stateCode?: string | null;
  pinCode?: string | null;
  profilePhotoObjectKey?: string | null;
}

export interface VehicleInput {
  serviceTypeCode: ServiceCategory;
  registrationNumber: string;
  vehicleModel: string;
  brand: string;
  color: string;
  year?: number | null;
  vehiclePhotoObjectKey?: string | null;
}

export interface VehicleDto {
  vehicleId: string;
  serviceTypeCode: ServiceCategory;
  registrationNumber: string;
  vehicleModel: string;
  isApproved: boolean;
  brand: string;
  color: string;
  year: number | null;
  status: string;
  vehiclePhotoObjectKey: string | null;
}

export interface UploadIntentInput {
  documentType: DocumentType;
  mimeType: string;
  sizeBytes: number;
  checksumSha256?: string | null;
}

export interface UploadIntentDto {
  documentId: string;
  uploadUrl: string;
  objectKey: string;
  expiresAtUtc: string;
  requiredHeaders: Record<string, string>;
}

export interface ConfirmDocumentInput {
  documentId: string;
  storageObjectKey: string;
  expiresOnUtc?: string | null;
  maskedReference?: string | null;
}

export interface KycDocumentDto {
  documentId: string;
  documentType: DocumentType;
  status: DocumentStatus;
  rejectionReason: string | null;
  maskedReference: string | null;
  expiresAtUtc: string | null;
  updatedAtUtc: string;
}

export interface ServiceZoneDto {
  zoneId: string;
  cityCode: string;
  name: string;
  isEnabled: boolean;
}

export interface EligibilityDto {
  isEligibleForOnline: boolean;
  blockingReasons: string[];
  approvedServiceCategoryCodes: ServiceCategory[];
  assignedZoneIds: string[];
  evaluatedAtUtc: string;
}

export interface HomeSummaryDto {
  dutyStatus: DutyStatus;
  todayCompletedCount: number;
  todayEstimatedMinor: number;
  currency: "INR";
  activeZones: ServiceZoneDto[];
  serverTimeUtc: string;
}

export interface DriverPreferencesDto {
  preferredZoneIds: string[];
  autoBoostEnabled: boolean;
  activeServiceCategoryCode: ServiceCategory;
}

export interface DutyStatusInput {
  idempotencyKey: string;
  latitude?: number | null;
  longitude?: number | null;
  accuracyMeters?: number | null;
  capturedAtUtc?: string | null;
}

export interface DutyStateDto {
  status: DutyStatus;
  sessionId: string | null;
  activeTripId: string | null;
  serverTimeUtc: string;
}

export interface LocationPingInput {
  latitude: number;
  longitude: number;
  accuracyMeters: number;
  bearingDegrees?: number | null;
  speedMps?: number | null;
  capturedAtUtc: string;
  dutySessionId?: string | null;
}

export interface WorkOfferDto {
  offerId: string;
  orderId: string;
  serviceCategoryCode: ServiceCategory;
  priorityLabel: string | null;
  pickupSummary: string;
  dropSummary: string;
  pickupDistanceMeters: number;
  distanceMeters: number;
  durationSeconds: number;
  estimatedBaseMinor: number;
  eligibleIncentiveMinor: number;
  estimatedTotalMinor: number;
  currency: "INR";
  isEstimate: boolean;
  expiresAtUtc: string;
  serverNowUtc: string;
  status: OfferStatus;
}

export interface AcceptOfferDto {
  tripId: string;
  orderId: string;
  tripStatus: TripStatus;
  acceptedAtUtc: string;
}

export interface TripLocationPointDto {
  label: string;
  addressSummary: string;
  latitude: number;
  longitude: number;
}

export interface ActiveTripDto {
  tripId: string;
  orderId: string;
  serviceCategoryCode: ServiceCategory;
  status: TripStatus;
  version: number;
  pickup: TripLocationPointDto;
  drop: TripLocationPointDto;
  maskedContactAvailable: boolean;
  startedAtUtc: string | null;
  updatedAtUtc: string;
  requiresStartPin: boolean;
  estimatedTotalMinor: number | null;
  currency: "INR";
}

export interface TripTransitionInput {
  targetStatus: TripStatus;
  expectedVersion: number;
  idempotencyKey: string;
  reasonCode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  verificationPin?: string | null;
}

export interface TripTransitionDto {
  tripId: string;
  status: TripStatus;
  version: number;
  earningSourceRefId: string | null;
  transitionedAtUtc: string;
}

export interface OrderHistorySummaryDto {
  orderId: string;
  tripId: string;
  serviceCategoryCode: ServiceCategory;
  status: TripStatus;
  pickupSummary: string;
  dropSummary: string;
  completedOrCancelledAtUtc: string;
  earningStatementId: string | null;
  finalizedAmountMinor: number | null;
  currency: "INR";
}

export interface OrderDetailDto {
  orderId: string;
  tripId: string;
  serviceCategoryCode: ServiceCategory;
  status: TripStatus;
  pickupSummary: string;
  dropSummary: string;
  distanceMeters: number;
  durationSeconds: number;
  earningStatementId: string | null;
  timelineEvents: string[];
  earningSourceRefId: string | null;
  finalizedAmountMinor: number | null;
  currency: "INR";
  cancellationReasonCode: string | null;
  completedOrCancelledAtUtc: string | null;
}

export interface EarningsSummaryDto {
  period: string;
  baseMinor: number;
  incentiveMinor: number;
  referralMinor: number;
  adjustmentMinor: number;
  finalizedTotalMinor: number;
  currency: "INR";
  lastUpdatedUtc: string;
}

export interface LedgerEntryDto {
  entryId: string;
  sourceType: string;
  sourceId: string;
  entryType: "TRIP_EARNING" | "INCENTIVE" | "REFERRAL_BONUS" | "PAYOUT_DEBIT" | "PAYOUT_REVERSAL" | "ADJUSTMENT" | "FEE_DEDUCTION";
  amountMinor: number;
  currency: "INR";
  balanceAfterMinor: number;
  createdAtUtc: string;
  description: string;
  statementId: string | null;
}

export interface EarningStatementDto {
  statementId: string;
  earningSourceRefId: string;
  tripId: string;
  fareConfigVersion: string;
  quoteSnapshotId: string;
  baseFareMinor: number;
  distanceFareMinor: number;
  timeFareMinor: number;
  incentiveMinor: number;
  platformFeeMinor: number;
  taxMinor: number;
  netEarningMinor: number;
  currency: "INR";
  finalizedAtUtc: string;
}

export interface WalletSummaryDto {
  availableMinor: number;
  pendingMinor: number;
  settledMinor: number;
  todayEarningMinor: number;
  currency: "INR";
  minimumPayoutMinor: number;
  maximumPayoutMinor: number;
  livePayoutsEnabled: boolean;
  derivedAtUtc: string;
}

export interface PayoutAccountDto {
  payoutAccountToken: string;
  accountLabel: string;
  maskedAccountNumber: string;
  ifscCode: string;
  status: "PENDING_VERIFICATION" | "VERIFIED" | "DISABLED";
}

export interface CreatePayoutAccountInput {
  accountHolderName: string;
  accountNumber: string;
  ifscCode: string;
  idempotencyKey: string;
}

export interface RequestPayoutInput {
  payoutAccountToken: string;
  amountMinor: number;
  idempotencyKey: string;
}

export interface PayoutDto {
  payoutId: string;
  payoutAccountToken: string;
  amountMinor: number;
  currency: "INR";
  status: PayoutStatus;
  requestedAtUtc: string;
  settledAtUtc: string | null;
  failureReason: string | null;
}

export interface CampaignDto {
  campaignId: string;
  title: string;
  category: "DAILY_INCENTIVE" | "WEEKLY_CHALLENGE" | "PRIORITY_REWARD";
  termsVersion: string;
  termsSummary: string;
  targetCount: number;
  currentCount: number;
  rewardMinor: number;
  currency: "INR";
  isQualified: boolean;
  isDriverEligible: boolean;
  ineligibilityReason: string | null;
  rewardCreditedSourceRefId: string | null;
  expiresAtUtc: string;
}

export interface ReferralSummaryDto {
  referralCode: string;
  shareLink: string;
  qualifiedCount: number;
  pendingCount: number;
  totalEarnedMinor: number;
  currency: "INR";
  isEligibleForReferral: boolean;
  ineligibilityReason: string | null;
  referrals: Array<{
    referralId: string;
    refereeDisplayLabel: string;
    status: "PENDING" | "QUALIFIED" | "REWARDED" | "REJECTED_SELF_REFERRAL" | "REJECTED_FRAUD";
    qualificationSummary: string;
    rewardMinor: number;
    currency: "INR";
    createdAtUtc: string;
  }>;
}

export interface ApplyReferralDto {
  referralId: string;
  status: "PENDING" | "QUALIFIED" | "REWARDED" | "REJECTED_SELF_REFERRAL" | "REJECTED_FRAUD";
  message: string;
}

export interface EmergencyContactDto {
  contactId: string;
  name: string;
  phoneMasked: string;
  relationship: string;
}

export interface EmergencyContactInput {
  name: string;
  phoneNumber: string;
  relationship: string;
  idempotencyKey: string;
}

export interface SosTriggerInput {
  latitude: number;
  longitude: number;
  activeTripId?: string | null;
  idempotencyKey: string;
}

export interface SosAlertDto {
  alertId: string;
  status: "UNCONFIRMED" | "SENDING" | "ACKNOWLEDGED" | "FAILED";
  responderReference: string | null;
  acknowledgedAtUtc: string | null;
  message: string;
}

export interface IncidentReportInput {
  category: string;
  description: string;
  relatedTripId?: string | null;
  idempotencyKey: string;
}

export interface IncidentReportDto {
  incidentId: string;
  status: string;
  createdAtUtc: string;
}

export interface InsuranceStatusDto {
  hasConfirmedPolicy: boolean;
  providerName: string | null;
  policyReference: string | null;
  approvedDisclaimerText: string | null;
}

export interface SupportTicketDto {
  ticketId: string;
  category: string;
  subject: string;
  status: TicketStatus;
  createdAtUtc: string;
  updatedAtUtc: string;
}

export interface CreateSupportTicketInput {
  category: string;
  subject: string;
  description: string;
  attachmentStorageKeys: string[];
  idempotencyKey: string;
}

export interface SupportMessageDto {
  messageId: string;
  senderRole: string;
  body: string;
  attachmentIds: string[];
  isInternalNote: boolean;
  sentAtUtc: string;
}

export interface CreateSupportMessageInput {
  body: string;
  attachmentStorageKeys: string[];
  idempotencyKey: string;
}

export interface AppNotificationDto {
  notificationId: string;
  dedupeKey: string;
  category: string;
  title: string;
  body: string;
  deepLinkRoute: string | null;
  isRead: boolean;
  createdAtUtc: string;
}

export interface NotificationPreferencesDto {
  orderAlertsEnabled: boolean;
  earningsAlertsEnabled: boolean;
  campaignAlertsEnabled: boolean;
}

export interface PagedDto<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface AdminCreateOrderInput {
  driverId: string;
  serviceCategoryCode: ServiceCategory;
  pickupLabel: string;
  pickupLat: number;
  pickupLng: number;
  dropLabel: string;
  dropLat: number;
  dropLng: number;
  distanceMeters: number;
  durationSeconds: number;
  incentiveMinor?: number;
  expiresInSeconds?: number;
  priorityLabel?: string | null;
}

export interface AuditLogDto {
  logId: string;
  actorId: string | null;
  actorRole: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  requestId: string | null;
  createdAtUtc: string;
}

export interface BackendRepository {
  checkHealth(): Promise<{ databaseReady: boolean; postgisReady: boolean }>;

  establishSession(claims: { firebaseUid: string; phoneE164?: string | null }): Promise<AuthSessionDto>;
  getSessionByFirebaseUid(firebaseUid: string): Promise<AuthSessionDto | null>;
  logout(driverId: string): Promise<void>;

  getDriverProfile(driverId: string): Promise<DriverProfileDto>;
  upsertDriverProfile(driverId: string, input: DriverProfileInput): Promise<DriverProfileDto>;
  getVehicles(driverId: string): Promise<VehicleDto[]>;
  upsertVehicle(driverId: string, input: VehicleInput): Promise<VehicleDto>;
  createDocumentUploadIntent(driverId: string, input: UploadIntentInput): Promise<UploadIntentDto>;
  confirmDocumentUpload(driverId: string, input: ConfirmDocumentInput): Promise<KycDocumentDto>;
  getDocuments(driverId: string): Promise<KycDocumentDto[]>;

  getEligibility(driverId: string): Promise<EligibilityDto>;
  getHomeSummary(driverId: string): Promise<HomeSummaryDto>;
  getPreferences(driverId: string): Promise<DriverPreferencesDto>;
  updatePreferences(driverId: string, input: DriverPreferencesDto): Promise<DriverPreferencesDto>;
  getZones(cityCode: string): Promise<ServiceZoneDto[]>;
  goOnline(driverId: string, input: DutyStatusInput): Promise<DutyStateDto>;
  goOffline(driverId: string, input: DutyStatusInput): Promise<DutyStateDto>;
  recordLocationPing(driverId: string, input: LocationPingInput): Promise<{ accepted: boolean; staleAfterSeconds: number; serverTimeUtc: string }>;

  getActiveOffers(driverId: string): Promise<{ offers: WorkOfferDto[]; serverNowUtc: string }>;
  getOfferDetail(driverId: string, offerId: string): Promise<WorkOfferDto>;
  acceptOffer(driverId: string, offerId: string, idempotencyKey: string): Promise<AcceptOfferDto>;
  rejectOffer(driverId: string, offerId: string, idempotencyKey: string, reasonCode?: string | null): Promise<void>;

  getActiveTrip(driverId: string): Promise<{ trip: ActiveTripDto | null; serverNowUtc: string }>;
  transitionTrip(driverId: string, tripId: string, input: TripTransitionInput): Promise<TripTransitionDto>;
  getOrders(driverId: string, statusFilter: "all" | "completed" | "cancelled", cursor?: string | null, limit?: number): Promise<PagedDto<OrderHistorySummaryDto>>;
  getOrderDetail(driverId: string, orderId: string): Promise<OrderDetailDto>;

  getEarningsSummary(driverId: string, period: string): Promise<EarningsSummaryDto>;
  getEarningsTransactions(driverId: string, cursor?: string | null, limit?: number): Promise<PagedDto<LedgerEntryDto>>;
  getEarningStatement(driverId: string, statementId: string): Promise<EarningStatementDto>;
  getWallet(driverId: string): Promise<WalletSummaryDto>;
  getPayoutAccounts(driverId: string): Promise<PayoutAccountDto[]>;
  addPayoutAccount(driverId: string, input: CreatePayoutAccountInput): Promise<PayoutAccountDto>;
  requestPayout(driverId: string, input: RequestPayoutInput): Promise<PayoutDto>;
  getPayouts(driverId: string, cursor?: string | null, limit?: number): Promise<PagedDto<PayoutDto>>;
  processPayoutWebhook(
    provider: string,
    providerEventId: string,
    payloadHash: string,
    payoutId: string,
    outcome: "PAID" | "FAILED",
    failureCode?: string | null
  ): Promise<PayoutDto>;

  getCampaigns(driverId: string): Promise<CampaignDto[]>;
  getReferralSummary(driverId: string): Promise<ReferralSummaryDto>;
  applyReferralCode(driverId: string, userId: string, referralCode: string, idempotencyKey: string): Promise<ApplyReferralDto>;

  getEmergencyContacts(driverId: string): Promise<EmergencyContactDto[]>;
  addEmergencyContact(driverId: string, input: EmergencyContactInput): Promise<EmergencyContactDto>;
  createSosIncident(
    driverId: string,
    input: SosTriggerInput,
    responderOutcome: { acknowledged: boolean; responderReference?: string | null }
  ): Promise<SosAlertDto>;
  submitIncidentReport(driverId: string, input: IncidentReportInput): Promise<IncidentReportDto>;
  getInsuranceStatus(driverId: string): Promise<InsuranceStatusDto>;

  getSupportTickets(driverId: string, cursor?: string | null, limit?: number): Promise<PagedDto<SupportTicketDto>>;
  createSupportTicket(driverId: string, input: CreateSupportTicketInput): Promise<SupportTicketDto>;
  getSupportMessages(driverId: string, ticketId: string): Promise<SupportMessageDto[]>;
  sendSupportMessage(driverId: string, ticketId: string, input: CreateSupportMessageInput): Promise<SupportMessageDto>;

  getNotifications(userId: string, cursor?: string | null, limit?: number): Promise<PagedDto<AppNotificationDto>>;
  markNotificationRead(userId: string, notificationId: string): Promise<void>;
  getNotificationPreferences(userId: string): Promise<NotificationPreferencesDto>;
  updateNotificationPreferences(userId: string, input: NotificationPreferencesDto): Promise<NotificationPreferencesDto>;

  adminReviewDocument(
    actorId: string,
    driverId: string,
    documentId: string,
    decision: "APPROVED" | "REJECTED",
    reason?: string | null,
    requestId?: string
  ): Promise<AuthSessionDto>;
  adminCreateAndOfferOrder(
    actorId: string,
    input: AdminCreateOrderInput,
    requestId?: string
  ): Promise<{ orderId: string; offerId: string; driverId: string }>;
  adminGetAuditLogs(limit?: number): Promise<AuditLogDto[]>;
}
