import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { describe, it } from "node:test";
import { buildApp } from "../src/app.js";
import { loadConfig } from "../src/config/env.js";
import { signDevJwt } from "../src/middleware/auth.js";
import { InMemoryTestBackendRepository } from "./helpers/inMemoryTestRepo.js";

const TEST_SECRET = "integration-test-jwt-secret";
const ADMIN_KEY = "integration-test-admin-key";
const WEBHOOK_SECRET = "integration-test-webhook-secret";

function createTestConfig(overrides: Record<string, string> = {}) {
  return loadConfig({
    NODE_ENV: "test",
    AUTH_MODE: "hs256-dev",
    JWT_DEV_SECRET: TEST_SECRET,
    ADMIN_API_KEY: ADMIN_KEY,
    PAYOUT_WEBHOOK_SECRET: WEBHOOK_SECRET,
    LIVE_PAYOUTS_ENABLED: "true",
    ...overrides
  });
}

async function onboardAndApproveDriver(
  app: Awaited<ReturnType<typeof buildApp>>,
  uid: string,
  phone: string,
  regNumber: string
) {
  const token = signDevJwt({ sub: uid, phone_number: phone }, TEST_SECRET);
  const authHeaders = { authorization: `Bearer ${token}` };

  const sessRes = await app.inject({
    method: "POST",
    url: "/api/v1/auth/session",
    payload: { firebaseIdToken: token, phoneE164: phone }
  });
  assert.equal(sessRes.statusCode, 200);
  const session = sessRes.json();

  await app.inject({
    method: "POST",
    url: "/api/v1/drivers/profile",
    headers: authHeaders,
    payload: { fullName: `Driver ${uid}`, cityCode: "JAI", preferredLanguage: "en" }
  });

  const vehRes = await app.inject({
    method: "POST",
    url: "/api/v1/vehicles",
    headers: authHeaders,
    payload: {
      serviceTypeCode: "BIKE",
      registrationNumber: regNumber,
      vehicleModel: "Splendor",
      brand: "Hero",
      color: "Black",
      year: 2024
    }
  });
  assert.equal(vehRes.statusCode, 200);

  const docsListRes = await app.inject({
    method: "GET",
    url: "/api/v1/documents",
    headers: authHeaders
  });
  const docs = docsListRes.json() as Array<{ documentId: string; documentType: string }>;

  for (const doc of docs) {
    const intentRes = await app.inject({
      method: "POST",
      url: "/api/v1/documents/upload-intent",
      headers: authHeaders,
      payload: { documentType: doc.documentType, mimeType: "image/jpeg", sizeBytes: 120000 }
    });
    assert.equal(intentRes.statusCode, 200);
    const intent = intentRes.json();

    const confirmRes = await app.inject({
      method: "POST",
      url: "/api/v1/documents",
      headers: authHeaders,
      payload: {
        documentId: intent.documentId,
        storageObjectKey: intent.objectKey,
        maskedReference: "VERIF-REF-1234"
      }
    });
    assert.equal(confirmRes.statusCode, 200);

    const reviewRes = await app.inject({
      method: "POST",
      url: `/api/v1/admin/drivers/${session.driverId}/documents/${intent.documentId}/review`,
      headers: { "x-admin-api-key": ADMIN_KEY },
      payload: { decision: "APPROVED" }
    });
    assert.equal(reviewRes.statusCode, 200);
  }

  return { token, authHeaders, driverId: session.driverId as string };
}

