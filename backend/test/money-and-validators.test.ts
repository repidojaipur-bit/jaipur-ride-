import assert from "node:assert/strict";
import { createSign, generateKeyPairSync } from "node:crypto";
import { describe, it } from "node:test";
import { sanitizeLogPayload } from "../src/common/logger.js";
import { assertBalancedDoubleEntry, computeFareBreakdownMinor } from "../src/common/money.js";
import {
  assertPrivateObjectKey,
  containsRawAadhaarDigits,
  normalizeVehicleRegistration
} from "../src/common/validators.js";
import { loadConfig, resolveSupabaseJwksUrl } from "../src/config/env.js";
import { clearAuthKeyCaches, signDevJwt, verifyBearerIdToken } from "../src/middleware/auth.js";

function signAsymmetricJwt(
  alg: "ES256" | "RS256",
  kid: string,
  privateKey: ReturnType<typeof generateKeyPairSync>["privateKey"],
  payload: Record<string, unknown>
): string {
  const headerB64 = Buffer.from(JSON.stringify({ alg, typ: "JWT", kid })).toString("base64url");
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signingInput = `${headerB64}.${payloadB64}`;
  const signer = createSign("SHA256");
  signer.update(signingInput);
  const sig =
    alg === "ES256"
      ? signer.sign({ key: privateKey, dsaEncoding: "ieee-p1363" }).toString("base64url")
      : signer.sign(privateKey).toString("base64url");
  return `${signingInput}.${sig}`;
}

