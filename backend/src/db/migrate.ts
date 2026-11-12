import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Pool } from "pg";
import { loadConfig } from "../config/env.js";
import { createPgPool } from "./pool.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export async function runMigrations(pool: Pool, migrationsDir?: string): Promise<string[]> {
  const dir = migrationsDir ?? path.resolve(__dirname, "../../migrations");
  const files = (await readdir(dir)).filter((f) => f.endsWith(".sql")).sort();

  const client = await pool.connect();
  const applied: string[] = [];
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        filename VARCHAR(255) PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
    `);

    for (const file of files) {
      const existing = await client.query<{ filename: string }>(
        "SELECT filename FROM schema_migrations WHERE filename = $1",
        [file]
      );
      if (existing.rowCount && existing.rowCount > 0) {
        continue;
      }

      const sql = await readFile(path.join(dir, file), "utf8");
      await client.query("BEGIN");
      try {
        await client.query(sql);
        await client.query(
          "INSERT INTO schema_migrations (filename) VALUES ($1)",
          [file]
        );
        await client.query("COMMIT");
        applied.push(file);
      } catch (err) {
        await client.query("ROLLBACK");
        throw err;
      }
    }
  } finally {
    client.release();
  }
  return applied;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const config = loadConfig();
  if (!config.DATABASE_URL) {
    console.error("DATABASE_URL is required to run migrations.");
    process.exit(1);
  }
  const pool = createPgPool(config);
  runMigrations(pool)
    .then((applied) => {
      console.log("Applied migrations:", applied.length > 0 ? applied.join(", ") : "none (up to date)");
      return pool.end();
    })
    .catch(async (err) => {
      console.error("Migration failed:", err);
      await pool.end();
      process.exit(1);
    });
}