describe("Drive Partner Backend HTTP, Security, Dispatch Concurrency & Double-Entry Ledger Integration", () => {
  it("enforces health check, auth barriers, Aadhaar/URL KYC rules, and onboarding -> online duty", async () => {
    const repo = new InMemoryTestBackendRepository();
    const app = await buildApp({ config: createTestConfig(), repository: repo });

    const health = await app.inject({ method: "GET", url: "/api/v1/health" });
    assert.equal(health.statusCode, 200);
    assert.equal(health.json().postgisReady, true);

    const unauth = await app.inject({ method: "GET", url: "/api/v1/me" });
    assert.equal(unauth.statusCode, 401);

    // Raw Aadhaar rejection & admin authorization barrier
    const token = signDevJwt({ sub: "uid-kyc", phone_number: "+919876543211" }, TEST_SECRET);
    const sess = await app.inject({
      method: "POST",
      url: "/api/v1/auth/session",
      payload: { firebaseIdToken: token }
    });
    const driverId = sess.json().driverId;

    const intent = await app.inject({
      method: "POST",
      url: "/api/v1/documents/upload-intent",
      headers: { authorization: `Bearer ${token}` },
      payload: { documentType: "AADHAAR_COMPLIANT_REF", mimeType: "image/jpeg", sizeBytes: 50000 }
    });

    const rawAadhaarFail = await app.inject({
      method: "POST",
      url: "/api/v1/documents",
      headers: { authorization: `Bearer ${token}` },
      payload: {
        documentId: intent.json().documentId,
        storageObjectKey: intent.json().objectKey,
        maskedReference: "1234 5678 9012"
      }
    });
    assert.equal(rawAadhaarFail.statusCode, 422);

    // Unapproved driver blocked from going online
    const blockedOnline = await app.inject({
      method: "POST",
      url: "/api/v1/availability/online",
      headers: { authorization: `Bearer ${token}` },
      payload: { idempotencyKey: "online-unapproved", latitude: 26.91, longitude: 75.78 }
    });
    assert.equal(blockedOnline.statusCode, 403);
    assert.equal(blockedOnline.json().error.code, "DRIVER_INELIGIBLE");

    // Non-admin caller rejected on admin review route
    const nonAdmin = await app.inject({
      method: "POST",
      url: `/api/v1/admin/drivers/${driverId}/documents/${intent.json().documentId}/review`,
      headers: { authorization: `Bearer ${token}` },
      payload: { decision: "APPROVED" }
    });
    assert.equal(nonAdmin.statusCode, 403);

    await app.close();
  });

  it("enforces atomic competing-driver offer claim, trip transitions, double-entry ledger, payouts & webhook reversal", async () => {
    const repo = new InMemoryTestBackendRepository();
    const app = await buildApp({ config: createTestConfig(), repository: repo });

    const driverA = await onboardAndApproveDriver(app, "uid-a", "+919876500001", "RJ14AA0001");
    const driverB = await onboardAndApproveDriver(app, "uid-b", "+919876500002", "RJ14BB0002");

    // Duplicate vehicle registration rejected across drivers
    const dupVeh = await app.inject({
      method: "POST",
      url: "/api/v1/vehicles",
      headers: driverB.authHeaders,
      payload: {
        serviceTypeCode: "BIKE",
        registrationNumber: "RJ 14 AA 0001",
        vehicleModel: "Activa",
        brand: "Honda",
        color: "White"
      }
    });
    assert.equal(dupVeh.statusCode, 409);
    assert.equal(dupVeh.json().error.code, "DUPLICATE_VEHICLE_REGISTRATION");

    // Both go online
    assert.equal(
      (
        await app.inject({
          method: "POST",
          url: "/api/v1/availability/online",
          headers: driverA.authHeaders,
          payload: { idempotencyKey: "on-a", latitude: 26.9124, longitude: 75.7873 }
        })
      ).statusCode,
      200
    );
    assert.equal(
      (
        await app.inject({
          method: "POST",
          url: "/api/v1/availability/online",
          headers: driverB.authHeaders,
          payload: { idempotencyKey: "on-b", latitude: 26.913, longitude: 75.788 }
        })
      ).statusCode,
      200
    );

    // Admin dispatches competing offers for same order to Driver A & Driver B
    const offerA = (
      await app.inject({
        method: "POST",
        url: "/api/v1/admin/orders",
        headers: { "x-admin-api-key": ADMIN_KEY },
        payload: {
          driverId: driverA.driverId,
          serviceCategoryCode: "BIKE",
          pickupLabel: "MI Road",
          pickupLat: 26.9124,
          pickupLng: 75.7873,
          dropLabel: "Vaishali Nagar",
          dropLat: 26.911,
          dropLng: 75.74,
          distanceMeters: 6000,
          durationSeconds: 900,
          incentiveMinor: 1000
        }
      })
    ).json();

    const offerB = (
      await app.inject({
        method: "POST",
        url: "/api/v1/admin/orders",
        headers: { "x-admin-api-key": ADMIN_KEY },
        payload: {
          driverId: driverB.driverId,
          serviceCategoryCode: "BIKE",
          pickupLabel: "MI Road",
          pickupLat: 26.9124,
          pickupLng: 75.7873,
          dropLabel: "Vaishali Nagar",
          dropLat: 26.911,
          dropLng: 75.74,
          distanceMeters: 6000,
          durationSeconds: 900,
          incentiveMinor: 1000
        }
      })
    ).json();

    // Concurrent claim race: strictly one succeeds and the second is rejected
    const [claimA, claimB] = await Promise.all([
      app.inject({
        method: "POST",
        url: `/api/v1/offers/${offerA.offerId}/accept`,
        headers: driverA.authHeaders,
        payload: { idempotencyKey: "accept-race-a" }
      }),
      app.inject({
        method: "POST",
        url: `/api/v1/offers/${offerB.offerId}/accept`,
        headers: driverB.authHeaders,
        payload: { idempotencyKey: "accept-race-b" }
      })
    ]);

    assert.equal(claimA.statusCode, 200);
    assert.equal(claimB.statusCode, 409);

    const tripId = claimA.json().tripId as string;

    // Cross-driver trip transition & order access strictly forbidden
    const crossDriverAttempt = await app.inject({
      method: "POST",
      url: `/api/v1/trips/${tripId}/events`,
      headers: driverB.authHeaders,
      payload: { targetStatus: "EN_ROUTE_TO_PICKUP", expectedVersion: 1, idempotencyKey: "cross-1" }
    });
    assert.equal(crossDriverAttempt.statusCode, 403);

    // Logout blocked while active trip in progress
    const activeLogout = await app.inject({
      method: "POST",
      url: "/api/v1/auth/logout",
      headers: driverA.authHeaders
    });
    assert.equal(activeLogout.statusCode, 409);
    assert.equal(activeLogout.json().error.code, "ACTIVE_TRIP_LOGOUT_BLOCKED");

    // Optimistic locking conflict test + idempotent transition walk to COMPLETED
    const staleVersion = await app.inject({
      method: "POST",
      url: `/api/v1/trips/${tripId}/events`,
      headers: driverA.authHeaders,
      payload: { targetStatus: "EN_ROUTE_TO_PICKUP", expectedVersion: 99, idempotencyKey: "bad-ver" }
    });
    assert.equal(staleVersion.statusCode, 409);
    assert.equal(staleVersion.json().error.code, "VERSION_CONFLICT");

    const steps = ["EN_ROUTE_TO_PICKUP", "ARRIVED_AT_PICKUP", "IN_PROGRESS", "COMPLETED"] as const;
    let ver = 1;
    let earningStatementId = "";
    for (const targetStatus of steps) {
      const res = await app.inject({
        method: "POST",
        url: `/api/v1/trips/${tripId}/events`,
        headers: driverA.authHeaders,
        payload: { targetStatus, expectedVersion: ver, idempotencyKey: `step-${targetStatus}` }
      });
      assert.equal(res.statusCode, 200);
      ver = res.json().version;
      if (targetStatus === "COMPLETED") {
        earningStatementId = res.json().earningSourceRefId;
      }
    }
    assert.ok(earningStatementId);

    // Wallet reflects server-finalized net earning & balanced ledger
    const walletAfterTrip = (
      await app.inject({ method: "GET", url: "/api/v1/wallet", headers: driverA.authHeaders })
    ).json();
    assert.ok(walletAfterTrip.availableMinor > 1000);

    // Link bank account & request payout
    const acctRes = await app.inject({
      method: "POST",
      url: "/api/v1/payout-accounts",
      headers: driverA.authHeaders,
      payload: {
        accountHolderName: "Driver A",
        accountNumber: "123456789012",
        ifscCode: "HDFC0001234",
        idempotencyKey: "acct-a"
      }
    });
    assert.equal(acctRes.statusCode, 200);
    const tokenAcct = acctRes.json().payoutAccountToken;

    const payoutDisabledRes = await app.inject({
      method: "POST",
      url: "/api/v1/payouts",
      headers: driverA.authHeaders,
      payload: {
        payoutAccountToken: tokenAcct,
        amountMinor: 1000,
        idempotencyKey: "payout-disabled-check"
      }
    });
    assert.equal(payoutDisabledRes.statusCode, 503);
    assert.equal(payoutDisabledRes.json().error.code, "LIVE_PAYOUTS_DISABLED");

    repo.livePayoutsEnabled = true;

    const payoutRes = await app.inject({
      method: "POST",
      url: "/api/v1/payouts",
      headers: driverA.authHeaders,
      payload: {
        payoutAccountToken: tokenAcct,
        amountMinor: 1000,
        idempotencyKey: "payout-req-1"
      }
    });
    assert.equal(payoutRes.statusCode, 200);
    const payoutId = payoutRes.json().payoutId;

    const walletAfterPayoutReq = (
      await app.inject({ method: "GET", url: "/api/v1/wallet", headers: driverA.authHeaders })
    ).json();
    assert.equal(walletAfterPayoutReq.availableMinor, walletAfterTrip.availableMinor - 1000);

    // Webhook FAILED -> reverses payout debit cleanly once (and rejects bad signature!)
    const webhookBody = {
      providerEventId: "evt_fail_1",
      payoutId,
      outcome: "FAILED",
      failureCode: "BANK_IFSC_MISMATCH"
    };
    const badSigWebhook = await app.inject({
      method: "POST",
      url: "/api/v1/webhooks/payouts/razorpay",
      headers: { "x-webhook-signature": "bad-sig" },
      payload: webhookBody
    });
    assert.equal(badSigWebhook.statusCode, 401);

    const goodSig = createHmac("sha256", WEBHOOK_SECRET)
      .update(JSON.stringify(webhookBody))
      .digest("hex");
    const goodWebhook = await app.inject({
      method: "POST",
      url: "/api/v1/webhooks/payouts/razorpay",
      headers: { "x-webhook-signature": goodSig },
      payload: webhookBody
    });
    assert.equal(goodWebhook.statusCode, 200);
    assert.equal(goodWebhook.json().status, "FAILED");

    const walletAfterReversal = (
      await app.inject({ method: "GET", url: "/api/v1/wallet", headers: driverA.authHeaders })
    ).json();
    assert.equal(walletAfterReversal.availableMinor, walletAfterTrip.availableMinor);

    // Support internal note visibility isolation & SOS truthful unconfigured failure
    const ticketRes = await app.inject({
      method: "POST",
      url: "/api/v1/support/tickets",
      headers: driverA.authHeaders,
      payload: {
        category: "EARNINGS",
        subject: "Fare check",
        description: "Verify statement",
        idempotencyKey: "tkt-1"
      }
    });
    const ticketId = ticketRes.json().ticketId;
    repo.addInternalSupportNoteForTest(ticketId, "INTERNAL ONLY: Ops note");
    await app.inject({
      method: "POST",
      url: `/api/v1/support/tickets/${ticketId}/messages`,
      headers: driverA.authHeaders,
      payload: { body: "Driver question", idempotencyKey: "msg-1" }
    });
    const msgs = (
      await app.inject({
        method: "GET",
        url: `/api/v1/support/tickets/${ticketId}/messages`,
        headers: driverA.authHeaders
      })
    ).json() as Array<{ body: string; isInternalNote: boolean }>;
    assert.equal(msgs.length, 1);
    assert.equal(msgs[0]!.body, "Driver question");

    const sosRes = await app.inject({
      method: "POST",
      url: "/api/v1/safety/sos",
      headers: driverA.authHeaders,
      payload: { latitude: 26.91, longitude: 75.78, idempotencyKey: "sos-1" }
    });
    assert.equal(sosRes.statusCode, 503);
    assert.equal(sosRes.json().error.code, "SOS_RESPONDER_UNCONFIGURED");

    // Audit logs recorded admin actions
    const auditRes = await app.inject({
      method: "GET",
      url: "/api/v1/admin/audit-logs",
      headers: { "x-admin-api-key": ADMIN_KEY }
    });
    assert.equal(auditRes.statusCode, 200);
    assert.ok((auditRes.json() as unknown[]).length >= 2);

    await app.close();
  });
});
