import { Pool, PoolConfig } from "pg";
import { BackendConfig } from "../config/env.js";

/**
 * Builds a PostgreSQL Pool configured for both local Docker Compose PostgreSQL
 * and TLS-secured Supabase PostgreSQL / Supavisor connection strings.
 */
export function createPgPool(config: Pick<BackendConfig, "DATABASE_URL" | "PG_SSL">): Pool {
  const url = config.DATABASE_URL.trim();
  const useSsl =
    config.PG_SSL === "require" ||
    (config.PG_SSL === "auto" &&
      (url.includes(".supabase.co") ||
        url.includes(".supabase.com") ||
        url.includes("sslmode=require")));

  const poolConfig: PoolConfig = {
    connectionString: url,
    max: 15,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 10_000
  };

  if (useSsl) {
    poolConfig.ssl = { rejectUnauthorized: false };
  }

  return new Pool(poolConfig);
}
