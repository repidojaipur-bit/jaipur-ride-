package com.drivepartner.app.core.auth

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.OnboardingValidators
import com.drivepartner.app.data.remote.dto.AuthErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class PhoneAuthProviderMode {
    DEMO_SANDBOX,
    SUPABASE_UNCONFIGURED,
    SUPABASE_CONFIGURED
}

data class OtpChallengeResult(
    val verificationId: String,
    val phoneE164: String,
    val resendAfterSeconds: Int,
    val demoOtpHint: String? = null,
    val dataOrigin: DataOrigin
)

/**
 * Boundary wrapping Supabase Phone Auth OTP verification (GoTrue `/auth/v1/otp` & `/auth/v1/verify`).
 *
 * Architectural guarantee (`00_MASTER.md` / `06_RULES` AUTH-001..003 / `03_APPFLOW` S-02):
 * - DEMO mode runs strictly isolated against [DemoPhoneAuthBoundary] (`123456` sandbox OTP).
 * - LIVE mode uses HTTPS Supabase Auth with only the public `SUPABASE_PUBLISHABLE_KEY`
 *   (never `SUPABASE_SECRET_KEY`), and returns `503 LIVE_AUTH_UNCONFIGURED` when unconfigured.
 */
interface PhoneAuthBoundary {
    val providerMode: PhoneAuthProviderMode
    suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult>
    suspend fun verifyOtp(verificationId: String, otpCode: String): ApiResult<String>
}

class DemoPhoneAuthBoundary(
    environmentConfig: AppEnvironmentConfig
) : PhoneAuthBoundary {

    init {
        require(environmentConfig.isDemoMode) {
            "DemoPhoneAuthBoundary cannot be instantiated in LIVE mode."
        }
    }

    override val providerMode: PhoneAuthProviderMode = PhoneAuthProviderMode.DEMO_SANDBOX

    private var lastIssuedPhoneByVerificationId = mutableMapOf<String, String>()

    override suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult> {
        val clean = phone10Digits.trim()
        if (!OnboardingValidators.isValidIndianPhone(clean)) {
            return ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_PHONE,
                    message = "Enter a valid 10-digit Indian mobile number starting with 6–9."
                )
            )
        }
        val verificationId = "DEMO-VERIF-${clean.takeLast(4)}"
        val phoneE164 = "+91$clean"
        lastIssuedPhoneByVerificationId[verificationId] = phoneE164
        return ApiResult.Success(
            data = OtpChallengeResult(
                verificationId = verificationId,
                phoneE164 = phoneE164,
                resendAfterSeconds = 30,
                demoOtpHint = DEMO_SANDBOX_OTP,
                dataOrigin = DataOrigin.DEMO
            ),
            origin = DataOrigin.DEMO
        )
    }

    override suspend fun verifyOtp(verificationId: String, otpCode: String): ApiResult<String> {
        if (!OnboardingValidators.isValidOtp(otpCode.trim())) {
            return ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_OTP,
                    message = "Enter a valid 6-digit OTP."
                )
            )
        }
        if (otpCode.trim() != DEMO_SANDBOX_OTP) {
            return ApiResult.HttpError(
                httpStatus = 401,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_OTP,
                    message = "Invalid DEMO OTP. Use $DEMO_SANDBOX_OTP for isolated sandbox testing."
                )
            )
        }
        val phoneE164 = lastIssuedPhoneByVerificationId[verificationId] ?: "+910000000000"
        return ApiResult.Success(
            data = "DEMO-SANDBOX-TOKEN-$phoneE164",
            origin = DataOrigin.DEMO
        )
    }

    companion object {
        const val DEMO_SANDBOX_OTP = "123456"
    }
}

@Serializable
private data class SupabaseOtpRequest(
    val phone: String,
    @SerialName("create_user") val createUser: Boolean = true
)

@Serializable
private data class SupabaseVerifyOtpRequest(
    val phone: String,
    val token: String,
    val type: String = "sms"
)

@Serializable
private data class SupabaseSessionTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long = 3600
)

@Serializable
private data class SupabaseAuthErrorBody(
    val msg: String? = null,
    val message: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val code: String? = null
)

