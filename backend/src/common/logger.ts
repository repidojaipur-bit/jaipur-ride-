const SENSITIVE_KEY_PATTERN =
  /"(authorization|apikey|idToken|firebaseIdToken|sessionToken|supabaseSecretKey|otp|token|accountNumber|ifscCode|phoneNumber|phoneE164|password|secret)"\s*:\s*("[^"]*"|\d+)/gi;

const BEARER_PATTERN = /(Bearer\s+)[A-Za-z0-9._-]+/gi;
const SECRET_PREFIX_PATTERN = /(sb_secret_[A-Za-z0-9._-]+|sb_publishable_[A-Za-z0-9._-]+)/gi;
const QUERY_SECRET_PATTERN = /([?&](?:token|otp|phone|secret|apikey|apiKey)=)([^&\s]+)/gi;

export function sanitizeLogPayload(input: string): string {
  return input
    .replace(SENSITIVE_KEY_PATTERN, '"$1":"[REDACTED]"')
    .replace(BEARER_PATTERN, "$1[REDACTED]")
    .replace(SECRET_PREFIX_PATTERN, "[REDACTED_KEY]")
    .replace(QUERY_SECRET_PATTERN, "$1[REDACTED]");
}
