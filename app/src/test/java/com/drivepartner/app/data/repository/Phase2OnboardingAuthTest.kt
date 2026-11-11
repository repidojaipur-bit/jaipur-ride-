package com.drivepartner.app.data.repository

import com.drivepartner.app.core.auth.DemoPhoneAuthBoundary
import com.drivepartner.app.core.auth.LivePhoneAuthBoundary
import com.drivepartner.app.core.auth.PhoneAuthProviderMode
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.InMemoryAuthSessionTokenProvider
import com.drivepartner.app.core.network.RequestMetadataInterceptor
import com.drivepartner.app.core.network.UnconfiguredApiTransport
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.local.InMemoryDemoAuthPersistence
import com.drivepartner.app.data.model.OnboardingValidators
import com.drivepartner.app.data.remote.dto.AuthErrorCodes
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2OnboardingAuthTest {

    private val demoConfig = AppEnvironmentConfig(
        appMode = AppMode.DEMO,
        environmentName = "demo",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    private val liveConfig = AppEnvironmentConfig(
        appMode = AppMode.LIVE,
        environmentName = "live",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    @Test
    fun onboardingValidators_enforceFormatsAndBlockRawAadhaar() {
        assertTrue(OnboardingValidators.isValidIndianPhone("9876543210"))
        assertFalse(OnboardingValidators.isValidIndianPhone("1234567890"))
        assertTrue(OnboardingValidators.isValidOtp("123456"))
        assertFalse(OnboardingValidators.isValidOtp("12345"))
        assertTrue(OnboardingValidators.isValidPinCode("302001"))
        assertFalse(OnboardingValidators.isValidPinCode("012345"))
        assertTrue(OnboardingValidators.isValidVehicleRegistration("RJ 14 AB 1234"))
        assertEquals("RJ14AB1234", OnboardingValidators.normalizeVehicleRegistration("rj 14-ab 1234"))
        assertTrue(OnboardingValidators.isValidIfsc("HDFC0001234"))
        assertFalse(OnboardingValidators.isValidIfsc("HDFC1001234"))
        assertTrue(OnboardingValidators.isValidBankAccount("123456789012"))
        assertTrue(OnboardingValidators.isValidPanReference("ABCDE1234F"))

        assertTrue(OnboardingValidators.containsRawAadhaarDigits("1234 5678 9012"))
        assertTrue(OnboardingValidators.containsRawAadhaarDigits("123456789012"))
        assertFalse(OnboardingValidators.containsRawAadhaarDigits("DIGILOCKER-REF-9012"))
    }

    @Test
    fun requestMetadataInterceptor_attachesBearerTokenAndStripsXSkipAuth() {
        val tokenProvider = InMemoryAuthSessionTokenProvider("secret-jwt-token")
        var observedAuthHeader: String? = null
        var observedSkipHeader: String? = null
        var observedReqId: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(RequestMetadataInterceptor(tokenProvider = tokenProvider))
            .addInterceptor { chain ->
                val req = chain.request()
                observedAuthHeader = req.header("Authorization")
                observedSkipHeader = req.header("X-Skip-Auth")
                observedReqId = req.header("X-Request-Id")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        client.newCall(Request.Builder().url("https://api.example.com/api/v1/me").build()).execute().close()
        assertEquals("Bearer secret-jwt-token", observedAuthHeader)
        assertNotNull(observedReqId)

        client.newCall(
            Request.Builder()
                .url("https://api.example.com/api/v1/auth/session")
                .header("X-Skip-Auth", "true")
                .build()
        ).execute().close()
        assertNull(observedAuthHeader)
        assertNull(observedSkipHeader)
    }

    @Test
    fun demoAuthOnboardingRepository_runsCompleteWizardValidationAndReview() = runTest {
        val coordinator = DemoAuthOnboardingCoordinator(
            persistence = InMemoryDemoAuthPersistence(),
            startAuthenticatedActive = false
        )
        val repo = DemoAuthOnboardingRepository(
            demoDataSource = DemoDataSource(demoConfig),
            phoneAuthBoundary = DemoPhoneAuthBoundary(demoConfig),
            coordinator = coordinator
        )

        val otpReq = repo.requestOtp("9876543210") as ApiResult.Success
        val badVerify = repo.verifyOtpAndEstablishSession(otpReq.data.verificationId, "000000")
        assertTrue(badVerify is ApiResult.HttpError)

        val sessionRes = repo.verifyOtpAndEstablishSession(
            otpReq.data.verificationId,
            DemoPhoneAuthBoundary.DEMO_SANDBOX_OTP
        ) as ApiResult.Success
        assertEquals(DataOrigin.DEMO, sessionRes.data.dataOrigin)
        assertTrue(sessionRes.data.requiresProfile)

        val profileRes = repo.saveProfile(
            DriverProfileRequest(
                fullName = "Arjun Sharma",
                cityCode = "JAI",
                preferredLanguage = "en",
                pinCode = "302001"
            )
        )
        assertTrue(profileRes is ApiResult.Success)

        val vehicleRes = repo.saveVehicle(
            VehicleDetailsRequest(
                serviceTypeCode = "BIKE",
                registrationNumber = "rj14ab4321",
                vehicleModel = "Splendor",
                brand = "Hero",
                color = "Black",
                year = 2024
            )
        ) as ApiResult.Success
        assertEquals("RJ14AB4321", vehicleRes.data.registrationNumber)

        val intent = repo.requestUploadIntent(
            DocumentUploadIntentRequest(KycDocumentType.AADHAAR_COMPLIANT_REF)
        ) as ApiResult.Success

        // Raw 12-digit Aadhaar must be rejected
        val rawAadhaarRes = repo.registerDocument(
            ConfirmDocumentUploadRequest(
                documentId = intent.data.documentId,
                storageObjectKey = intent.data.objectKey,
                maskedReference = "1234 5678 9012"
            )
        )
        assertTrue(rawAadhaarRes is ApiResult.HttpError)

        // Public http URL key must be rejected
        val publicUrlRes = repo.registerDocument(
            ConfirmDocumentUploadRequest(
                documentId = intent.data.documentId,
                storageObjectKey = "https://public.bucket/file.jpg"
            )
        )
        assertTrue(publicUrlRes is ApiResult.HttpError)

        // Upload all mandatory docs cleanly -> PENDING_REVIEW
        for (docType in DemoAuthOnboardingCoordinator.mandatoryDocTypes) {
            val docIntent = repo.requestUploadIntent(DocumentUploadIntentRequest(docType)) as ApiResult.Success
            val confirmed = repo.registerDocument(
                ConfirmDocumentUploadRequest(
                    documentId = docIntent.data.documentId,
                    storageObjectKey = docIntent.data.objectKey,
                    maskedReference = "VERIF-REF-9012"
                )
            )
            assertTrue(confirmed is ApiResult.Success)
        }

        val pendingSession = (repo.fetchCurrentSession() as ApiResult.Success).data
        assertEquals(DriverAccountStatus.PENDING_REVIEW, pendingSession.accountStatus)

        // Reviewer approves -> ACTIVE
        val approved = (repo.simulateDemoReviewOutcome(DriverAccountStatus.ACTIVE) as ApiResult.Success).data
        assertEquals(DriverAccountStatus.ACTIVE, approved.accountStatus)
        assertTrue(approved.canAccessMainDashboard)

        // Logout blocked during active trip
        val blockedLogout = repo.logout(hasActiveTrip = true) as ApiResult.HttpError
        assertEquals(AuthErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED, blockedLogout.error.code)

        // Logout allowed when idle
        assertTrue(repo.logout(hasActiveTrip = false) is ApiResult.Success)
        assertTrue(repo.isSignedOut.value)
    }

    @Test
    fun liveAuthOnboardingRepository_neverFakesOtpOrApprovalWhenUnconfigured() = runTest {
        val tokenProvider = InMemoryAuthSessionTokenProvider()
        val livePhoneAuth = LivePhoneAuthBoundary(environmentConfig = liveConfig)
        assertEquals(PhoneAuthProviderMode.SUPABASE_UNCONFIGURED, livePhoneAuth.providerMode)

        val liveRepo = LiveAuthOnboardingRepository(
            liveDataSource = LiveApiDataSource(liveConfig, UnconfiguredApiTransport(liveConfig)),
            phoneAuthBoundary = livePhoneAuth,
            tokenProvider = tokenProvider
        )

        val otpRes = liveRepo.requestOtp("9876543210") as ApiResult.HttpError
        assertEquals(AuthErrorCodes.LIVE_AUTH_UNCONFIGURED, otpRes.error.code)

        val simRes = liveRepo.simulateDemoReviewOutcome(DriverAccountStatus.ACTIVE)
        assertTrue(simRes is ApiResult.BackendNotDeployed)

        val sessionRes = liveRepo.fetchCurrentSession() as ApiResult.HttpError
        assertEquals(401, sessionRes.httpStatus)
    }

    @Test
    fun livePhoneAuthBoundary_callsSupabaseGoTrueOtpAndVerifyEndpoints() = runTest {
        val configuredLive = liveConfig.copy(
            supabaseUrl = "https://example.supabase.co",
            supabasePublishableKey = "sb_publishable_test_key"
        )
        val capturedUrls = mutableListOf<String>()
        val capturedApiKeys = mutableListOf<String?>()
        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrls += req.url.encodedPath
                capturedApiKeys += req.header("apikey")
                val body = when (req.url.encodedPath) {
                    "/auth/v1/otp" -> """{}"""
                    "/auth/v1/verify" -> """{"access_token":"supabase-jwt-123","token_type":"bearer"}"""
                    else -> "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val boundary = LivePhoneAuthBoundary(configuredLive, fakeClient)
        assertEquals(PhoneAuthProviderMode.SUPABASE_CONFIGURED, boundary.providerMode)

        val challenge = boundary.requestOtp("9876543210") as ApiResult.Success
        assertEquals("+919876543210", challenge.data.phoneE164)

        val jwt = boundary.verifyOtp(challenge.data.verificationId, "654321") as ApiResult.Success
        assertEquals("supabase-jwt-123", jwt.data)
        assertEquals(listOf("/auth/v1/otp", "/auth/v1/verify"), capturedUrls)
        assertEquals(listOf("sb_publishable_test_key", "sb_publishable_test_key"), capturedApiKeys)
    }
}