describe("Money, Ledger, Validators & Auth Security Unit Tests", () => {
  it("computes deterministic integer paise fare breakdowns without floating-point drift", () => {
    const bike = computeFareBreakdownMinor({
      category: "BIKE",
      distanceMeters: 5250,
      durationSeconds: 900,
      incentiveMinor: 1500
    });
    assert.equal(bike.baseFareMinor, 2000);
    assert.equal(bike.distanceFareMinor, 4200);
    assert.equal(bike.timeFareMinor, 1500);
    assert.equal(bike.incentiveMinor, 1500);
    assert.equal(bike.grossAmountMinor, 9200);
    assert.equal(Number.isInteger(bike.netEarningMinor), true);
    assert.equal(bike.netEarningMinor, bike.grossAmountMinor - bike.platformFeeMinor - bike.taxMinor);

    // Minimum fare enforcement for ultra-short trip
    const shortAuto = computeFareBreakdownMinor({
      category: "AUTO",
      distanceMeters: 200,
      durationSeconds: 60,
      incentiveMinor: 0
    });
    assert.equal(shortAuto.grossAmountMinor, 4000);
  });

  it("rejects unbalanced double-entry ledger postings and non-positive legs", () => {
    assert.doesNotThrow(() =>
      assertBalancedDoubleEntry([
        { accountId: "platform", direction: "DEBIT", amountMinor: 5000 },
        { accountId: "driver-1", direction: "CREDIT", amountMinor: 5000 }
      ])
    );

    assert.throws(
      () =>
        assertBalancedDoubleEntry([
          { accountId: "platform", direction: "DEBIT", amountMinor: 5000 },
          { accountId: "driver-1", direction: "CREDIT", amountMinor: 4999 }
        ]),
      /Unbalanced ledger posting/
    );

    assert.throws(() =>
      assertBalancedDoubleEntry([
        { accountId: "platform", direction: "DEBIT", amountMinor: -100 },
        { accountId: "driver-1", direction: "CREDIT", amountMinor: -100 }
      ])
    );
  });

  it("blocks raw Aadhaar digits and public HTTP storage URLs", () => {
    assert.equal(containsRawAadhaarDigits("1234 5678 9012"), true);
    assert.equal(containsRawAadhaarDigits("123456789012"), true);
    assert.equal(containsRawAadhaarDigits("DIGILOCKER-VERIF-9012"), false);

    assert.throws(() => assertPrivateObjectKey("https://s3.amazonaws.com/public/dl.jpg"));
    assert.throws(() => assertPrivateObjectKey("../secret"));
    assert.doesNotThrow(() => assertPrivateObjectKey("private/kyc/driver-1/dl.jpg"));

    assert.equal(normalizeVehicleRegistration("rj 14-ab 1234"), "RJ14AB1234");
  });

  it("redacts tokens, phone numbers, supabase keys, and account numbers from log payloads", () => {
    const raw = `{"authorization":"Bearer abc.def","idToken":"jwt.part.sig","apikey":"sb_secret_123456","phoneNumber":"+919876543210","accountNumber":"123456789012","city":"JAI"}`;
    const masked = sanitizeLogPayload(raw);
    assert.equal(masked.includes("abc.def"), false);
    assert.equal(masked.includes("jwt.part.sig"), false);
    assert.equal(masked.includes("sb_secret_123456"), false);
    assert.equal(masked.includes("9876543210"), false);
    assert.equal(masked.includes("123456789012"), false);
    assert.equal(masked.includes("JAI"), true);
  });

  it("verifies Supabase JWKS ES256 and RS256 JWTs, enforces issuer/audience, and handles key rotation", async () => {
    clearAuthKeyCaches();
    const ec = generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const rsa = generateKeyPairSync("rsa", { modulusLength: 2048 });
    const ecJwk = { ...ec.publicKey.export({ format: "jwk" }), kid: "ec-key-1", alg: "ES256", use: "sig" };
    const rsaJwk = { ...rsa.publicKey.export({ format: "jwk" }), kid: "rsa-key-1", alg: "RS256", use: "sig" };

    const cfg = loadConfig({
      NODE_ENV: "test",
      AUTH_MODE: "supabase",
      SUPABASE_URL: "https://zdkoqxkxeayimjzqxfvi.supabase.co"
    });
    assert.equal(
      resolveSupabaseJwksUrl(cfg),
      "https://zdkoqxkxeayimjzqxfvi.supabase.co/auth/v1/.well-known/jwks.json"
    );

    let jwksFetches = 0;
    const mockFetch = (async () => {
      jwksFetches += 1;
      const keys = jwksFetches === 1 ? [ecJwk] : [ecJwk, rsaJwk];
      return {
        ok: true,
        headers: new Headers({ "cache-control": "max-age=300" }),
        json: async () => ({ keys })
      } as unknown as Response;
    }) as typeof fetch;

    const nowSec = Math.floor(Date.now() / 1000);
    const esToken = signAsymmetricJwt("ES256", "ec-key-1", ec.privateKey, {
      sub: "supabase-user-ec-1",
      aud: "authenticated",
      iss: "https://zdkoqxkxeayimjzqxfvi.supabase.co/auth/v1",
      phone: "+919876543210",
      exp: nowSec + 600
    });
    const esClaims = await verifyBearerIdToken(esToken, cfg, mockFetch);
    assert.equal(esClaims.authUid, "supabase-user-ec-1");
    assert.equal(esClaims.phoneE164, "+919876543210");

    // Trigger key rotation refresh when unknown kid ("rsa-key-1") appears
    const rsaToken = signAsymmetricJwt("RS256", "rsa-key-1", rsa.privateKey, {
      sub: "supabase-user-rsa-2",
      aud: "authenticated",
      iss: "https://zdkoqxkxeayimjzqxfvi.supabase.co/auth/v1",
      exp: nowSec + 600
    });
    const rsaClaims = await verifyBearerIdToken(rsaToken, cfg, mockFetch);
    assert.equal(rsaClaims.authUid, "supabase-user-rsa-2");
    assert.equal(jwksFetches, 2);

    // Reject wrong issuer
    const wrongIss = signAsymmetricJwt("ES256", "ec-key-1", ec.privateKey, {
      sub: "supabase-user-ec-1",
      aud: "authenticated",
      iss: "https://evil.supabase.co/auth/v1",
      exp: nowSec + 600
    });
    await assert.rejects(() => verifyBearerIdToken(wrongIss, cfg, mockFetch));

    // Reject missing authenticated audience
    const wrongAud = signAsymmetricJwt("ES256", "ec-key-1", ec.privateKey, {
      sub: "supabase-user-ec-1",
      aud: "anon",
      iss: "https://zdkoqxkxeayimjzqxfvi.supabase.co/auth/v1",
      exp: nowSec + 600
    });
    await assert.rejects(() => verifyBearerIdToken(wrongAud, cfg, mockFetch));
  });

  it("forbids hs256-dev auth mode in production and rejects expired/tampered JWTs", async () => {
    assert.throws(
      () =>
        loadConfig({
          NODE_ENV: "production",
          DATABASE_URL: "postgresql://x:y@localhost:5432/db",
          AUTH_MODE: "hs256-dev"
        }),
      /hs256-dev AUTH_MODE is forbidden/
    );

    const cfg = loadConfig({
      NODE_ENV: "test",
      AUTH_MODE: "hs256-dev",
      JWT_DEV_SECRET: "unit-test-secret-key"
    });
    const valid = signDevJwt({ sub: "uid-1", phone_number: "+919876543210" }, cfg.JWT_DEV_SECRET);
    const claims = await verifyBearerIdToken(valid, cfg);
    assert.equal(claims.firebaseUid, "uid-1");

    const tampered = `${valid.slice(0, -4)}XXXX`;
    await assert.rejects(() => verifyBearerIdToken(tampered, cfg));

    const expired = signDevJwt(
      { sub: "uid-1", exp: Math.floor(Date.now() / 1000) - 10 },
      cfg.JWT_DEV_SECRET
    );
    await assert.rejects(() => verifyBearerIdToken(expired, cfg));
  });
});