class LivePhoneAuthBoundary(
    private val environmentConfig: AppEnvironmentConfig = AppEnvironmentConfig.unconfiguredLive(),
    private val httpClient: OkHttpClient = defaultHttpClient()
) : PhoneAuthBoundary {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val providerMode: PhoneAuthProviderMode
        get() = if (environmentConfig.isSupabaseConfigured) {
            PhoneAuthProviderMode.SUPABASE_CONFIGURED
        } else {
            PhoneAuthProviderMode.SUPABASE_UNCONFIGURED
        }

    override suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult> {
        val clean = phone10Digits.trim()
        if (!OnboardingValidators.isValidIndianPhone(clean)) {
            return ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_PHONE,
                    message = "Enter a valid 10-digit Indian mobile number starting with 6–9."
                )
            )
        }
        if (!environmentConfig.isSupabaseConfigured) {
            return ApiResult.HttpError(
                httpStatus = 503,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.LIVE_AUTH_UNCONFIGURED,
                    message = "Supabase Phone Auth is not configured yet (missing SUPABASE_URL / publishable key)."
                )
            )
        }

        val phoneE164 = "+91$clean"
        val baseUrl = environmentConfig.supabaseUrl.trim().trimEnd('/')
        val bodyJson = json.encodeToString(
            SupabaseOtpRequest.serializer(),
            SupabaseOtpRequest(phone = phoneE164, createUser = true)
        )
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/otp")
            .header("apikey", environmentConfig.supabasePublishableKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        ApiResult.Success(
                            data = OtpChallengeResult(
                                verificationId = phoneE164,
                                phoneE164 = phoneE164,
                                resendAfterSeconds = 60,
                                demoOtpHint = null,
                                dataOrigin = DataOrigin.LIVE
                            ),
                            origin = DataOrigin.LIVE
                        )
                    } else {
                        val parsedErr = runCatching {
                            json.decodeFromString(SupabaseAuthErrorBody.serializer(), raw)
                        }.getOrNull()
                        val msg = parsedErr?.msg ?: parsedErr?.message ?: parsedErr?.errorDescription
                            ?: "Unable to send OTP via Supabase Auth."
                        val code = when (response.code) {
                            429 -> AuthErrorCodes.RATE_LIMITED
                            400, 422 -> AuthErrorCodes.INVALID_PHONE
                            else -> AuthErrorCodes.LIVE_AUTH_UNCONFIGURED
                        }
                        ApiResult.HttpError(
                            httpStatus = response.code,
                            error = ApiErrorEnvelope(code = code, message = msg)
                        )
                    }
                }
            } catch (err: Exception) {
                ApiResult.NetworkError(causeMessage = err.message ?: "Network error requesting OTP")
            }
        }
    }

    override suspend fun verifyOtp(verificationId: String, otpCode: String): ApiResult<String> {
        val cleanOtp = otpCode.trim()
        if (!OnboardingValidators.isValidOtp(cleanOtp)) {
            return ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_OTP,
                    message = "Enter a valid 6-digit OTP."
                )
            )
        }
        if (!environmentConfig.isSupabaseConfigured) {
            return ApiResult.HttpError(
                httpStatus = 503,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.LIVE_AUTH_UNCONFIGURED,
                    message = "Supabase Phone Auth verification is not configured yet."
                )
            )
        }

        val baseUrl = environmentConfig.supabaseUrl.trim().trimEnd('/')
        val bodyJson = json.encodeToString(
            SupabaseVerifyOtpRequest.serializer(),
            SupabaseVerifyOtpRequest(phone = verificationId, token = cleanOtp, type = "sms")
        )
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/verify")
            .header("apikey", environmentConfig.supabasePublishableKey)
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val session = runCatching {
                            json.decodeFromString(SupabaseSessionTokenResponse.serializer(), raw)
                        }.getOrNull()
                        val jwt = session?.accessToken.orEmpty()
                        if (jwt.isBlank()) {
                            ApiResult.HttpError(
                                httpStatus = 502,
                                error = ApiErrorEnvelope(
                                    code = AuthErrorCodes.INVALID_TOKEN,
                                    message = "Supabase Auth response missing access token."
                                )
                            )
                        } else {
                            ApiResult.Success(data = jwt, origin = DataOrigin.LIVE)
                        }
                    } else {
                        val parsedErr = runCatching {
                            json.decodeFromString(SupabaseAuthErrorBody.serializer(), raw)
                        }.getOrNull()
                        val msg = parsedErr?.msg ?: parsedErr?.message ?: parsedErr?.errorDescription
                            ?: "OTP verification failed."
                        val code = when (response.code) {
                            429 -> AuthErrorCodes.RATE_LIMITED
                            else -> AuthErrorCodes.INVALID_OTP
                        }
                        ApiResult.HttpError(
                            httpStatus = response.code,
                            error = ApiErrorEnvelope(code = code, message = msg)
                        )
                    }
                }
            } catch (err: Exception) {
                ApiResult.NetworkError(causeMessage = err.message ?: "Network error verifying OTP")
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
