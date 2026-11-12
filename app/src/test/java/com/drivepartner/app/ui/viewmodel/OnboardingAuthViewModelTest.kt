package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.auth.DemoPhoneAuthBoundary
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.AppMode
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.local.InMemoryDemoAuthPersistence
import com.drivepartner.app.data.model.OnboardingStage
import com.drivepartner.app.data.model.RegistrationStep
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.repository.DemoAuthOnboardingCoordinator
import com.drivepartner.app.data.repository.DemoAuthOnboardingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingAuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val demoConfig = AppEnvironmentConfig(
        appMode = AppMode.DEMO,
        environmentName = "demo",
        apiBaseUrl = "",
        realtimeWsUrl = ""
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themePreference_fromStorageParsesLightDarkSystem() {
        assertEquals(ThemePreference.DARK, ThemePreference.fromStorage("dark"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage("SYSTEM"))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStorage(null))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStorage("unknown"))
    }

    @Test
    fun onboardingViewModel_walksFullDemoWizardAndReviewerFlow() = runTest(testDispatcher) {
        val repo = DemoAuthOnboardingRepository(
            demoDataSource = DemoDataSource(demoConfig),
            phoneAuthBoundary = DemoPhoneAuthBoundary(demoConfig),
            coordinator = DemoAuthOnboardingCoordinator(
                persistence = InMemoryDemoAuthPersistence(),
                startAuthenticatedActive = false
            )
        )
        val viewModel = OnboardingAuthViewModel(authRepository = repo)
        advanceUntilIdle()

        viewModel.startOnboardingPreview()
        assertEquals(OnboardingStage.SPLASH_LANGUAGE, viewModel.uiState.value.stage)

        viewModel.continueFromSplash()
        assertEquals(OnboardingStage.PHONE_LOGIN, viewModel.uiState.value.stage)

        viewModel.updatePhoneInput("9876543210")
        viewModel.requestOtp()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isOtpSent)

        viewModel.updateOtpInput(DemoPhoneAuthBoundary.DEMO_SANDBOX_OTP)
        viewModel.verifyOtp()
        advanceUntilIdle()
        assertEquals(OnboardingStage.REGISTRATION_WIZARD, viewModel.uiState.value.stage)
        assertEquals(RegistrationStep.PERSONAL, viewModel.uiState.value.registrationStep)

        viewModel.savePersonalDetails(
            fullName = "Ravi Kumar",
            cityCode = "JAI",
            language = AppLanguage.ENGLISH,
            dateOfBirth = "1994-03-10",
            gender = "MALE",
            addressLine = "Vaishali Nagar",
            stateCode = "RJ",
            pinCode = "302021"
        )
        advanceUntilIdle()
        assertEquals(RegistrationStep.VEHICLE, viewModel.uiState.value.registrationStep)

        viewModel.saveVehicleDetails(
            serviceType = ServiceType.AUTO,
            registrationNumber = "RJ14PA9988",
            brand = "Bajaj",
            model = "RE Compact",
            color = "Yellow",
            yearText = "2023"
        )
        advanceUntilIdle()
        assertEquals(RegistrationStep.BANK, viewModel.uiState.value.registrationStep)

        viewModel.selectRegistrationStep(RegistrationStep.DOCUMENTS)
        DemoAuthOnboardingCoordinator.mandatoryDocTypes.forEach { docType ->
            val ref = if (docType == KycDocumentType.PAN_CARD) "ABCDE1234F" else "MASK-9988"
            viewModel.uploadAndRegisterDocument(
                documentType = docType,
                maskedReference = ref
            )
            advanceUntilIdle()
        }

        viewModel.submitForReview()
        advanceUntilIdle()
        assertEquals(OnboardingStage.VERIFICATION_PENDING, viewModel.uiState.value.stage)

        viewModel.simulateDemoReviewerDecision(approveAll = true)
        advanceUntilIdle()
        assertEquals(OnboardingStage.AUTHENTICATED_ACTIVE, viewModel.uiState.value.stage)
        assertTrue(viewModel.uiState.value.isAuthenticatedAndActive)

        // Logout blocked during active trip
        viewModel.logout(hasActiveTrip = true)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.isAuthenticatedAndActive)

        // Logout succeeds when idle
        viewModel.logout(hasActiveTrip = false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAuthenticatedAndActive)
        assertEquals(OnboardingStage.PHONE_LOGIN, viewModel.uiState.value.stage)
    }
}
