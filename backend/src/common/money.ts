import { AppError, ErrorCodes } from "./errors.js";

export interface FareConfigRules {
  version: string;
  baseFareMinor: number;
  perKmMinor: number;
  perMinMinor: number;
  minimumFareMinor: number;
  platformFeeBps: number; // basis points (e.g. 1500 = 15.00%)
  taxBps: number;         // tax basis points on platform fee (e.g. 1800 = 18.00% GST)
}

export interface ComputedFareBreakdown {
  fareConfigVersion: string;
  baseFareMinor: number;
  distanceFareMinor: number;
  timeFareMinor: number;
  incentiveMinor: number;
  grossAmountMinor: number;
  platformFeeMinor: number;
  taxMinor: number;
  netEarningMinor: number;
}

export const DEFAULT_FARE_RULES: Record<string, FareConfigRules> = {
  BIKE: {
    version: "fare-bike-v1",
    baseFareMinor: 2000,
    perKmMinor: 800,
    perMinMinor: 100,
    minimumFareMinor: 2500,
    platformFeeBps: 1500,
    taxBps: 1800
  },
  AUTO: {
    version: "fare-auto-v1",
    baseFareMinor: 3000,
    perKmMinor: 1200,
    perMinMinor: 150,
    minimumFareMinor: 4000,
    platformFeeBps: 1500,
    taxBps: 1800
  },
  CAB: {
    version: "fare-cab-v1",
    baseFareMinor: 5000,
    perKmMinor: 1600,
    perMinMinor: 200,
    minimumFareMinor: 7000,
    platformFeeBps: 1800,
    taxBps: 1800
  },
  DELIVERY: {
    version: "fare-delivery-v1",
    baseFareMinor: 2500,
    perKmMinor: 900,
    perMinMinor: 100,
    minimumFareMinor: 3000,
    platformFeeBps: 1200,
    taxBps: 1800
  }
};

/**
 * Pure integer minor-unit (INR paise) fare breakdown computation.
 * Uses integer BigInt rounding only; zero IEEE-754 floating-point money math.
 */
export function computeFareBreakdownMinor(params: {
  category: string;
  distanceMeters: number;
  durationSeconds: number;
  incentiveMinor: number;
  rulesOverride?: FareConfigRules;
}): ComputedFareBreakdown {
  const rules =
    params.rulesOverride ??
    DEFAULT_FARE_RULES[params.category] ??
    DEFAULT_FARE_RULES.BIKE!;

  const distM = BigInt(Math.max(0, Math.round(params.distanceMeters)));
  const durS = BigInt(Math.max(0, Math.round(params.durationSeconds)));
  const incentive = BigInt(Math.max(0, Math.round(params.incentiveMinor)));

  const base = BigInt(rules.baseFareMinor);
  const distanceFare = (distM * BigInt(rules.perKmMinor) + 500n) / 1000n;
  const timeFare = (durS * BigInt(rules.perMinMinor) + 30n) / 60n;
  const minFare = BigInt(rules.minimumFareMinor);

  const rawRideTotal = base + distanceFare + timeFare;
  const rideGross = rawRideTotal > minFare ? rawRideTotal : minFare;
  const gross = rideGross + incentive;

  const platformFee = (rideGross * BigInt(rules.platformFeeBps) + 5000n) / 10000n;
  const tax = (platformFee * BigInt(rules.taxBps) + 5000n) / 10000n;
  const net = gross - platformFee - tax;

  return {
    fareConfigVersion: rules.version,
    baseFareMinor: Number(base),
    distanceFareMinor: Number(distanceFare),
    timeFareMinor: Number(timeFare),
    incentiveMinor: Number(incentive),
    grossAmountMinor: Number(gross),
    platformFeeMinor: Number(platformFee),
    taxMinor: Number(tax),
    netEarningMinor: Number(net > 0n ? net : 0n)
  };
}

export interface DoubleEntryLeg {
  accountId: string;
  direction: "DEBIT" | "CREDIT";
  amountMinor: number;
}

/**
 * Asserts strict double-entry ledger balance before persisting any ledger transaction.
 */
export function assertBalancedDoubleEntry(legs: DoubleEntryLeg[]): void {
  if (legs.length < 2) {
    throw new AppError({
      httpStatus: 500,
      code: "LEDGER_UNBALANCED",
      message: "Double-entry ledger transaction requires at least two legs."
    });
  }
  let debitTotal = 0n;
  let creditTotal = 0n;
  for (const leg of legs) {
    if (!Number.isInteger(leg.amountMinor) || leg.amountMinor <= 0) {
      throw new AppError({
        httpStatus: 422,
        code: ErrorCodes.INVALID_INPUT,
        message: "Ledger entry amountMinor must be a positive integer."
      });
    }
    if (leg.direction === "DEBIT") {
      debitTotal += BigInt(leg.amountMinor);
    } else {
      creditTotal += BigInt(leg.amountMinor);
    }
  }
  if (debitTotal !== creditTotal) {
    throw new AppError({
      httpStatus: 500,
      code: "LEDGER_UNBALANCED",
      message: `Unbalanced ledger posting detected: DEBIT=${debitTotal} CREDIT=${creditTotal}`
    });
  }
}
