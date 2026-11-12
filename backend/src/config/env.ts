import { z } from "zod";

const EnvSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  DATABASE_URL: z.string().default(""),
  PG_SSL: z.enum(["auto", "require", "disable"]).default("auto"),
  AUTH_MODE: z.enum(["supabase", "hs256-dev", "firebase"]).default("supabase"),
  SUPABASE_URL: z.string().default(""),
  SUPABASE_JWKS_URL: z.string().default(""),
  SUPABASE_SECRET_KEY: z.string().default(""),
  SUPABASE_STORAGE_BUCKET: z.string().default("drive-partner-kyc-private"),
  FIREBASE_PROJECT_ID: z.string().default(""),
  JWT_DEV_SECRET: z.string().default(""),
  ADMIN_API_KEY: z.string().default(""),
  OBJECT_STORAGE_BUCKET: z.string().default("drive-partner-kyc-private"),
  LIVE_PAYOUTS_ENABLED: z
    .string()
    .default("false")
    .transform((v) => v.toLowerCase() === "true"),
  PAYOUT_WEBHOOK_SECRET: z.string().default(""),
  SOS_RESPONDER_WEBHOOK_URL: z.string().default(""),
  CORS_ALLOWED_ORIGINS: z.string().default("")
});

export type BackendConfig = z.infer<typeof EnvSchema>;

export function resolveSupabaseJwksUrl(config: Pick<BackendConfig, "SUPABASE_URL" | "SUPABASE_JWKS_URL">): string {
  const explicit = config.SUPABASE_JWKS_URL.trim();
  if (explicit) return explicit;
  const base = config.SUPABASE_URL.trim().replace(/\/+$/, "");
  return base ? `${base}/auth/v1/.well-known/jwks.json` : "";
}

export function loadConfig(rawEnv: NodeJS.ProcessEnv = process.env): BackendConfig {
  const parsed = EnvSchema.parse(rawEnv);
  if (parsed.NODE_ENV === "production" && parsed.AUTH_MODE === "hs256-dev") {
    throw new Error("hs256-dev AUTH_MODE is forbidden when NODE_ENV=production.");
  }
  if (parsed.NODE_ENV === "production" && !parsed.DATABASE_URL) {
    throw new Error("DATABASE_URL is required in production mode.");
  }
  const effectiveJwks = resolveSupabaseJwksUrl(parsed);
  return {
    ...parsed,
    SUPABASE_JWKS_URL: effectiveJwks,
    OBJECT_STORAGE_BUCKET: parsed.SUPABASE_STORAGE_BUCKET || parsed.OBJECT_STORAGE_BUCKET
  };
}
