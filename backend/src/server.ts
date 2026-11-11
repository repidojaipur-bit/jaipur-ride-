import { loadConfig } from "./config/env.js";
import { buildApp } from "./app.js";
import { PgBackendRepository } from "./db/pgRepository.js";
import { runMigrations } from "./db/migrate.js";
import { createPgPool } from "./db/pool.js";
import { SupabasePrivateStorageUploader } from "./modules/storage/supabaseStorage.js";

async function start() {
  const config = loadConfig();
  if (!config.DATABASE_URL) {
    console.error("DATABASE_URL is required to start Drive Partner backend.");
    process.exit(1);
  }

  const pool = createPgPool(config);
  await runMigrations(pool);

  const storageUploader = new SupabasePrivateStorageUploader(config);
  const repository = new PgBackendRepository(pool, {
    objectStorageBucket: config.OBJECT_STORAGE_BUCKET,
    livePayoutsEnabled: config.LIVE_PAYOUTS_ENABLED,
    storageUploader
  });

  const app = await buildApp({ config, repository });

  const shutdown = async () => {
    await app.close();
    await pool.end();
    process.exit(0);
  };
  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  await app.listen({ host: config.HOST, port: config.PORT });
}

start().catch((err) => {
  console.error("Fatal backend startup error:", err);
  process.exit(1);
});
