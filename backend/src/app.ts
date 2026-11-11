import { createHash, createHmac, randomUUID, timingSafeEqual } from "node:crypto";
import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import websocket from "@fastify/websocket";
import { z, ZodError } from "zod";
import { BackendConfig } from "./config/env.js";
import { AppError, ErrorCodes, sendErrorEnvelope } from "./common/errors.js";
import { sanitizeLogPayload } from "./common/logger.js";
import {
  AcceptOfferSchema,
  ApplyReferralSchema,
  ConfirmDocumentUploadSchema,
  CreatePayoutAccountSchema,
  CreateSupportMessageSchema,
  CreateSupportTicketSchema,
  DocumentUploadIntentSchema,
  DriverPreferencesSchema,
  DriverProfileSchema,
  EmergencyContactSchema,
  IncidentReportSchema,
  LocationPingSchema,
  NotificationPreferencesSchema,
  RejectOfferSchema,
  RequestPayoutSchema,
  SosTriggerSchema,
  TripTransitionSchema,
  UpdateDutyStatusSchema,
  VehicleDetailsSchema,
  VerifySessionSchema
} from "./common/validators.js";
import { BackendRepository } from "./db/repository.js";
import {
  assertAdminAuthorized,
  AuthContext,
  extractBearerToken,
  verifyBearerIdToken
} from "./middleware/auth.js";
import { RealtimeHub } from "./modules/realtime/ws.gateway.js";

declare module "fastify" {
  interface FastifyRequest {
    authContext?: AuthContext;
    requestIdValue: string;
  }
}

function parseBody<T>(schema: z.ZodType<T>, raw: unknown): T {
  try {
    return schema.parse(raw ?? {});
  } catch (err) {
    if (err instanceof ZodError) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.INVALID_INPUT,
        message: err.issues.map((i) => `${i.path.join(".") || "body"}: ${i.message}`).join("; ")
      });
    }
    throw err;
  }
}

export interface BuildAppOptions {
  config: BackendConfig;
  repository: BackendRepository;
  realtimeHub?: RealtimeHub;
}

