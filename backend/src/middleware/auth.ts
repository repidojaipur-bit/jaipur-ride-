import {
  createHmac,
  createPublicKey,
  createVerify,
  JsonWebKey as NodeJsonWebKey,
  timingSafeEqual
} from "node:crypto";
import { FastifyRequest } from "fastify";
import { BackendConfig, resolveSupabaseJwksUrl } from "../config/env.js";
import { AppError, ErrorCodes } from "../common/errors.js";

export interface VerifiedTokenClaims {
  authUid: string;
  /** Transitional compatibility mirror of `authUid` */
  firebaseUid: string;
  phoneE164?: string;
}

export interface AuthContext {
  userId: string;
  driverId: string;
  authUid: string;
  /** Transitional compatibility mirror of `authUid` */
  firebaseUid: string;
  accountStatus: AccountStatus;
}

export type AccountStatus =
  | "APPLICANT"
  | "PENDING_REVIEW"
  | "ACTIVE"
  | "SUSPENDED"
  | "REJECTED"
  | "CLOSED";

export type ServiceCategory = "BIKE" | "AUTO" | "CAB" | "DELIVERY";

export type DocumentType =
  | "DRIVING_LICENSE"
  | "VEHICLE_RC"
  | "VEHICLE_INSURANCE"
  | "PERMIT"
  | "PAN_CARD"
  | "AADHAAR_COMPLIANT_REF"
  | "PROFILE_PHOTO";

export type DocumentStatus =
  | "NOT_SUBMITTED"
  | "UPLOADED"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "EXPIRED";

export type DutyStatus = "OFFLINE" | "GOING_ONLINE" | "ONLINE" | "GOING_OFFLINE" | "BLOCKED";
export type OfferStatus = "CREATED" | "OFFERED" | "ACCEPTED" | "REJECTED" | "EXPIRED" | "WITHDRAWN";
export type TripStatus =
  | "ASSIGNED"
  | "EN_ROUTE_TO_PICKUP"
  | "ARRIVED_AT_PICKUP"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED"
  | "DISPUTED";

export type PayoutStatus =
  | "REQUESTED"
  | "PROCESSING"
  | "PAID"
  | "FAILED"
  | "CANCELLED"
  | "REVERSED";

export type TicketStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "WAITING_FOR_DRIVER"
  | "RESOLVED"
  | "CLOSED";

interface JwkEntry extends NodeJsonWebKey {
  kid?: string;
  alg?: string;
  kty: string;
  use?: string;
}

interface CachedJwks {
  url: string;
  expiresAtMs: number;
  keys: JwkEntry[];
}

interface CachedCerts {
  expiresAtMs: number;
  certs: Record<string, string>;
}

const GOOGLE_CERTS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

let cachedJwks: CachedJwks | null = null;
let cachedCerts: CachedCerts | null = null;

export function clearAuthKeyCaches(): void {
  cachedJwks = null;
  cachedCerts = null;
}

async function fetchSupabaseJwks(
  jwksUrl: string,
  forceRefresh = false,
  fetchFn: typeof fetch = fetch
): Promise<JwkEntry[]> {
  const now = Date.now();
  if (!forceRefresh && cachedJwks && cachedJwks.url === jwksUrl && cachedJwks.expiresAtMs > now) {
    return cachedJwks.keys;
  }
  const resp = await fetchFn(jwksUrl);
  if (!resp.ok) {
    throw new AppError({
      httpStatus: 503,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Unable to fetch Supabase Auth JWKS signing keys.",
      retryable: true
    });
  }
  const cacheControl = resp.headers?.get?.("cache-control") ?? "";
  const maxAgeMatch = /max-age=(\d+)/i.exec(cacheControl);
  const maxAgeSec = maxAgeMatch?.[1] ? Math.min(3600, Number(maxAgeMatch[1])) : 600;
  const body = (await resp.json()) as { keys?: JwkEntry[] };
  const keys = Array.isArray(body.keys) ? body.keys : [];
  cachedJwks = {
    url: jwksUrl,
    keys,
    expiresAtMs: now + maxAgeSec * 1000
  };
  return keys;
}

