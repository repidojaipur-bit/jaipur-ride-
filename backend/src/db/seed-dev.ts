import { loadConfig } from "../config/env.js";
import { DEFAULT_FARE_RULES } from "../common/money.js";
import { createPgPool } from "./pool.js";

/**
 * Explicit local/staging development seed script (NEVER executed implicitly on app startup).
 * Seeds Jaipur operational service zones, baseline fare configs, and an initial campaign.
 */
async function seedDevelopmentData() {
  const config = loadConfig();
  if (config.NODE_ENV === "production") {
    throw new Error("Refusing to run dev seed script in production.");
  }
  if (!config.DATABASE_URL) {
    throw new Error("DATABASE_URL is required for seed:dev.");
  }

  const pool = createPgPool(config);
  try {
    await pool.query("BEGIN");

    await pool.query(
      `
      INSERT INTO service_zones (zone_code, name, city_code, boundary, enabled)
      VALUES
        (
          'JAI-CENTRAL',
          'Jaipur Central / MI Road',
          'JAI',
          ST_GeogFromText('MULTIPOLYGON(((75.77 26.89, 75.84 26.89, 75.84 26.94, 75.77 26.94, 75.77 26.89)))'),
          TRUE
        ),
        (
          'JAI-MALVIYA',
          'Malviya Nagar & Airport',
          'JAI',
          ST_GeogFromText('MULTIPOLYGON(((75.79 26.81, 75.86 26.81, 75.86 26.88, 75.79 26.88, 75.79 26.81)))'),
          TRUE
        )
      ON CONFLICT (zone_code) DO NOTHING;
      `
    );

    for (const [category, rules] of Object.entries(DEFAULT_FARE_RULES)) {
      await pool.query(
        `
        INSERT INTO fare_configs (category, version, currency, config, status)
        VALUES ($1::service_category, $2, 'INR', $3::jsonb, 'ACTIVE')
        ON CONFLICT DO NOTHING;
        `,
        [category, rules.version, JSON.stringify(rules)]
      );
    }

    await pool.query("COMMIT");
    console.log("Development seed completed successfully.");
  } catch (err) {
    await pool.query("ROLLBACK");
    throw err;
  } finally {
    await pool.end();
  }
}

seedDevelopmentData().catch((err) => {
  console.error("Dev seed failed:", err);
  process.exit(1);
});
