import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { buildApp } from "../src/app.js";
import { loadConfig } from "../src/config/env.js";
import { signDevJwt } from "../src/middleware/auth.js";
import { SupabasePrivateStorageUploader } from "../src/modules/storage/supabaseStorage.js";
import { InMemoryTestBackendRepository } from "./helpers/inMemoryTestRepo.js";

describe("Supabase Private Storage Signing & Canonical Session Auth Tests", () => {
  it("requests signed private KYC upload URL from Supabase Storage without exposing secret key", async () => {
    const cfg = loadConfig({
      NODE_ENV: "development",
      AUTH_MODE: "supabase",
      SUPABASE_URL: "https://zdkoqxkxeayimjzqxfvi.supabase.co",
      SUPABASE_SECRET_KEY: "test-server-only-secret",
      SUPABASE_STORAGE_BUCKET: "drive-partner-kyc-private"
    });

    let requestedUrl = "";
    let sentSecretHeader: string | null = null;
    const uploader = new SupabasePrivateStorageUploader(cfg, (async (input, init) => {
      requestedUrl = String(input);
      const headers = new Headers(init?.headers);
      sentSecretHeader = headers.get("apikey");
      return {
        ok: true,
        json: async () => ({
          url: "/object/upload/sign/drive-partner-kyc-private/private/kyc/d1/dl.jpg?token=signed123"
        })
      } as unknown as Response;
    }) as typeof fetch);

    const signed = await uploader.createSignedUploadUrl({
      objectKey: "private/kyc/d1/dl.jpg",
      mimeType: "image/jpeg"
    });

    assert.equal(
      requestedUrl,
      "https://zdkoqxkxeayimjzqxfvi.supabase.co/storage/v1/object/upload/sign/drive-partner-kyc-private/private/kyc/d1/dl.jpg"
    );
    assert.equal(sentSecretHeader, "test-server-only-secret");
    assert.equal(
      signed.uploadUrl,
      "https://zdkoqxkxeayimjzqxfvi.supabase.co/storage/v1/object/upload/sign/drive-partner-kyc-private/private/kyc/d1/dl.jpg?token=signed123"
    );
    assert.equal(signed.uploadUrl.includes("test-server-only-secret"), false);
  });

  it("establishes session via canonical idToken payload on POST /api/v1/auth/session", async () => {
    const secret = "supabase-session-test-secret";
    const cfg = loadConfig({
      NODE_ENV: "test",
      AUTH_MODE: "hs256-dev",
      JWT_DEV_SECRET: secret
    });
    const repo = new InMemoryTestBackendRepository();
    const app = await buildApp({ config: cfg, repository: repo });

    const token = signDevJwt({ sub: "supa-uid-42", phone_number: "+919876500042" }, secret);
    const res = await app.inject({
      method: "POST",
      url: "/api/v1/auth/session",
      payload: { idToken: token, phoneE164: "+919876500042" }
    });
    assert.equal(res.statusCode, 200);
    assert.equal(res.json().requiresProfile, true);

    await app.close();
  });
});