async function fetchGooglePublicCerts(fetchFn: typeof fetch = fetch): Promise<Record<string, string>> {
  const now = Date.now();
  if (cachedCerts && cachedCerts.expiresAtMs > now) {
    return cachedCerts.certs;
  }
  const resp = await fetchFn(GOOGLE_CERTS_URL);
  if (!resp.ok) {
    throw new AppError({
      httpStatus: 503,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Unable to fetch Firebase public signing certificates.",
      retryable: true
    });
  }
  const cacheControl = resp.headers?.get?.("cache-control") ?? "";
  const maxAgeMatch = /max-age=(\d+)/i.exec(cacheControl);
  const maxAgeSec = maxAgeMatch?.[1] ? Number(maxAgeMatch[1]) : 3600;
  const certs = (await resp.json()) as Record<string, string>;
  cachedCerts = {
    certs,
    expiresAtMs: now + maxAgeSec * 1000
  };
  return certs;
}

function decodeJwtPart<T>(b64Url: string): T {
  const padded = b64Url.replace(/-/g, "+").replace(/_/g, "/");
  const jsonStr = Buffer.from(padded, "base64").toString("utf8");
  return JSON.parse(jsonStr) as T;
}

export function signDevJwt(
  payload: { sub: string; phone_number?: string; exp?: number },
  secret: string
): string {
  const headerB64 = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const fullPayload = {
    ...payload,
    iat: Math.floor(Date.now() / 1000),
    exp: payload.exp ?? Math.floor(Date.now() / 1000) + 3600
  };
  const payloadB64 = Buffer.from(JSON.stringify(fullPayload)).toString("base64url");
  const signingInput = `${headerB64}.${payloadB64}`;
  const sig = createHmac("sha256", secret).update(signingInput).digest("base64url");
  return `${signingInput}.${sig}`;
}

function matchesAudience(aud: string | string[] | undefined, expected: string): boolean {
  if (!aud) return false;
  return Array.isArray(aud) ? aud.includes(expected) : aud === expected;
}

