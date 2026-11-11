package com.drivepartner.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.notifications.DrivePartnerNotificationBridge
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.OnboardingStage
import com.drivepartner.app.data.model.RegistrationStep
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.navigation.DriveNavHost
import com.drivepartner.app.ui.components.DriveBottomNavigation
import com.drivepartner.app.ui.components.DriveDrawerSheet
import com.drivepartner.app.ui.components.DriveSecondaryTopBar
import com.drivepartner.app.ui.components.DriveTopBar
import com.drivepartner.app.ui.screens.onboarding.PhoneOtpLoginScreen
import com.drivepartner.app.ui.screens.onboarding.RegistrationWizardScreen
import com.drivepartner.app.ui.screens.onboarding.SplashLanguageScreen
import com.drivepartner.app.ui.screens.onboarding.VerificationStatusScreen
import com.drivepartner.app.ui.screens.support.SupportRoute
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.viewmodel.MainViewModel
import com.drivepartner.app.ui.viewmodel.OnboardingAuthViewModel
import com.drivepartner.app.ui.viewmodel.SupportViewModel
import kotlinx.coroutines.launch

/**
 * MainActivity - Single Activity entry point for Drive Partner.
 * Uses AppCompatActivity so AppCompatDelegate per-app EN/HI localization works
 * reliably across Android API 24-35 alongside Jetpack Compose & Navigation.
 */
class MainActivity : AppCompatActivity() {

    private val appContainer: AppContainer by lazy {
        (application as DrivePartnerApp).appContainer
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.provideFactory(appContainer)
    }

    private val onboardingViewModel: OnboardingAuthViewModel by viewModels {
        OnboardingAuthViewModel.provideFactory(appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1769AA.toInt())
        )
        val initialDeepLink = intent?.getStringExtra(DrivePartnerNotificationBridge.EXTRA_DEEP_LINK_ROUTE)

        setContent {
            val uiState by viewModel.mainUiState.collectAsState()
            DrivePartnerTheme(themePreference = uiState.themePreference) {
                DrivePartnerMainContent(
                    viewModel = viewModel,
                    onboardingViewModel = onboardingViewModel,
                    appContainer = appContainer,
                    initialDeepLinkRoute = initialDeepLink,
                    onApplyLocale = ::applyAppLocale
                )
            }
        }
    }

    private fun applyAppLocale(language: AppLanguage) {
        val targetLocales = LocaleListCompat.forLanguageTags(language.bcp47Tag)
        if (AppCompatDelegate.getApplicationLocales() != targetLocales) {
            AppCompatDelegate.setApplicationLocales(targetLocales)
        }
    }
}

