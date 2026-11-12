import { FastifyReply } from "fastify";

export class AppError extends Error {
  public readonly httpStatus: number;
  public readonly code: string;
  public readonly retryable: boolean;
  public readonly details: Record<string, string>;

  constructor(params: {
    httpStatus: number;
    code: string;
    message: string;
    retryable?: boolean;
    details?: Record<string, string>;
  }) {
    super(params.message);
    this.httpStatus = params.httpStatus;
    this.code = params.code;
    this.retryable = params.retryable ?? false;
    this.details = params.details ?? {};
  }
}

export function sendErrorEnvelope(
  reply: FastifyReply,
  requestId: string,
  err: unknown
): FastifyReply {
  if (err instanceof AppError) {
    return reply.status(err.httpStatus).send({
      error: {
        code: err.code,
        message: err.message,
        requestId,
        retryable: err.retryable,
        details: err.details
      }
    });
  }
  const message = err instanceof Error ? err.message : "Unexpected internal server error.";
  return reply.status(500).send({
    error: {
      code: "INTERNAL_ERROR",
      message,
      requestId,
      retryable: true,
      details: {}
    }
  });
}

export const ErrorCodes = {
  INVALID_INPUT: "INVALID_INPUT",
  INVALID_TOKEN: "INVALID_TOKEN",
  UNAUTHORIZED: "UNAUTHORIZED",
  FORBIDDEN_CROSS_DRIVER: "FORBIDDEN_CROSS_DRIVER",
  FORBIDDEN_ADMIN_ONLY: "FORBIDDEN_ADMIN_ONLY",
  DUPLICATE_VEHICLE_REGISTRATION: "DUPLICATE_VEHICLE_REGISTRATION",
  ACTIVE_TRIP_LOGOUT_BLOCKED: "ACTIVE_TRIP_LOGOUT_BLOCKED",
  DRIVER_INELIGIBLE: "DRIVER_INELIGIBLE",
  DRIVER_OFFLINE: "DRIVER_OFFLINE",
  OFFER_EXPIRED: "OFFER_EXPIRED",
  ORDER_ALREADY_ASSIGNED: "ORDER_ALREADY_ASSIGNED",
  OFFER_ALREADY_CLAIMED: "OFFER_ALREADY_CLAIMED",
  OFFER_NOT_FOUND: "OFFER_NOT_FOUND",
  INVALID_TRIP_TRANSITION: "INVALID_TRIP_TRANSITION",
  VERSION_CONFLICT: "VERSION_CONFLICT",
  INVALID_START_PIN: "INVALID_START_PIN",
  TRIP_NOT_FOUND: "TRIP_NOT_FOUND",
  ORDER_NOT_FOUND: "ORDER_NOT_FOUND",
  LIVE_PAYOUTS_DISABLED: "LIVE_PAYOUTS_DISABLED",
  INSUFFICIENT_BALANCE: "INSUFFICIENT_BALANCE",
  PAYOUT_BELOW_MINIMUM: "PAYOUT_BELOW_MINIMUM",
  PAYOUT_ABOVE_MAXIMUM: "PAYOUT_ABOVE_MAXIMUM",
  INVALID_PAYOUT_ACCOUNT: "INVALID_PAYOUT_ACCOUNT",
  STATEMENT_NOT_FOUND: "STATEMENT_NOT_FOUND",
  IDEMPOTENCY_CONFLICT: "IDEMPOTENCY_CONFLICT",
  INVALID_WEBHOOK_SIGNATURE: "INVALID_WEBHOOK_SIGNATURE",
  SELF_REFERRAL_FORBIDDEN: "SELF_REFERRAL_FORBIDDEN",
  REFERRAL_CODE_NOT_FOUND: "REFERRAL_CODE_NOT_FOUND",
  SOS_RESPONDER_UNCONFIGURED: "SOS_RESPONDER_UNCONFIGURED",
  TICKET_NOT_FOUND: "TICKET_NOT_FOUND",
  DOCUMENT_NOT_FOUND: "DOCUMENT_NOT_FOUND",
  RATE_LIMITED: "RATE_LIMITED"
} as const;