export async function verifyBearerIdToken(
  token: string,
  config: BackendConfig,
  fetchFn: typeof fetch = fetch
): Promise<VerifiedTokenClaims> {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Malformed bearer token."
    });
  }
  const [headerPart, payloadPart, sigPart] = parts as [string, string, string];
  let header: { alg?: string; kid?: string };
  let payload: {
    sub?: string;
    aud?: string | string[];
    iss?: string;
    exp?: number;
    phone?: string;
    phone_number?: string;
  };
  try {
    header = decodeJwtPart(headerPart);
    payload = decodeJwtPart(payloadPart);
  } catch {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Malformed JWT payload."
    });
  }

  const nowSec = Math.floor(Date.now() / 1000);
  if (!payload.sub || typeof payload.exp !== "number" || payload.exp <= nowSec) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Bearer token is expired or missing subject."
    });
  }

  const resolvedPhone = payload.phone_number ?? payload.phone;

  if (config.AUTH_MODE === "hs256-dev") {
    if (!config.JWT_DEV_SECRET || header.alg !== "HS256") {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Invalid development JWT configuration or algorithm."
      });
    }
    const expectedSig = createHmac("sha256", config.JWT_DEV_SECRET)
      .update(`${headerPart}.${payloadPart}`)
      .digest();
    const providedSig = Buffer.from(sigPart, "base64url");
    if (
      expectedSig.length !== providedSig.length ||
      !timingSafeEqual(expectedSig, providedSig)
    ) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Bearer token signature mismatch."
      });
    }
    return {
      authUid: payload.sub,
      firebaseUid: payload.sub,
      phoneE164: resolvedPhone
    };
  }

  if (config.AUTH_MODE === "supabase") {
    const jwksUrl = resolveSupabaseJwksUrl(config);
    if (!jwksUrl) {
      throw new AppError({
        httpStatus: 503,
        code: "LIVE_AUTH_UNCONFIGURED",
        message: "SUPABASE_URL or SUPABASE_JWKS_URL is not configured on the backend."
      });
    }

    const supportedAlg = header.alg === "ES256" || header.alg === "RS256";
    if (!supportedAlg || !header.kid) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Supabase JWT signing algorithm or key ID invalid."
      });
    }

    const cleanSupabaseBase = config.SUPABASE_URL.trim().replace(/\/+$/, "");
    if (cleanSupabaseBase) {
      const expectedIssuer = `${cleanSupabaseBase}/auth/v1`;
      if (payload.iss !== expectedIssuer) {
        throw new AppError({
          httpStatus: 401,
          code: ErrorCodes.INVALID_TOKEN,
          message: "Supabase JWT issuer mismatch."
        });
      }
    }
    if (!matchesAudience(payload.aud, "authenticated")) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Supabase JWT audience must include authenticated."
      });
    }

    let keys = await fetchSupabaseJwks(jwksUrl, false, fetchFn);
    let jwk = keys.find((k) => k.kid === header.kid);
    if (!jwk) {
      // Handle seamless Supabase JWKS key rotation by forcing one refresh
      keys = await fetchSupabaseJwks(jwksUrl, true, fetchFn);
      jwk = keys.find((k) => k.kid === header.kid);
    }
    if (!jwk) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Supabase JWKS signing key rotated or unknown."
      });
    }

    const pubKey = createPublicKey({ key: jwk, format: "jwk" });
    const signatureBytes = Buffer.from(sigPart, "base64url");
    const signingInput = `${headerPart}.${payloadPart}`;
    const verifier = createVerify("SHA256");
    verifier.update(signingInput);
    const valid =
      header.alg === "ES256"
        ? verifier.verify({ key: pubKey, dsaEncoding: "ieee-p1363" }, signatureBytes)
        : verifier.verify(pubKey, signatureBytes);

    if (!valid) {
      throw new AppError({
        httpStatus: 401,
        code: ErrorCodes.INVALID_TOKEN,
        message: "Supabase JWT signature verification failed."
      });
    }

    return {
      authUid: payload.sub,
      firebaseUid: payload.sub,
      phoneE164: resolvedPhone
    };
  }

  // Legacy AUTH_MODE === "firebase" (RS256 verified against Google's X.509 signing certs)
  if (!config.FIREBASE_PROJECT_ID) {
    throw new AppError({
      httpStatus: 503,
      code: "LIVE_AUTH_UNCONFIGURED",
      message: "FIREBASE_PROJECT_ID is not configured on the backend."
    });
  }
  if (
    header.alg !== "RS256" ||
    !header.kid ||
    !matchesAudience(payload.aud, config.FIREBASE_PROJECT_ID) ||
    payload.iss !== `https://securetoken.google.com/${config.FIREBASE_PROJECT_ID}`
  ) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Firebase token claims or signing algorithm invalid."
    });
  }

  const certs = await fetchGooglePublicCerts(fetchFn);
  const certPem = certs[header.kid];
  if (!certPem) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Firebase signing key rotated or unknown."
    });
  }
  const pubKey = createPublicKey(certPem);
  const verifier = createVerify("RSA-SHA256");
  verifier.update(`${headerPart}.${payloadPart}`);
  const signatureBytes = Buffer.from(sigPart, "base64url");
  if (!verifier.verify(pubKey, signatureBytes)) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.INVALID_TOKEN,
      message: "Firebase ID token signature verification failed."
    });
  }

  return {
    authUid: payload.sub,
    firebaseUid: payload.sub,
    phoneE164: resolvedPhone
  };
}

export function extractBearerToken(req: FastifyRequest): string {
  const header = req.headers.authorization;
  if (!header || !header.startsWith("Bearer ")) {
    throw new AppError({
      httpStatus: 401,
      code: ErrorCodes.UNAUTHORIZED,
      message: "Missing Authorization: Bearer token."
    });
  }
  return header.slice("Bearer ".length).trim();
}

export function assertAdminAuthorized(req: FastifyRequest, config: BackendConfig): string {
  const provided = req.headers["x-admin-api-key"];
  const key = Array.isArray(provided) ? provided[0] : provided;
  if (!config.ADMIN_API_KEY || !key) {
    throw new AppError({
      httpStatus: 403,
      code: ErrorCodes.FORBIDDEN_ADMIN_ONLY,
      message: "Back-office admin API key required."
    });
  }
  const a = Buffer.from(config.ADMIN_API_KEY);
  const b = Buffer.from(key);
  if (a.length !== b.length || !timingSafeEqual(a, b)) {
    throw new AppError({
      httpStatus: 403,
      code: ErrorCodes.FORBIDDEN_ADMIN_ONLY,
      message: "Invalid back-office admin API key."
    });
  }
  return "admin-operator";
}