export async function buildApp(options: BuildAppOptions): Promise<FastifyInstance> {
  const { config, repository } = options;
  const realtimeHub = options.realtimeHub ?? new RealtimeHub();

  const app = Fastify({
    logger: {
      level: config.NODE_ENV === "test" ? "silent" : "info",
      serializers: {
        req(req) {
          return {
            method: req.method,
            url: sanitizeLogPayload(req.url)
          };
        }
      }
    },
    genReqId: (req) => {
      const incoming = req.headers["x-request-id"];
      return typeof incoming === "string" && incoming.trim() ? incoming.trim() : randomUUID();
    }
  });

  await app.register(websocket);

  app.addHook("onRequest", async (req, reply) => {
    req.requestIdValue = String(req.id);
    reply.header("X-Request-Id", req.requestIdValue);
    reply.header("X-Content-Type-Options", "nosniff");
    reply.header("X-Frame-Options", "DENY");
  });

  app.setErrorHandler((err, req, reply) => {
    return sendErrorEnvelope(reply, req.requestIdValue ?? randomUUID(), err);
  });

  async function requireAuth(req: FastifyRequest, _reply: FastifyReply): Promise<void> {
    const token = extractBearerToken(req);
    const claims = await verifyBearerIdToken(token, config);
    const session = await repository.getSessionByFirebaseUid(claims.authUid);
    if (!session) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Driver session not found. Establish session via POST /api/v1/auth/session first."
      });
    }
    req.authContext = {
      userId: session.userId,
      driverId: session.driverId,
      authUid: claims.authUid,
      firebaseUid: claims.authUid,
      accountStatus: session.accountStatus
    };
  }

  // 1. Health & Readiness
  app.get("/api/v1/health", async (_req, reply) => {
    const health = await repository.checkHealth();
    const ok = health.databaseReady && health.postgisReady;
    return reply.status(ok ? 200 : 503).send({
      status: ok ? "ok" : "degraded",
      databaseReady: health.databaseReady,
      postgisReady: health.postgisReady,
      serverTimeUtc: new Date().toISOString(),
      environment: config.NODE_ENV
    });
  });

  // 2. Auth & Onboarding (Phase 2)
  app.post("/api/v1/auth/session", async (req) => {
    const body = parseBody(VerifySessionSchema, req.body);
    const rawToken = body.idToken ?? body.firebaseIdToken ?? "";
    const claims = await verifyBearerIdToken(rawToken, config);
    return repository.establishSession({
      firebaseUid: claims.authUid,
      phoneE164: body.phoneE164 ?? claims.phoneE164 ?? null
    });
  });

  app.get("/api/v1/me", { preHandler: requireAuth }, async (req) => {
    const session = await repository.getSessionByFirebaseUid(req.authContext!.firebaseUid);
    if (!session) {
      throw new AppError({ httpStatus: 401, code: ErrorCodes.UNAUTHORIZED, message: "Session not found." });
    }
    return session;
  });

  app.post("/api/v1/auth/logout", { preHandler: requireAuth }, async (req) => {
    await repository.logout(req.authContext!.driverId);
    return {};
  });

  app.get("/api/v1/drivers/me", { preHandler: requireAuth }, async (req) => {
    return repository.getDriverProfile(req.authContext!.driverId);
  });

  app.post("/api/v1/drivers/profile", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(DriverProfileSchema, req.body);
    return repository.upsertDriverProfile(req.authContext!.driverId, body);
  });

  app.get("/api/v1/vehicles", { preHandler: requireAuth }, async (req) => {
    return repository.getVehicles(req.authContext!.driverId);
  });

  app.post("/api/v1/vehicles", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(VehicleDetailsSchema, req.body);
    return repository.upsertVehicle(req.authContext!.driverId, body);
  });

  app.post("/api/v1/documents/upload-intent", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(DocumentUploadIntentSchema, req.body);
    return repository.createDocumentUploadIntent(req.authContext!.driverId, body);
  });

  app.post("/api/v1/documents", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(ConfirmDocumentUploadSchema, req.body);
    return repository.confirmDocumentUpload(req.authContext!.driverId, body);
  });

  app.get("/api/v1/documents", { preHandler: requireAuth }, async (req) => {
    return repository.getDocuments(req.authContext!.driverId);
  });

  // 3. Home, Eligibility, Preferences, Zones, Duty & Location (Phase 3)
  app.get("/api/v1/home", { preHandler: requireAuth }, async (req) => {
    return repository.getHomeSummary(req.authContext!.driverId);
  });

  app.get("/api/v1/drivers/eligibility", { preHandler: requireAuth }, async (req) => {
    return repository.getEligibility(req.authContext!.driverId);
  });

  app.get("/api/v1/drivers/preferences", { preHandler: requireAuth }, async (req) => {
    return repository.getPreferences(req.authContext!.driverId);
  });

  app.put("/api/v1/drivers/preferences", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(DriverPreferencesSchema, req.body);
    return repository.updatePreferences(req.authContext!.driverId, body);
  });

  app.get("/api/v1/zones", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { city?: string };
    return repository.getZones(query.city ?? "JAI");
  });

  app.post("/api/v1/availability/online", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(UpdateDutyStatusSchema, req.body);
    return repository.goOnline(req.authContext!.driverId, body);
  });

  app.post("/api/v1/availability/offline", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(UpdateDutyStatusSchema, req.body);
    return repository.goOffline(req.authContext!.driverId, body);
  });

  app.post("/api/v1/locations", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(LocationPingSchema, req.body);
    return repository.recordLocationPing(req.authContext!.driverId, body);
  });

  // 4. Dispatch & Offers (Phase 4)
  app.get("/api/v1/offers/active", { preHandler: requireAuth }, async (req) => {
    return repository.getActiveOffers(req.authContext!.driverId);
  });

  app.get("/api/v1/offers/:offerId", { preHandler: requireAuth }, async (req) => {
    const { offerId } = req.params as { offerId: string };
    return repository.getOfferDetail(req.authContext!.driverId, offerId);
  });

  app.post("/api/v1/offers/:offerId/accept", { preHandler: requireAuth }, async (req) => {
    const { offerId } = req.params as { offerId: string };
    const body = parseBody(AcceptOfferSchema, req.body);
    const result = await repository.acceptOffer(req.authContext!.driverId, offerId, body.idempotencyKey);
    realtimeHub.emitToDriver(req.authContext!.driverId, "OFFER_UPDATED", offerId);
    realtimeHub.emitToDriver(req.authContext!.driverId, "TRIP_UPDATED", result.tripId);
    return result;
  });

  app.post("/api/v1/offers/:offerId/reject", { preHandler: requireAuth }, async (req) => {
    const { offerId } = req.params as { offerId: string };
    const body = parseBody(RejectOfferSchema, req.body);
    await repository.rejectOffer(req.authContext!.driverId, offerId, body.idempotencyKey, body.reasonCode);
    realtimeHub.emitToDriver(req.authContext!.driverId, "OFFER_UPDATED", offerId);
    return {};
  });

  // 5. Trips & Order History (Phase 5)
  app.get("/api/v1/trips/active", { preHandler: requireAuth }, async (req) => {
    return repository.getActiveTrip(req.authContext!.driverId);
  });

  app.post("/api/v1/trips/:tripId/events", { preHandler: requireAuth }, async (req) => {
    const { tripId } = req.params as { tripId: string };
    const body = parseBody(TripTransitionSchema, req.body);
    const result = await repository.transitionTrip(req.authContext!.driverId, tripId, body);
    realtimeHub.emitToDriver(req.authContext!.driverId, "TRIP_UPDATED", tripId);
    return result;
  });

  app.get("/api/v1/orders", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { status?: string; cursor?: string; limit?: string };
    const statusFilter =
      query.status === "completed" || query.status === "cancelled" ? query.status : "all";
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    return repository.getOrders(req.authContext!.driverId, statusFilter, query.cursor ?? null, limit);
  });

  app.get("/api/v1/orders/:orderId", { preHandler: requireAuth }, async (req) => {
    const { orderId } = req.params as { orderId: string };
    return repository.getOrderDetail(req.authContext!.driverId, orderId);
  });

  // 6. Earnings, Statements, Ledger, Wallet, Payouts & Webhooks (Phase 6)
  app.get("/api/v1/earnings/summary", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { period?: string };
    return repository.getEarningsSummary(req.authContext!.driverId, query.period ?? "today");
  });

  app.get("/api/v1/earnings/transactions", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { cursor?: string; limit?: string };
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    return repository.getEarningsTransactions(req.authContext!.driverId, query.cursor ?? null, limit);
  });

  app.get("/api/v1/earnings/statements/:statementId", { preHandler: requireAuth }, async (req) => {
    const { statementId } = req.params as { statementId: string };
    return repository.getEarningStatement(req.authContext!.driverId, statementId);
  });

  app.get("/api/v1/wallet", { preHandler: requireAuth }, async (req) => {
    return repository.getWallet(req.authContext!.driverId);
  });

  app.get("/api/v1/payout-accounts", { preHandler: requireAuth }, async (req) => {
    return repository.getPayoutAccounts(req.authContext!.driverId);
  });

  app.post("/api/v1/payout-accounts", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(CreatePayoutAccountSchema, req.body);
    return repository.addPayoutAccount(req.authContext!.driverId, body);
  });

  app.post("/api/v1/payouts", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(RequestPayoutSchema, req.body);
    const result = await repository.requestPayout(req.authContext!.driverId, body);
    realtimeHub.emitToDriver(req.authContext!.driverId, "PAYOUT_UPDATED", result.payoutId);
    return result;
  });

  app.get("/api/v1/payouts", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { cursor?: string; limit?: string };
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    return repository.getPayouts(req.authContext!.driverId, query.cursor ?? null, limit);
  });

  app.post("/api/v1/webhooks/payouts/:provider", async (req) => {
    const { provider } = req.params as { provider: string };
    if (!config.PAYOUT_WEBHOOK_SECRET) {
      throw new AppError({
        httpStatus: 503,
        code: ErrorCodes.INVALID_WEBHOOK_SIGNATURE,
        message: "Payout webhook verification secret is not configured."
      });
    }
    const sigHeader = req.headers["x-webhook-signature"];
    const sig = Array.isArray(sigHeader) ? sigHeader[0] : sigHeader;
    const rawBody = JSON.stringify(req.body ?? {});
    const expected = createHmac("sha256", config.PAYOUT_WEBHOOK_SECRET).update(rawBody).digest("hex");
    if (!sig || Buffer.from(sig).length !== Buffer.from(expected).length || !timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_WEBHOOK_SIGNATURE,
        message: "Webhook HMAC signature invalid."
      });
    }
    const payload = parseBody(
      z.object({
        providerEventId: z.string().min(1),
        payoutId: z.string().min(1),
        outcome: z.enum(["PAID", "FAILED"]),
        failureCode: z.string().optional().nullable()
      }),
      req.body
    );
    const hash = createHash("sha256").update(rawBody).digest("hex");
    return repository.processPayoutWebhook(
      provider,
      payload.providerEventId,
      hash,
      payload.payoutId,
      payload.outcome,
      payload.failureCode
    );
  });

  // 7. Campaigns, Referrals, Safety, Support & Notifications (Phase 7)
  app.get("/api/v1/campaigns", { preHandler: requireAuth }, async (req) => {
    return repository.getCampaigns(req.authContext!.driverId);
  });

  app.get("/api/v1/referrals/me", { preHandler: requireAuth }, async (req) => {
    return repository.getReferralSummary(req.authContext!.driverId);
  });

  app.post("/api/v1/referrals/apply", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(ApplyReferralSchema, req.body);
    return repository.applyReferralCode(
      req.authContext!.driverId,
      req.authContext!.userId,
      body.referralCode,
      body.idempotencyKey
    );
  });

  app.get("/api/v1/safety/contacts", { preHandler: requireAuth }, async (req) => {
    return repository.getEmergencyContacts(req.authContext!.driverId);
  });

  app.post("/api/v1/safety/contacts", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(EmergencyContactSchema, req.body);
    return repository.addEmergencyContact(req.authContext!.driverId, body);
  });

  app.post("/api/v1/safety/sos", { preHandler: requireAuth }, async (req, reply) => {
    const body = parseBody(SosTriggerSchema, req.body);
    if (!config.SOS_RESPONDER_WEBHOOK_URL) {
      await repository.createSosIncident(req.authContext!.driverId, body, { acknowledged: false });
      return reply.status(503).send({
        error: {
          code: ErrorCodes.SOS_RESPONDER_UNCONFIGURED,
          message: "Emergency responder webhook is not configured. Call local emergency 112 immediately.",
          requestId: req.requestIdValue,
          retryable: false,
          details: {}
        }
      });
    }
    try {
      const resp = await fetch(config.SOS_RESPONDER_WEBHOOK_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          driverId: req.authContext!.driverId,
          latitude: body.latitude,
          longitude: body.longitude,
          activeTripId: body.activeTripId ?? null
        })
      });
      if (!resp.ok) {
        return repository.createSosIncident(req.authContext!.driverId, body, { acknowledged: false });
      }
      const data = (await resp.json().catch(() => ({}))) as { responderReference?: string };
      return repository.createSosIncident(req.authContext!.driverId, body, {
        acknowledged: true,
        responderReference: data.responderReference ?? "RESPONDER-ACK"
      });
    } catch {
      return repository.createSosIncident(req.authContext!.driverId, body, { acknowledged: false });
    }
  });

  app.post("/api/v1/safety/incidents", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(IncidentReportSchema, req.body);
    return repository.submitIncidentReport(req.authContext!.driverId, body);
  });

  app.get("/api/v1/safety/insurance", { preHandler: requireAuth }, async (req) => {
    return repository.getInsuranceStatus(req.authContext!.driverId);
  });

  app.get("/api/v1/support/tickets", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { cursor?: string; limit?: string };
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    return repository.getSupportTickets(req.authContext!.driverId, query.cursor ?? null, limit);
  });

  app.post("/api/v1/support/tickets", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(CreateSupportTicketSchema, req.body);
    return repository.createSupportTicket(req.authContext!.driverId, body);
  });

  app.get("/api/v1/support/tickets/:ticketId/messages", { preHandler: requireAuth }, async (req) => {
    const { ticketId } = req.params as { ticketId: string };
    return repository.getSupportMessages(req.authContext!.driverId, ticketId);
  });

  app.post("/api/v1/support/tickets/:ticketId/messages", { preHandler: requireAuth }, async (req) => {
    const { ticketId } = req.params as { ticketId: string };
    const body = parseBody(CreateSupportMessageSchema, req.body);
    return repository.sendSupportMessage(req.authContext!.driverId, ticketId, body);
  });

  app.get("/api/v1/notifications", { preHandler: requireAuth }, async (req) => {
    const query = req.query as { cursor?: string; limit?: string };
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    return repository.getNotifications(req.authContext!.userId, query.cursor ?? null, limit);
  });

  app.post("/api/v1/notifications/:notificationId/read", { preHandler: requireAuth }, async (req) => {
    const { notificationId } = req.params as { notificationId: string };
    await repository.markNotificationRead(req.authContext!.userId, notificationId);
    return {};
  });

  app.get("/api/v1/notifications/preferences", { preHandler: requireAuth }, async (req) => {
    return repository.getNotificationPreferences(req.authContext!.userId);
  });

  app.put("/api/v1/notifications/preferences", { preHandler: requireAuth }, async (req) => {
    const body = parseBody(NotificationPreferencesSchema, req.body);
    return repository.updateNotificationPreferences(req.authContext!.userId, body);
  });

  // 8. Protected Back-Office Admin Endpoints (Strict Admin Auth + Audit Logs)
  app.post("/api/v1/admin/drivers/:driverId/documents/:documentId/review", async (req) => {
    const actor = assertAdminAuthorized(req, config);
    const { driverId, documentId } = req.params as { driverId: string; documentId: string };
    const body = parseBody(
      z.object({
        decision: z.enum(["APPROVED", "REJECTED"]),
        reason: z.string().max(255).optional().nullable()
      }),
      req.body
    );
    const updated = await repository.adminReviewDocument(
      actor,
      driverId,
      documentId,
      body.decision,
      body.reason,
      req.requestIdValue
    );
    realtimeHub.emitToDriver(driverId, "DRIVER_VERIFICATION_UPDATED", driverId);
    return updated;
  });

  app.post("/api/v1/admin/orders", async (req) => {
    const actor = assertAdminAuthorized(req, config);
    const body = parseBody(
      z.object({
        driverId: z.string().min(1),
        serviceCategoryCode: z.enum(["BIKE", "AUTO", "CAB", "DELIVERY"]),
        pickupLabel: z.string().min(2),
        pickupLat: z.number().finite(),
        pickupLng: z.number().finite(),
        dropLabel: z.string().min(2),
        dropLat: z.number().finite(),
        dropLng: z.number().finite(),
        distanceMeters: z.number().int().min(0),
        durationSeconds: z.number().int().min(0),
        incentiveMinor: z.number().int().min(0).optional(),
        expiresInSeconds: z.number().int().min(5).max(300).optional(),
        priorityLabel: z.string().optional().nullable()
      }),
      req.body
    );
    const created = await repository.adminCreateAndOfferOrder(actor, body, req.requestIdValue);
    realtimeHub.emitToDriver(body.driverId, "OFFER_CREATED", created.offerId);
    return created;
  });

  app.get("/api/v1/admin/audit-logs", async (req) => {
    assertAdminAuthorized(req, config);
    return repository.adminGetAuditLogs();
  });

  // 9. Authenticated Realtime WebSocket Gateway (04_TECHSPEC Section 5)
  app.get("/ws/v1/driver", { websocket: true }, async (socket, req) => {
    try {
      const authHeader = req.headers.authorization;
      const queryToken = (req.query as { token?: string }).token;
      const rawToken =
        authHeader?.startsWith("Bearer ") ? authHeader.slice(7).trim() : queryToken;
      if (!rawToken) {
        socket.close(4401, "Unauthorized");
        return;
      }
      const claims = await verifyBearerIdToken(rawToken, config);
      const session = await repository.getSessionByFirebaseUid(claims.firebaseUid);
      if (!session) {
        socket.close(4401, "Session not found");
        return;
      }
      realtimeHub.register(session.driverId, socket);
    } catch {
      socket.close(4401, "Invalid token");
    }
  });

  return app;
}
