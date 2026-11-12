import { BackendConfig } from "../../config/env.js";
import { AppError } from "../../common/errors.js";

export interface SignedUploadIntentResult {
  uploadUrl: string;
  objectKey: string;
  expiresAtUtc: string;
  requiredHeaders: Record<string, string>;
}

/**
 * Issues short-lived private signed upload URLs via Supabase Storage REST API
 * (`POST /storage/v1/object/upload/sign/{bucket}/{path}`) using server-side
 * `SUPABASE_SECRET_KEY`. Never embeds or leaks the secret key to Android.
 */
export interface StorageSignedUploader {
  createSignedUploadUrl(params: {
    objectKey: string;
    mimeType: string;
  }): Promise<SignedUploadIntentResult>;
}

export class SupabasePrivateStorageUploader implements StorageSignedUploader {
  private readonly baseUrl: string;
  private readonly secretKey: string;
  private readonly bucket: string;
  private readonly nodeEnv: string;

  constructor(
    config: Pick<
      BackendConfig,
      "SUPABASE_URL" | "SUPABASE_SECRET_KEY" | "SUPABASE_STORAGE_BUCKET" | "NODE_ENV"
    >,
    private readonly fetchFn: typeof fetch = fetch
  ) {
    this.baseUrl = config.SUPABASE_URL.trim().replace(/\/+$/, "");
    this.secretKey = config.SUPABASE_SECRET_KEY.trim();
    this.bucket = config.SUPABASE_STORAGE_BUCKET.trim() || "drive-partner-kyc-private";
    this.nodeEnv = config.NODE_ENV;
  }

  async createSignedUploadUrl(params: {
    objectKey: string;
    mimeType: string;
  }): Promise<SignedUploadIntentResult> {
    const cleanKey = params.objectKey.replace(/^\/+/, "");
    // Supabase signed upload tokens are valid for 2 hours per Storage API spec.
    const expiresAtUtc = new Date(Date.now() + 7200 * 1000).toISOString();

    if (!this.baseUrl || !this.secretKey) {
      if (this.nodeEnv === "production") {
        throw new AppError({
          httpStatus: 503,
          code: "STORAGE_UNCONFIGURED",
          message: "SUPABASE_URL and SUPABASE_SECRET_KEY are required for signed KYC uploads."
        });
      }
      return {
        uploadUrl: `https://${this.bucket}.storage.local/${cleanKey}`,
        objectKey: cleanKey,
        expiresAtUtc,
        requiredHeaders: { "Content-Type": params.mimeType }
      };
    }

    const endpoint = `${this.baseUrl}/storage/v1/object/upload/sign/${encodeURIComponent(this.bucket)}/${cleanKey}`;
    const resp = await this.fetchFn(endpoint, {
      method: "POST",
      headers: {
        apikey: this.secretKey,
        Authorization: `Bearer ${this.secretKey}`,
        "Content-Type": "application/json",
        "x-upsert": "true"
      },
      body: JSON.stringify({})
    });

    if (!resp.ok) {
      throw new AppError({
        httpStatus: 503,
        code: "STORAGE_SIGN_FAILED",
        message: "Unable to create private KYC storage upload URL.",
        retryable: true
      });
    }

    const payload = (await resp.json()) as { url?: string; signedURL?: string };
    const relativePath = payload.url ?? payload.signedURL ?? "";
    if (!relativePath) {
      throw new AppError({
        httpStatus: 503,
        code: "STORAGE_SIGN_FAILED",
        message: "Supabase Storage did not return a signed upload URL."
      });
    }
    const uploadUrl = relativePath.startsWith("http")
      ? relativePath
      : `${this.baseUrl}/storage/v1${relativePath.startsWith("/") ? "" : "/"}${relativePath}`;

    return {
      uploadUrl,
      objectKey: cleanKey,
      expiresAtUtc,
      requiredHeaders: {
        "Content-Type": params.mimeType,
        "x-upsert": "true"
      }
    };
  }
}
