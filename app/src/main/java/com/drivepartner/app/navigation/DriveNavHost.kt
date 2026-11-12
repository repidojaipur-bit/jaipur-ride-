package com.drivepartner.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.ui.screens.designsystem.DesignSystemGalleryScreen
import com.drivepartner.app.ui.screens.earnings.EarningsRoute
import com.drivepartner.app.ui.screens.home.HomeRoute
import com.drivepartner.app.ui.screens.notifications.NotificationsRoute
import com.drivepartner.app.ui.screens.onboarding.DocumentsKycScreen
import com.drivepartner.app.ui.screens.onboarding.VehicleDetailsScreen
import com.drivepartner.app.ui.screens.orders.OrdersRoute
import com.drivepartner.app.ui.screens.profile.ProfileScreen
import com.drivepartner.app.ui.screens.rewards.RewardsRoute
import com.drivepartner.app.ui.screens.safety.SafetyCenterRoute
import com.drivepartner.app.ui.screens.settings.SettingsLegalScreen
import com.drivepartner.app.ui.screens.support.SupportRoute
import com.drivepartner.app.ui.viewmodel.EarningsViewModel
import com.drivepartner.app.ui.viewmodel.HomeMetricsViewModel
import com.drivepartner.app.ui.viewmodel.HomeViewModel
import com.drivepartner.app.ui.viewmodel.NotificationsViewModel
import com.drivepartner.app.ui.viewmodel.OnboardingAuthViewModel
import com.drivepartner.app.ui.viewmodel.OrdersViewModel
import com.drivepartner.app.ui.viewmodel.RewardsViewModel
import com.drivepartner.app.ui.viewmodel.SafetyViewModel
import com.drivepartner.app.ui.viewmodel.SupportViewModel

@Composable
fun DriveNavHost(
    navController: NavHostController,
    appContainer: AppContainer,
    onboardingViewModel: OnboardingAuthViewModel,
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    currentLanguage: AppLanguage,
    currentTheme: ThemePreference,
    onStatusToggle: () -> Unit,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
    dataOrigin: DataOrigin = DataOrigin.LIVE,
    boundaryMessage: String? = null
) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.provideFactory(appContainer))
    val homeMetricsViewModel: HomeMetricsViewModel = viewModel(
        factory = HomeMetricsViewModel.provideFactory(appContainer)
    )
    val ordersViewModel: OrdersViewModel = viewModel(factory = OrdersViewModel.provideFactory(appContainer))
    val earningsViewModel: EarningsViewModel = viewModel(factory = EarningsViewModel.provideFactory(appContainer))
    val rewardsViewModel: RewardsViewModel = viewModel(factory = RewardsViewModel.provideFactory(appContainer))
    val safetyViewModel: SafetyViewModel = viewModel(factory = SafetyViewModel.provideFactory(appContainer))
    val supportViewModel: SupportViewModel = viewModel(factory = SupportViewModel.provideFactory(appContainer))
    val notificationsViewModel: NotificationsViewModel = viewModel(
        factory = NotificationsViewModel.provideFactory(appContainer)
    )

    val ordersUiState by ordersViewModel.uiState.collectAsState()
    val onboardingUiState by onboardingViewModel.uiState.collectAsState()
    val hasActiveTrip = ordersUiState.activeTrip != null

    NavHost(
        navController = navController,
        startDestination = DriveDestination.Home.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        composable(DriveDestination.Home.route) {
            val activeIncoming = ordersUiState.activeOffers.firstOrNull()
            val remainingSeconds = activeIncoming?.let {
                ordersUiState.remainingSecondsByOfferId[it.offerId] ?: 0
            } ?: 0
            HomeRoute(
                viewModel = homeViewModel,
                metricsViewModel = homeMetricsViewModel,
                activeTrip = ordersUiState.activeTrip,
                incomingOffer = activeIncoming,
                incomingOfferRemainingSeconds = remainingSeconds,
                onOpenOrders = {
                    navController.navigate(DriveDestination.Orders.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenSupport = {
                    navController.navigate(DriveDestination.Support.route) {
                        launchSingleTop = true
                    }
                },
                onOpenRewards = {
                    navController.navigate(DriveDestination.Rewards.route) {
                        launchSingleTop = true
                    }
                },
                onOpenSafety = {
                    navController.navigate(DriveDestination.EmergencySos.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(DriveDestination.Orders.route) {
            OrdersRoute(
                viewModel = ordersViewModel,
                onGoOnlineClick = onStatusToggle
            )
        }
        composable(DriveDestination.Earnings.route) {
            EarningsRoute(viewModel = earningsViewModel)
        }
        composable(DriveDestination.Rewards.route) {
            RewardsRoute(viewModel = rewardsViewModel)
        }
        composable(DriveDestination.Profile.route) {
            ProfileScreen(
                partnerProfile = partnerProfile,
                onServiceTypeChange = onServiceTypeChange,
                onLanguageToggle = onLanguageToggle,
                onNavigateSecondary = { destination ->
                    navController.navigate(destination.route) {
                        launchSingleTop = true
                    }
                },
                dataOrigin = dataOrigin
            )
        }

        // Phase 2 & Phase 7 Secondary Operational Destinations
        composable(DriveDestination.Notifications.route) {
            NotificationsRoute(
                viewModel = notificationsViewModel,
                onNavigateDeepLink = { dest ->
                    if (dest.isPrimaryTab) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else {
                        navController.navigate(dest.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        composable(DriveDestination.VehicleDetails.route) {
            VehicleDetailsScreen(
                uiState = onboardingUiState,
                onSaveVehicle = { serviceType, reg, brand, model, color, year ->
                    onboardingViewModel.saveVehicleDetails(
                        serviceType = serviceType,
                        registrationNumber = reg,
                        brand = brand,
                        model = model,
                        color = color,
                        yearText = year,
                        advanceStep = false
                    )
                },
                onNavigateDocuments = {
                    navController.navigate(DriveDestination.Documents.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(DriveDestination.Documents.route) {
            DocumentsKycScreen(
                uiState = onboardingUiState,
                onUploadDocument = { docType, mimeType, sizeBytes, maskedRef, expires ->
                    onboardingViewModel.uploadAndRegisterDocument(
                        documentType = docType,
                        mimeType = mimeType,
                        sizeBytes = sizeBytes,
                        maskedReference = maskedRef,
                        expiresOnUtc = expires
                    )
                },
                onSimulateDemoReview = { approveAll, rejectType ->
                    onboardingViewModel.simulateDemoReviewerDecision(
                        approveAll = approveAll,
                        rejectType = rejectType
                    )
                }
            )
        }
        composable(DriveDestination.Support.route) {
            SupportRoute(viewModel = supportViewModel)
        }
        composable(DriveDestination.EmergencySos.route) {
            SafetyCenterRoute(
                viewModel = safetyViewModel,
                activeTripId = ordersUiState.activeTrip?.tripId
            )
        }
        composable(DriveDestination.DesignSystem.route) {
            DesignSystemGalleryScreen()
        }
        composable(DriveDestination.Settings.route) {
            SettingsLegalScreen(
                dataOrigin = dataOrigin,
                currentLanguage = currentLanguage,
                currentTheme = currentTheme,
                hasActiveTrip = hasActiveTrip,
                authErrorMessage = onboardingUiState.errorMessage,
                onSelectLanguage = onSelectLanguage,
                onSelectTheme = onSelectTheme,
                onOpenOnboardingFlow = {
                    onboardingViewModel.startOnboardingPreview()
                },
                onLogout = {
                    onboardingViewModel.logout(hasActiveTrip = hasActiveTrip)
                }
            )
        }
    }
}