@Composable
fun DrivePartnerMainContent(
    viewModel: MainViewModel,
    onboardingViewModel: OnboardingAuthViewModel,
    appContainer: AppContainer,
    onApplyLocale: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
    initialDeepLinkRoute: String? = null
) {
    val uiState by viewModel.mainUiState.collectAsState()
    val onboardingState by onboardingViewModel.uiState.collectAsState()
    val activeTrip by appContainer.dispatchRepository.activeTrip.collectAsState()
    val hasActiveTrip = activeTrip != null

    if (!onboardingState.isAuthenticatedAndActive) {
        var showingOnboardingSupport by remember { mutableStateOf(false) }

        if (showingOnboardingSupport) {
            val supportViewModel: SupportViewModel = viewModel(
                factory = SupportViewModel.provideFactory(appContainer)
            )
            Scaffold(
                topBar = {
                    DriveSecondaryTopBar(
                        title = stringResource(R.string.support_title),
                        onBackClick = { showingOnboardingSupport = false }
                    )
                }
            ) { padding ->
                SupportRoute(
                    viewModel = supportViewModel,
                    modifier = Modifier.padding(padding)
                )
            }
            return
        }

        Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
            when (onboardingState.stage) {
                OnboardingStage.SPLASH_LANGUAGE -> {
                    SplashLanguageScreen(
                        dataOrigin = uiState.dataOrigin,
                        selectedLanguage = uiState.preferredLanguage,
                        isLoading = onboardingState.isLoading,
                        onSelectLanguage = { lang ->
                            viewModel.setLanguage(lang, onLanguageSelected = onApplyLocale)
                        },
                        onContinue = { onboardingViewModel.continueFromSplash() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                OnboardingStage.PHONE_LOGIN -> {
                    PhoneOtpLoginScreen(
                        uiState = onboardingState,
                        onPhoneChanged = onboardingViewModel::updatePhoneInput,
                        onOtpChanged = onboardingViewModel::updateOtpInput,
                        onRequestOtp = onboardingViewModel::requestOtp,
                        onVerifyOtp = onboardingViewModel::verifyOtp,
                        onEditPhone = onboardingViewModel::editPhoneNumber,
                        onOpenSettingsLanguage = {
                            viewModel.toggleLanguage(onLanguageSelected = onApplyLocale)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                OnboardingStage.REGISTRATION_WIZARD -> {
                    RegistrationWizardScreen(
                        uiState = onboardingState,
                        selectedLanguage = uiState.preferredLanguage,
                        onSelectStep = onboardingViewModel::selectRegistrationStep,
                        onSavePersonal = { fullName, city, lang, dob, gender, address, state, pin ->
                            onboardingViewModel.savePersonalDetails(
                                fullName = fullName,
                                cityCode = city,
                                language = lang,
                                dateOfBirth = dob,
                                gender = gender,
                                addressLine = address,
                                stateCode = state,
                                pinCode = pin,
                                advanceStep = true
                            )
                        },
                        onSaveVehicle = { serviceType, reg, brand, model, color, year ->
                            onboardingViewModel.saveVehicleDetails(
                                serviceType = serviceType,
                                registrationNumber = reg,
                                brand = brand,
                                model = model,
                                color = color,
                                yearText = year,
                                advanceStep = true
                            )
                        },
                        onSaveBank = { holder, acct, ifsc ->
                            onboardingViewModel.saveBankAccountStep(
                                accountHolderName = holder,
                                accountNumber = acct,
                                ifscCode = ifsc,
                                advanceStep = true
                            )
                        },
                        onUploadDocument = { docType, mimeType, sizeBytes, maskedRef, expires ->
                            onboardingViewModel.uploadAndRegisterDocument(
                                documentType = docType,
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                maskedReference = maskedRef,
                                expiresOnUtc = expires
                            )
                        },
                        onSubmitForReview = onboardingViewModel::submitForReview,
                        onLogout = { onboardingViewModel.logout(hasActiveTrip = hasActiveTrip) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                OnboardingStage.VERIFICATION_PENDING,
                OnboardingStage.AUTHENTICATED_ACTIVE -> {
                    VerificationStatusScreen(
                        uiState = onboardingState,
                        onRefreshStatus = onboardingViewModel::refreshOnboardingState,
                        onFixDocuments = onboardingViewModel::openDocumentsFixStep,
                        onContactSupport = { showingOnboardingSupport = true },
                        onSimulateDemoApproval = { approve ->
                            onboardingViewModel.simulateDemoReviewerDecision(
                                approveAll = approve,
                                rejectType = if (approve) null else KycDocumentType.DRIVING_LICENSE
                            )
                        },
                        onLogout = { onboardingViewModel.logout(hasActiveTrip = hasActiveTrip) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: DriveDestination.Home.route
    val currentDestination = DriveDestination.fromRoute(currentRoute)

    LaunchedEffect(initialDeepLinkRoute) {
        if (!initialDeepLinkRoute.isNullOrBlank()) {
            val dest = DriveDestination.fromRoute(initialDeepLinkRoute)
            navController.navigate(dest.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        viewModel.setDestination(currentRoute)
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    fun navigateToDestination(destination: DriveDestination) {
        if (destination.isPrimaryTab) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(destination.route) {
                launchSingleTop = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentDestination.isPrimaryTab,
        drawerContent = {
            DriveDrawerSheet(
                currentRoute = currentRoute,
                dataOrigin = uiState.dataOrigin,
                partnerProfile = uiState.partnerProfile,
                onSelectDestination = { destination ->
                    coroutineScope.launch { drawerState.close() }
                    navigateToDestination(destination)
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentDestination.isPrimaryTab) {
                        DriveTopBar(
                            status = uiState.partnerStatus,
                            serviceType = uiState.selectedService,
                            dutyControlState = uiState.dutyControlState,
                            hasNotifications = uiState.isNotificationBadgeActive,
                            onMenuClick = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            onStatusToggle = { viewModel.toggleDutyStatus() },
                            onLanguageToggle = {
                                viewModel.toggleLanguage(onLanguageSelected = onApplyLocale)
                            },
                            onNotificationClick = {
                                navigateToDestination(DriveDestination.Notifications)
                            }
                        )
                    } else {
                        DriveSecondaryTopBar(
                            title = stringResource(currentDestination.titleRes),
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    if (uiState.dataOrigin == DataOrigin.DEMO) {
                        Text(
                            text = stringResource(R.string.demo_mode_banner),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DriveOnYellowContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DriveYellowContainer)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            bottomBar = {
                if (currentDestination.isPrimaryTab) {
                    DriveBottomNavigation(
                        currentRoute = currentRoute,
                        onNavigate = { destination ->
                            navigateToDestination(destination)
                        }
                    )
                }
            }
        ) { innerPadding ->
            DriveNavHost(
                navController = navController,
                appContainer = appContainer,
                onboardingViewModel = onboardingViewModel,
                partnerStatus = uiState.partnerStatus,
                partnerProfile = uiState.partnerProfile,
                currentLanguage = uiState.preferredLanguage,
                currentTheme = uiState.themePreference,
                onStatusToggle = { viewModel.toggleDutyStatus() },
                onServiceTypeChange = { viewModel.switchServiceType(it) },
                onLanguageToggle = {
                    viewModel.toggleLanguage(onLanguageSelected = onApplyLocale)
                },
                onSelectLanguage = { lang ->
                    viewModel.setLanguage(lang, onLanguageSelected = onApplyLocale)
                },
                onSelectTheme = { theme ->
                    viewModel.setThemePreference(theme)
                },
                dataOrigin = uiState.dataOrigin,
                boundaryMessage = uiState.boundaryMessage,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
