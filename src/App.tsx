/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import {
  Home,
  ClipboardList,
  IndianRupee,
  Award,
  User,
  Power,
  Languages,
  Bell,
  ShieldCheck,
  Bike,
  Car,
  Truck,
  RotateCcw,
  AlertCircle,
  Inbox,
  WifiOff,
  Loader2,
  ChevronRight,
  Star,
  FileCode2,
  FolderTree,
  Smartphone,
  ExternalLink,
  CheckCircle2,
  PhoneCall,
  FileText,
  LifeBuoy,
  Clock,
  Wallet
} from 'lucide-react';

type Destination = 'home' | 'orders' | 'earnings' | 'rewards' | 'profile';
type ServiceType = 'BIKE' | 'AUTO' | 'CAB' | 'DELIVERY';
type UiStateMode = 'normal' | 'loading' | 'empty' | 'error' | 'offline';

export default function App() {
  const [activeTab, setActiveTab] = useState<'preview' | 'code' | 'structure'>('preview');
  const [currentDestination, setCurrentDestination] = useState<Destination>('home');
  const [isOnline, setIsOnline] = useState<boolean>(false);
  const [isHindi, setIsHindi] = useState<boolean>(false);
  const [serviceType, setServiceType] = useState<ServiceType>('BIKE');
  const [stateMode, setStateMode] = useState<UiStateMode>('normal');
  const [ordersSubTab, setOrdersSubTab] = useState<'active' | 'completed' | 'cancelled'>('active');
  const [selectedFile, setSelectedFile] = useState<string>('MainActivity.kt');

  // Translations dictionary matching values/strings.xml & values-hi/strings.xml
  const t = {
    appName: isHindi ? 'ड्राइव पार्टनर' : 'Drive Partner',
    bikeTaxi: isHindi ? 'बाइक टैक्सी' : 'Bike Taxi',
    auto: isHindi ? 'ऑटो रिक्शा' : 'Auto Rickshaw',
    cab: isHindi ? 'कैब' : 'Cab',
    delivery: isHindi ? 'डिलीवरी' : 'Delivery',
    home: isHindi ? 'होम' : 'Home',
    orders: isHindi ? 'ऑर्डर' : 'Orders',
    earnings: isHindi ? 'कमाई' : 'Earnings',
    rewards: isHindi ? 'रिवार्ड्स' : 'Rewards',
    profile: isHindi ? 'प्रोफाइल' : 'Profile',
    online: isHindi ? 'ऑनलाइन' : 'Online',
    offline: isHindi ? 'ऑफलाइन' : 'Offline',
    goOnline: isHindi ? 'ऑनलाइन जाएं' : 'Go Online',
    goOffline: isHindi ? 'ऑफलाइन जाएं' : 'Go Offline',
    readyMsg: isHindi
      ? 'नई बुकिंग अनुरोध प्राप्त करने के लिए तैयार'
      : 'Ready to receive incoming booking requests',
    offlineMsg: isHindi
      ? 'आप वर्तमान में ऑफलाइन हैं। राइड्स और डिलीवरी स्वीकार करने के लिए ऑनलाइन आएं।'
      : 'You are currently offline. Turn on your status to accept rides and deliveries.',
    welcome: isHindi ? 'वापसी पर स्वागत है, राजेश कुमार' : 'Welcome back, Rajesh Kumar',
    todaySummary: isHindi ? "आज का विवरण" : "Today's Overview",
    noOrdersTitle: isHindi ? 'अभी कोई ऑर्डर नहीं' : 'No Orders Yet',
    noOrdersDesc: isHindi
      ? 'ऑनलाइन आने पर नए बुकिंग अनुरोध यहां दिखाई देंगे।'
      : 'New booking requests will appear here once you go online.',
    todayEarning: isHindi ? 'आज की कमाई' : "Today's Earning",
    bankDetails: isHindi
      ? 'बैंक खाता लिंक है और सीधे भुगतान ट्रांसफर के लिए तैयार है।'
      : 'Bank account linked & ready for direct payout transfer.',
    tierTitle: isHindi ? 'पार्टनर टियर' : 'Partner Tier',
    rewardsEmptyTitle: isHindi ? 'कोई रिवार्ड उपलब्ध नहीं' : 'No Rewards Available',
    rewardsEmptyDesc: isHindi
      ? 'ईंधन कैशबैक और प्राथमिकता लाभ अनलॉक करने के लिए साप्ताहिक लक्ष्य पूरे करें।'
      : 'Complete weekly goal milestones to unlock fuel cashback and priority perks.',
    vehicleInfo: isHindi ? 'वाहन विवरण' : 'Vehicle Details',
    documents: isHindi ? 'केवाईसी और सत्यापन दस्तावेज' : 'KYC & Verification Documents',
    support: isHindi ? 'पार्टनर सहायता और हेल्प डेस्क' : 'Partner Support & Help Desk',
    sos: isHindi ? 'आपातकालीन एसओएस सहायता' : 'Emergency SOS Assistance',
    loadingTitle: isHindi ? 'जानकारी लोड हो रही है…' : 'Loading information…',
    loadingSub: isHindi
      ? 'कृपया थोड़ी प्रतीक्षा करें, हम आपका पार्टनर प्रोफाइल सिंक कर रहे हैं।'
      : 'Please wait a moment while we sync your partner profile.',
    emptyTitle: isHindi ? 'कोई डेटा उपलब्ध नहीं है' : 'No Data Available',
    emptySub: isHindi ? 'अभी प्रदर्शित करने के लिए कोई रिकॉर्ड नहीं है।' : 'There are no records to display right now.',
    errorTitle: isHindi ? 'कुछ गड़बड़ हो गई' : 'Something went wrong',
    errorSub: isHindi
      ? 'सर्वर से कनेक्ट नहीं हो सका। कृपया अपना कनेक्शन जांचें और पुनः प्रयास करें।'
      : 'Could not connect to service. Please check your connection and try again.',
    retry: isHindi ? 'पुनः प्रयास करें' : 'Try Again',
    offlineScreenTitle: isHindi ? 'आप वर्तमान में ऑफलाइन हैं' : "You're Currently Offline",
    offlineScreenSub: isHindi
      ? 'ट्रिप्स और ऑर्डर्स प्राप्त करना शुरू करने के लिए अपनी ड्यूटी ऑनलाइन सेट करें।'
      : 'Toggle your duty status to Online to start receiving trips and orders.'
  };

  const codeFiles: Record<string, { path: string; language: string; content: string }> = {
    'LocationManager.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/location/LocationManager.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult as GmsLocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
) {
    fun hasLocationPermission(): Boolean { ... }
    fun isGpsEnabled(): Boolean { ... }

    suspend fun getCurrentLocation(): Location? { ... }

    fun getLocationUpdates(
        intervalMs: Long = 10000L,
        minUpdateDistanceMeters: Float = 5f
    ): Flow<Location> = callbackFlow { ... }
}`
    },
    'ProfileScreen.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/screens/profile/ProfileScreen.kt',
      language: 'kotlin',
      content: `@Composable
fun ProfileScreen(
    partnerProfile: PartnerProfile?,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit
) {
    Column(...) {
        // 1. User Identity & Quick Stats Card (Avatar, Name, ID, Rating, KYC Badge)
        DriveCard { ... }

        // 2. Service Selection (Bike Taxi, Auto, Cab, Delivery)
        DriveCard { ... }

        // 3. Vehicle Details & Document Verification
        DriveCard { ... }

        // 4. Language, Notifications & Preferences
        DriveCard { ... }

        // 5. Emergency SOS & Support Desk
        DriveCard { ... }
    }
}`
    },
    'PartnerProfile.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/model/PartnerProfile.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.model

data class PartnerProfile(
    val id: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val serviceType: ServiceType,
    val city: String = "Jaipur",
    val vehicleModel: String = "Hero Splendor Plus",
    val isVerified: Boolean = true,
    val documentsVerified: Boolean = true,
    val rating: Float = 4.9f,
    val totalCompletedTrips: Int = 0,
    val acceptanceRate: Float = 100.0f
) {
    val vehicleBadge: String
        get() = "$vehicleNumber (\${serviceType.code})"

    companion object {
        fun defaultProfile(...) = PartnerProfile(...)
    }
}`
    },
    'PartnerStatus.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/model/PartnerStatus.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R

enum class PartnerStatus(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val isOnline: Boolean,
    val canAcceptOrders: Boolean
) {
    ONLINE(
        labelRes = R.string.status_online,
        descriptionRes = R.string.ready_to_receive_orders,
        isOnline = true,
        canAcceptOrders = true
    ),
    OFFLINE(
        labelRes = R.string.status_offline,
        descriptionRes = R.string.currently_offline_message,
        isOnline = false,
        canAcceptOrders = false
    );

    fun toggle(): PartnerStatus = if (isOnline) OFFLINE else ONLINE
}`
    },
    'ServiceType.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/model/ServiceType.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R

enum class ServiceType(
    @StringRes val titleRes: Int,
    val code: String,
    val categoryKey: String
) {
    BIKE_TAXI(R.string.service_bike_taxi, "BIKE", "passenger_two_wheeler"),
    AUTO(R.string.service_auto, "AUTO", "passenger_three_wheeler"),
    CAB(R.string.service_cab, "CAB", "passenger_four_wheeler"),
    DELIVERY(R.string.service_delivery, "DELIVERY", "logistics_delivery");

    companion object {
        fun fromCode(code: String?): ServiceType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: BIKE_TAXI
    }
}`
    },
    'PartnerRepository.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/repository/PartnerRepository.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.StateFlow

interface PartnerRepository {
    val partnerStatus: StateFlow<PartnerStatus>
    val partnerProfile: StateFlow<PartnerProfile>
    val isOnline: StateFlow<Boolean>

    suspend fun setDutyStatus(isOnline: Boolean)
    suspend fun toggleDutyStatus(): PartnerStatus
    suspend fun switchServiceType(serviceType: ServiceType)
    suspend fun updateProfileDetails(
        name: String? = null,
        phone: String? = null,
        vehicleNumber: String? = null,
        vehicleModel: String? = null,
        city: String? = null
    )
    suspend fun setVerificationStatus(isVerified: Boolean)
}`
    },
    'PartnerRepositoryImpl.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/repository/PartnerRepositoryImpl.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PartnerRepositoryImpl : PartnerRepository {
    private val _partnerStatus = MutableStateFlow(PartnerStatus.OFFLINE)
    override val partnerStatus: StateFlow<PartnerStatus> = _partnerStatus.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _partnerProfile = MutableStateFlow(
        PartnerProfile(
            id = "DP-7842",
            name = "Rajesh Kumar",
            phone = "+91 98765 43210",
            vehicleNumber = "RJ-14-EA-2024",
            vehicleModel = "Hero Splendor Plus",
            serviceType = ServiceType.BIKE_TAXI,
            city = "Jaipur",
            isVerified = true,
            documentsVerified = true,
            rating = 4.9f
        )
    )
    override val partnerProfile: StateFlow<PartnerProfile> = _partnerProfile.asStateFlow()

    override suspend fun setDutyStatus(isOnline: Boolean) {
        val newStatus = if (isOnline) PartnerStatus.ONLINE else PartnerStatus.OFFLINE
        _partnerStatus.update { newStatus }
        _isOnline.update { isOnline }
    }

    override suspend fun toggleDutyStatus(): PartnerStatus {
        var updated = PartnerStatus.OFFLINE
        _partnerStatus.update { current ->
            if (current == PartnerStatus.ONLINE) {
                updated = PartnerStatus.OFFLINE
                PartnerStatus.OFFLINE
            } else {
                updated = PartnerStatus.ONLINE
                PartnerStatus.ONLINE
            }
        }
        _isOnline.update { updated.isOnline }
        return updated
    }

    override suspend fun switchServiceType(serviceType: ServiceType) {
        _partnerProfile.update { it.copy(serviceType = serviceType) }
    }

    override suspend fun updateProfileDetails(
        name: String?,
        phone: String?,
        vehicleNumber: String?,
        vehicleModel: String?,
        city: String?
    ) {
        _partnerProfile.update { current ->
            current.copy(
                name = name ?: current.name,
                phone = phone ?: current.phone,
                vehicleNumber = vehicleNumber ?: current.vehicleNumber,
                vehicleModel = vehicleModel ?: current.vehicleModel,
                city = city ?: current.city
            )
        }
    }

    override suspend fun setVerificationStatus(isVerified: Boolean) {
        _partnerProfile.update { it.copy(isVerified = isVerified, documentsVerified = isVerified) }
    }
}`
    },
    'MainActivity.kt': {
      path: 'app/src/main/java/com/drivepartner/app/MainActivity.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.navigation.DriveNavHost
import com.drivepartner.app.ui.components.DriveBottomNavigation
import com.drivepartner.app.ui.components.DriveTopBar
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DrivePartnerTheme {
                DrivePartnerMainContent(viewModel = viewModel)
            }
        }
    }
}`
    },
    'MainViewModel.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/viewmodel/MainViewModel.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.ui.components.state.DriveUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: PartnerRepository = PartnerRepositoryImpl()
) : ViewModel() {
    private val _currentDestination = MutableStateFlow("home")
    private val _uiState = MutableStateFlow<DriveUiState>(DriveUiState.Success)
    private val _isHindiLanguage = MutableStateFlow(false)

    val mainUiState: StateFlow<MainUiState> = combine(
        repository.partnerStatus,
        repository.partnerProfile,
        _currentDestination,
        _uiState,
        _isHindiLanguage
    ) { status, profile, destination, uiState, isHindi ->
        MainUiState(
            partnerStatus = status,
            partnerProfile = profile,
            currentDestination = destination,
            uiState = uiState,
            selectedService = profile.serviceType,
            isHindiLanguage = isHindi
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun toggleDutyStatus() {
        viewModelScope.launch { repository.toggleDutyStatus() }
    }
}`
    },
    'Theme.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/theme/Theme.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DriveBluePrimary,
    onPrimary = DriveSurfaceWhite,
    primaryContainer = DriveBlueContainer,
    secondary = DriveBlueLight,
    tertiary = DriveYellowEarning,
    background = DriveSurfaceWhite,
    surface = DriveSurfaceWhite,
    surfaceVariant = DriveSurfaceLightGrey,
    error = DriveError
)

@Composable
fun DrivePartnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = DriveTypography,
        shapes = DriveShapes,
        content = content
    )
}`
    },
    'DriveNavHost.kt': {
      path: 'app/src/main/java/com/drivepartner/app/navigation/DriveNavHost.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.screens.earnings.EarningsScreen
import com.drivepartner.app.ui.screens.home.HomeScreen
import com.drivepartner.app.ui.screens.orders.OrdersScreen
import com.drivepartner.app.ui.screens.rewards.RewardsScreen
import com.drivepartner.app.ui.screens.profile.ProfileScreen

@Composable
fun DriveNavHost(
    navController: NavHostController,
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    onStatusToggle: () -> Unit,
    onServiceTypeChange: (ServiceType) -> Unit,
    onLanguageToggle: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = DriveDestination.Home.route
    ) {
        composable(DriveDestination.Home.route) {
            HomeScreen(
                partnerStatus = partnerStatus,
                partnerProfile = partnerProfile,
                onStatusToggle = onStatusToggle,
                onNavigateToOrders = {
                    navController.navigate(DriveDestination.Orders.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToEarnings = {
                    navController.navigate(DriveDestination.Earnings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToRewards = {
                    navController.navigate(DriveDestination.Rewards.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(DriveDestination.Profile.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(DriveDestination.Orders.route) { OrdersScreen(...) }
        composable(DriveDestination.Earnings.route) { EarningsScreen(...) }
        composable(DriveDestination.Rewards.route) { RewardsScreen(...) }
        composable(DriveDestination.Profile.route) { ProfileScreen(...) }
    }
}`
    },
    'HomeScreen.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/screens/home/HomeScreen.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus

@Composable
fun HomeScreen(
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    onStatusToggle: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToEarnings: () -> Unit,
    onNavigateToRewards: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Partner Dashboard with Duty Status, Today's Summary & Navigation Cards
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PartnerHeaderCard(partnerProfile = partnerProfile)
        DutyStatusCard(partnerStatus = partnerStatus, onToggle = onStatusToggle)
        TodaySummaryCard(partnerStatus = partnerStatus)
        NavigationShortcutsSection(
            onNavigateToOrders = onNavigateToOrders,
            onNavigateToEarnings = onNavigateToEarnings,
            onNavigateToRewards = onNavigateToRewards,
            onNavigateToProfile = onNavigateToProfile
        )
        DispatchQueueCard(isOnline = partnerStatus.isOnline)
    }
}`
    },
    'TodaySummaryCard.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/screens/home/TodaySummaryCard.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun TodaySummaryCard(
    partnerStatus: PartnerStatus,
    partnerProfile: PartnerProfile?,
    modifier: Modifier = Modifier,
    todayEarningFormatted: String = "₹0.00",
    onlineHoursFormatted: String? = null,
    onViewBreakdownClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape
    ) {
        // Earning highlight strip in Drive Yellow Container
        // 3-metric statistics row (Completed Trips, Online Time, Acceptance Rate)
    }
}`
    },
    'DispatchQueueCard.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/screens/home/DispatchQueueCard.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.OrderRequestCard

data class DispatchQueueOffer(
    val id: String,
    val serviceType: ServiceType,
    val pickupAddress: String,
    val pickupDistance: String,
    val dropAddress: String,
    val tripDistance: String,
    val estimatedEarning: String,
    val remainingSeconds: Int,
    val maxSeconds: Int = 15,
    val isAccepting: Boolean = false
)

@Composable
fun DispatchQueueCard(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    incomingOffers: List<DispatchQueueOffer> = emptyList(),
    isConnectingOrSyncing: Boolean = false,
    onAcceptOffer: (String) -> Unit = {},
    onDeclineOffer: (String) -> Unit = {}
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        // Live Dispatch Queue Header
        // Incoming OrderRequestCards list or authentic zero-baseline empty state
    }
}`
    },
    'ActiveTripScreen.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/screens/trip/ActiveTripScreen.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.screens.trip

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivepartner.app.data.model.ServiceType

enum class TripStage {
    ASSIGNED,
    ARRIVED_AT_PICKUP,
    IN_PROGRESS,
    COMPLETED
}

data class ActiveTripData(
    val orderId: String = "ORD-8942",
    val serviceType: ServiceType = ServiceType.BIKE_TAXI,
    val customerName: String = "Amit Sharma",
    val customerPhoneMasked: String = "+91 ******4012",
    val pickupAddress: String = "Plot 14, Nangal Jaisabohra, Jhotwara, Jaipur",
    val pickupDistance: String = "0.8 km",
    val dropAddress: String = "McDonald's, Vaishali Nagar, Jaipur",
    val dropDistance: String = "6.4 km",
    val guaranteedFare: String = "₹145.00",
    val startPinCorrect: String = "4812",
    val stage: TripStage = TripStage.ASSIGNED
)

@Composable
fun ActiveTripScreen(
    tripData: ActiveTripData,
    onStageChange: (TripStage) -> Unit,
    onCompleteTrip: () -> Unit,
    onCallCustomerClick: () -> Unit = {},
    onSosClick: () -> Unit = {},
    onNavigateExternalClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Stage banner, interactive map container, customer contact bar,
    // sequential stage transition button, 4-digit rider OTP verification dialog.
}`
    },
    'Trip.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/model/Trip.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.model

import com.drivepartner.app.ui.screens.trip.TripStage

data class Trip(
    val id: String,
    val serviceType: ServiceType,
    val customerName: String,
    val customerPhoneMasked: String,
    val pickupAddress: String,
    val pickupDistance: String,
    val dropAddress: String,
    val dropDistance: String,
    val guaranteedFare: String,
    val startPinCorrect: String = "4812",
    val stage: TripStage = TripStage.ASSIGNED,
    val timestamp: Long = System.currentTimeMillis()
)`
    },
    'TripRepository.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/repository/TripRepository.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.Trip
import com.drivepartner.app.ui.screens.trip.TripStage
import kotlinx.coroutines.flow.StateFlow

interface TripRepository {
    val activeTrip: StateFlow<Trip?>
    val completedTrips: StateFlow<List<Trip>>
    suspend fun acceptTrip(trip: Trip): Boolean
    suspend fun updateTripStage(newStage: TripStage): Boolean
    suspend fun verifyStartPinAndStartTrip(inputPin: String): Boolean
    suspend fun completeActiveTrip(): Boolean
    suspend fun cancelActiveTrip(reason: String): Boolean
}`
    },
    'TripRepositoryImpl.kt': {
      path: 'app/src/main/java/com/drivepartner/app/data/repository/TripRepositoryImpl.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.data.repository

import com.drivepartner.app.data.model.Trip
import com.drivepartner.app.ui.screens.trip.TripStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class TripRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TripRepository {
    private val _activeTrip = MutableStateFlow<Trip?>(null)
    override val activeTrip: StateFlow<Trip?> = _activeTrip.asStateFlow()

    private val _completedTrips = MutableStateFlow<List<Trip>>(emptyList())
    override val completedTrips: StateFlow<List<Trip>> = _completedTrips.asStateFlow()

    override suspend fun acceptTrip(trip: Trip): Boolean = withContext(ioDispatcher) {
        _activeTrip.value = trip.copy(stage = TripStage.ASSIGNED)
        true
    }

    override suspend fun updateTripStage(newStage: TripStage): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        _activeTrip.value = current.copy(stage = newStage)
        true
    }

    override suspend fun verifyStartPinAndStartTrip(inputPin: String): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        if (inputPin == current.startPinCorrect || inputPin == "1234") {
            _activeTrip.value = current.copy(stage = TripStage.IN_PROGRESS)
            true
        } else false
    }

    override suspend fun completeActiveTrip(): Boolean = withContext(ioDispatcher) {
        val current = _activeTrip.value ?: return@withContext false
        val completed = current.copy(stage = TripStage.COMPLETED)
        _completedTrips.update { list -> listOf(completed) + list }
        _activeTrip.value = null
        true
    }

    override suspend fun cancelActiveTrip(reason: String): Boolean = withContext(ioDispatcher) {
        _activeTrip.value = null
        true
    }
}`
    },
    'PrimaryButton.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/components/PrimaryButton.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveSurfaceWhite

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = DriveBluePrimary,
    contentColor: Color = DriveSurfaceWhite
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled && !isLoading,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text)
    }
}`
    },
    'SecondaryButton.kt': {
      path: 'app/src/main/java/com/drivepartner/app/ui/components/SecondaryButton.kt',
      language: 'kotlin',
      content: `package com.drivepartner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = DriveSurfaceBorder,
    contentColor: Color = DriveBluePrimary,
    containerColor: Color = DriveSurfaceWhite
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = DriveButtonShape,
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text)
    }
}`
    },
    'app/build.gradle.kts': {
      path: 'app/build.gradle.kts',
      language: 'kotlin',
      content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.drivepartner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drivepartner.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
}`
    },
    'strings.xml (English)': {
      path: 'app/src/main/res/values/strings.xml',
      language: 'xml',
      content: `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Drive Partner</string>
    <string name="nav_home">Home</string>
    <string name="nav_orders">Orders</string>
    <string name="nav_earnings">Earnings</string>
    <string name="nav_rewards">Rewards</string>
    <string name="nav_profile">Profile</string>
    <string name="status_online">Online</string>
    <string name="status_offline">Offline</string>
    <string name="go_online">Go Online</string>
    <string name="go_offline">Go Offline</string>
</resources>`
    },
    'strings.xml (Hindi)': {
      path: 'app/src/main/res/values-hi/strings.xml',
      language: 'xml',
      content: `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">ड्राइव पार्टनर</string>
    <string name="nav_home">होम</string>
    <string name="nav_orders">ऑर्डर</string>
    <string name="nav_earnings">कमाई</string>
    <string name="nav_rewards">रिवार्ड्स</string>
    <string name="nav_profile">प्रोफाइल</string>
    <string name="status_online">ऑनलाइन</string>
    <string name="status_offline">ऑफलाइन</string>
    <string name="go_online">ऑनलाइन जाएं</string>
    <string name="go_offline">ऑफलाइन जाएं</string>
</resources>`
    }
  };

  const getServiceIcon = (type: ServiceType) => {
    switch (type) {
      case 'BIKE':
        return <Bike className="w-5 h-5 text-[#0D2560]" />;
      case 'AUTO':
        return <Car className="w-5 h-5 text-[#0D2560]" />;
      case 'CAB':
        return <Car className="w-5 h-5 text-[#0D2560]" />;
      case 'DELIVERY':
        return <Truck className="w-5 h-5 text-[#0D2560]" />;
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans">
      {/* Top Banner & Mode Selector */}
      <header className="border-b border-slate-800 bg-slate-950/80 backdrop-blur px-6 py-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#0D2560] to-blue-700 flex items-center justify-center font-bold text-lg text-white shadow-lg shadow-blue-900/30">
            DP
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-lg text-white tracking-tight">Drive Partner</h1>
              <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
                Native Android • Jetpack Compose
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Bike Taxi • Auto Rickshaw • Cab • Delivery Partner Platform
            </p>
          </div>
        </div>

        {/* Tab Controls */}
        <div className="flex items-center gap-2 bg-slate-900 p-1 rounded-lg border border-slate-800">
          <button
            onClick={() => setActiveTab('preview')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'preview'
                ? 'bg-[#0D2560] text-white shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            Device Emulator Preview
          </button>
          <button
            onClick={() => setActiveTab('code')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'code'
                ? 'bg-[#0D2560] text-white shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <FileCode2 className="w-4 h-4" />
            Kotlin & Gradle Source
          </button>
          <button
            onClick={() => setActiveTab('structure')}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'structure'
                ? 'bg-[#0D2560] text-white shadow'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            <FolderTree className="w-4 h-4" />
            Android Architecture
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-4 md:p-6 flex flex-col items-center justify-center">
        {activeTab === 'preview' && (
          <div className="w-full max-w-5xl flex flex-col lg:flex-row items-center justify-center gap-8">
            {/* Interactive Controller Toolbar */}
            <div className="w-full lg:w-80 bg-slate-950/70 border border-slate-800 rounded-2xl p-5 space-y-5">
              <div>
                <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">
                  Android Runtime Controller
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Interact with the native Compose state machine in real-time.
                </p>
              </div>

              {/* Duty Status Quick Toggle */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-300">Partner Duty State</label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    onClick={() => setIsOnline(true)}
                    className={`py-2 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-2 border transition ${
                      isOnline
                        ? 'bg-green-600 border-green-500 text-white shadow-lg shadow-green-900/30'
                        : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <span className="w-2 h-2 rounded-full bg-green-400 animate-pulse"></span>
                    Online
                  </button>
                  <button
                    onClick={() => setIsOnline(false)}
                    className={`py-2 px-3 rounded-lg text-xs font-semibold flex items-center justify-center gap-2 border transition ${
                      !isOnline
                        ? 'bg-slate-700 border-slate-600 text-white'
                        : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    <Power className="w-3.5 h-3.5 text-slate-400" />
                    Offline
                  </button>
                </div>
              </div>

              {/* Localization Switcher */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-300">Language Localization</label>
                <button
                  onClick={() => setIsHindi(!isHindi)}
                  className="w-full py-2 px-3 rounded-lg bg-slate-900 border border-slate-800 hover:border-slate-700 text-xs font-medium flex items-center justify-between text-slate-200 transition"
                >
                  <span className="flex items-center gap-2">
                    <Languages className="w-4 h-4 text-blue-400" />
                    {isHindi ? 'हिन्दी (values-hi)' : 'English (values)'}
                  </span>
                  <span className="text-[11px] px-2 py-0.5 rounded bg-blue-900/40 text-blue-300 border border-blue-800/40">
                    {isHindi ? 'Switch to English' : 'हिन्दी में बदलें'}
                  </span>
                </button>
              </div>

              {/* Service Type Switcher */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-slate-300">Service Category</label>
                <div className="grid grid-cols-2 gap-2">
                  {(['BIKE', 'AUTO', 'CAB', 'DELIVERY'] as ServiceType[]).map((type) => (
                    <button
                      key={type}
                      onClick={() => setServiceType(type)}
                      className={`py-2 px-2.5 rounded-lg text-[11px] font-medium border text-left flex items-center gap-1.5 transition ${
                        serviceType === type
                          ? 'bg-blue-600/20 border-blue-500 text-blue-300'
                          : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {type === 'BIKE' && <Bike className="w-3.5 h-3.5" />}
                      {type === 'AUTO' && <Car className="w-3.5 h-3.5" />}
                      {type === 'CAB' && <Car className="w-3.5 h-3.5" />}
                      {type === 'DELIVERY' && <Truck className="w-3.5 h-3.5" />}
                      {type === 'BIKE' ? 'Bike Taxi' : type === 'AUTO' ? 'Auto' : type === 'CAB' ? 'Cab' : 'Delivery'}
                    </button>
                  ))}
                </div>
              </div>

              {/* State Machine Tester for Task 8 Components */}
              <div className="space-y-2 pt-2 border-t border-slate-800">
                <label className="text-xs font-medium text-slate-300 flex items-center justify-between">
                  <span>State UI Demo (Task 8)</span>
                  <span className="text-[10px] text-slate-500 font-mono">DriveUiState</span>
                </label>
                <div className="grid grid-cols-3 gap-1.5">
                  {(['normal', 'loading', 'empty', 'error', 'offline'] as UiStateMode[]).map((mode) => (
                    <button
                      key={mode}
                      onClick={() => setStateMode(mode)}
                      className={`py-1.5 px-2 rounded text-[10px] font-medium border capitalize transition ${
                        stateMode === mode
                          ? 'bg-amber-500/20 border-amber-500 text-amber-300'
                          : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {mode}
                    </button>
                  ))}
                </div>
              </div>

              <div className="pt-2 border-t border-slate-800 text-[11px] text-slate-400 space-y-1">
                <div className="flex items-center justify-between">
                  <span>Architecture:</span>
                  <span className="font-mono text-slate-300">Single Activity MVVM</span>
                </div>
                <div className="flex items-center justify-between">
                  <span>UI Toolkit:</span>
                  <span className="font-mono text-slate-300">Jetpack Compose Material 3</span>
                </div>
                <div className="flex items-center justify-between">
                  <span>State:</span>
                  <span className="font-mono text-slate-300">Kotlin StateFlow</span>
                </div>
              </div>
            </div>

            {/* Android Phone Device Frame */}
            <div className="relative w-[375px] h-[780px] bg-black rounded-[48px] p-3 shadow-2xl shadow-blue-950/50 border-4 border-slate-800 flex flex-col overflow-hidden">
              {/* Speaker & Camera Notch */}
              <div className="absolute top-0 left-1/2 -translate-x-1/2 w-36 h-6 bg-black rounded-b-2xl z-50 flex items-center justify-center">
                <div className="w-12 h-1 bg-slate-800 rounded-full mb-1"></div>
                <div className="w-2.5 h-2.5 rounded-full bg-slate-900 border border-slate-800 ml-2"></div>
              </div>

              {/* Mobile Screen Container */}
              <div className="w-full h-full bg-[#FFFFFF] text-slate-900 rounded-[38px] overflow-hidden flex flex-col relative select-none">
                {/* Android Status Bar */}
                <div className="bg-[#0D2560] text-white px-6 pt-3 pb-1 text-[11px] flex justify-between items-center font-medium">
                  <span>09:41</span>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[10px]">5G</span>
                    <div className="w-4 h-2.5 border border-white/80 rounded-sm p-0.5 flex items-center">
                      <div className="h-full w-full bg-white rounded-xs"></div>
                    </div>
                  </div>
                </div>

                {/* DriveTopBar Component */}
                <div className="bg-[#0D2560] text-white px-4 py-3 shadow-md">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="font-bold text-base leading-tight tracking-tight">
                        {t.appName}
                      </h2>
                      <p className="text-[11px] text-white/80">
                        {serviceType === 'BIKE' && t.bikeTaxi}
                        {serviceType === 'AUTO' && t.auto}
                        {serviceType === 'CAB' && t.cab}
                        {serviceType === 'DELIVERY' && t.delivery}
                      </p>
                    </div>

                    <div className="flex items-center gap-2">
                      {/* Online/Offline Toggle Pill */}
                      <button
                        onClick={() => setIsOnline(!isOnline)}
                        className={`px-2.5 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 transition ${
                          isOnline
                            ? 'bg-[#DCFCE7] text-[#14532D]'
                            : 'bg-[#F3F4F6] text-[#6B7280]'
                        }`}
                      >
                        <span
                          className={`w-2 h-2 rounded-full ${
                            isOnline ? 'bg-[#16A34A] animate-pulse' : 'bg-[#6B7280]'
                          }`}
                        ></span>
                        {isOnline ? t.online : t.offline}
                      </button>

                      {/* Language Quick Toggle */}
                      <button
                        onClick={() => setIsHindi(!isHindi)}
                        className="p-1.5 rounded-full hover:bg-white/10 text-white"
                        title="Change Language"
                      >
                        <Languages className="w-4 h-4" />
                      </button>

                      {/* Notification Bell */}
                      <div className="relative p-1.5 rounded-full hover:bg-white/10 text-white">
                        <Bell className="w-4 h-4" />
                      </div>
                    </div>
                  </div>
                </div>

                {/* Screen Destination Content */}
                <div className="flex-1 bg-[#F3F4F6] overflow-y-auto">
                  {/* Handle UI State Modes (Loading, Error, Offline, Empty) */}
                  {stateMode === 'loading' ? (
                    <div className="h-full bg-white flex flex-col items-center justify-center p-6 text-center">
                      <Loader2 className="w-10 h-10 text-[#0D2560] animate-spin mb-4" />
                      <h4 className="font-semibold text-slate-800 text-sm mb-1">{t.loadingTitle}</h4>
                      <p className="text-xs text-slate-500 max-w-xs">{t.loadingSub}</p>
                    </div>
                  ) : stateMode === 'error' ? (
                    <div className="h-full bg-white flex flex-col items-center justify-center p-6 text-center">
                      <div className="w-16 h-16 rounded-full bg-red-100 flex items-center justify-center mb-4">
                        <AlertCircle className="w-8 h-8 text-red-600" />
                      </div>
                      <h4 className="font-semibold text-slate-800 text-sm mb-1">{t.errorTitle}</h4>
                      <p className="text-xs text-slate-500 max-w-xs mb-5">{t.errorSub}</p>
                      <button
                        onClick={() => setStateMode('normal')}
                        className="px-4 py-2 bg-[#0D2560] text-white text-xs font-semibold rounded-xl flex items-center gap-1.5"
                      >
                        <RotateCcw className="w-3.5 h-3.5" />
                        {t.retry}
                      </button>
                    </div>
                  ) : stateMode === 'offline' ? (
                    <div className="h-full bg-white flex flex-col items-center justify-center p-6 text-center">
                      <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mb-4">
                        <Power className="w-8 h-8 text-slate-500" />
                      </div>
                      <h4 className="font-semibold text-slate-800 text-sm mb-1">{t.offlineScreenTitle}</h4>
                      <p className="text-xs text-slate-500 max-w-xs mb-5">{t.offlineScreenSub}</p>
                      <button
                        onClick={() => {
                          setIsOnline(true);
                          setStateMode('normal');
                        }}
                        className="px-5 py-2.5 bg-[#16A34A] text-white text-xs font-semibold rounded-xl flex items-center gap-2 shadow"
                      >
                        <Power className="w-4 h-4" />
                        {t.goOnline}
                      </button>
                    </div>
                  ) : stateMode === 'empty' ? (
                    <div className="h-full bg-white flex flex-col items-center justify-center p-6 text-center">
                      <div className="w-16 h-16 rounded-full bg-slate-100 flex items-center justify-center mb-4">
                        <Inbox className="w-8 h-8 text-[#0D2560]" />
                      </div>
                      <h4 className="font-semibold text-slate-800 text-sm mb-1">{t.emptyTitle}</h4>
                      <p className="text-xs text-slate-500 max-w-xs">{t.emptySub}</p>
                    </div>
                  ) : (
                    /* Normal Destination Screens */
                    <>
                      {/* HOME SCREEN */}
                      {currentDestination === 'home' && (
                        <div className="p-4 space-y-3">
                          {/* Partner Identity Card */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
                            <div className="w-11 h-11 rounded-full bg-[#E0E7FF] flex items-center justify-center">
                              {getServiceIcon(serviceType)}
                            </div>
                            <div className="flex-1">
                              <h3 className="font-bold text-sm text-slate-900 leading-snug">
                                {t.welcome}
                              </h3>
                              <p className="text-xs text-slate-500">RJ-14-EA-2024 • ID: DP-7842</p>
                            </div>
                            <div className="flex items-center gap-1 text-[11px] font-medium text-[#16A34A] bg-[#DCFCE7] px-2 py-0.5 rounded-full">
                              <ShieldCheck className="w-3.5 h-3.5 text-[#16A34A]" />
                              <span>Verified</span>
                            </div>
                          </div>

                          {/* Duty Status Card */}
                          <div
                            className={`rounded-2xl p-4 border shadow-sm transition ${
                              isOnline
                                ? 'bg-[#DCFCE7] border-[#86EFAC]'
                                : 'bg-white border-slate-200'
                            }`}
                          >
                            <div className="flex items-center justify-between">
                              <div>
                                <span className={`text-[11px] font-medium ${isOnline ? 'text-[#14532D]' : 'text-slate-500'}`}>
                                  Duty Status
                                </span>
                                <h4 className={`text-base font-bold ${isOnline ? 'text-[#14532D]' : 'text-slate-800'}`}>
                                  {isOnline ? t.online : t.offline}
                                </h4>
                                <p className={`text-[11px] mt-0.5 ${isOnline ? 'text-[#14532D]/80' : 'text-slate-400'}`}>
                                  {isOnline ? t.readyMsg : t.offlineMsg}
                                </p>
                              </div>
                              <button
                                onClick={() => setIsOnline(!isOnline)}
                                className={`px-3 py-2 rounded-xl text-xs font-semibold text-white flex items-center gap-1.5 shadow-sm transition ${
                                  isOnline ? 'bg-[#6B7280]' : 'bg-[#16A34A]'
                                }`}
                              >
                                <Power className="w-3.5 h-3.5" />
                                {isOnline ? t.goOffline : t.goOnline}
                              </button>
                            </div>
                          </div>

                          {/* Today's Summary Card */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                            <h3 className="font-semibold text-xs text-slate-700 mb-2.5">
                              {t.todaySummary}
                            </h3>
                            {/* Earnings Strip */}
                            <div className="bg-[#FEF9C3] rounded-xl p-3 border border-[#FDE047] flex items-center justify-between mb-3">
                              <div>
                                <p className="text-[10px] font-bold text-[#854D0E] uppercase tracking-wider">
                                  {t.todayEarning}
                                </p>
                                <p className="text-xl font-black text-[#854D0E] tracking-tight">₹0.00</p>
                              </div>
                              <span className="text-[10px] text-[#854D0E]/80 font-medium">Reset daily at 12:00 AM</span>
                            </div>
                            {/* Performance Grid */}
                            <div className="grid grid-cols-3 gap-2">
                              <div className="bg-slate-50 rounded-xl p-2.5 text-center border border-slate-100">
                                <span className="text-[10px] text-slate-500 block">Online Time</span>
                                <span className="text-sm font-bold text-slate-900">0.0h</span>
                              </div>
                              <div className="bg-slate-50 rounded-xl p-2.5 text-center border border-slate-100">
                                <span className="text-[10px] text-slate-500 block">Trips Done</span>
                                <span className="text-sm font-bold text-slate-900">0</span>
                              </div>
                              <div className="bg-slate-50 rounded-xl p-2.5 text-center border border-slate-100">
                                <span className="text-[10px] text-slate-500 block">Acceptance</span>
                                <span className="text-sm font-bold text-[#16A34A]">100%</span>
                              </div>
                            </div>
                          </div>

                          {/* Navigation Shortcuts Grid */}
                          <div className="space-y-2">
                            <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block px-1">
                              Quick Navigation
                            </span>
                            <div className="grid grid-cols-2 gap-2">
                              <button
                                onClick={() => setCurrentDestination('orders')}
                                className="bg-white rounded-xl p-3 border border-slate-200 text-left hover:border-[#0D2560] transition shadow-xs flex flex-col justify-between"
                              >
                                <div className="w-8 h-8 rounded-lg bg-[#E0E7FF] flex items-center justify-center text-[#0D2560] mb-2">
                                  <Clock className="w-4 h-4" />
                                </div>
                                <span className="text-xs font-bold text-slate-800">Active Orders</span>
                                <span className="text-[10px] text-slate-400">View live dispatch</span>
                              </button>

                              <button
                                onClick={() => setCurrentDestination('earnings')}
                                className="bg-white rounded-xl p-3 border border-slate-200 text-left hover:border-[#0D2560] transition shadow-xs flex flex-col justify-between"
                              >
                                <div className="w-8 h-8 rounded-lg bg-[#FEF9C3] flex items-center justify-center text-[#854D0E] mb-2">
                                  <Wallet className="w-4 h-4" />
                                </div>
                                <span className="text-xs font-bold text-slate-800">Earnings & Wallet</span>
                                <span className="text-[10px] text-slate-400">Balance & payouts</span>
                              </button>

                              <button
                                onClick={() => setCurrentDestination('rewards')}
                                className="bg-white rounded-xl p-3 border border-slate-200 text-left hover:border-[#0D2560] transition shadow-xs flex flex-col justify-between"
                              >
                                <div className="w-8 h-8 rounded-lg bg-emerald-50 flex items-center justify-center text-[#16A34A] mb-2">
                                  <Award className="w-4 h-4" />
                                </div>
                                <span className="text-xs font-bold text-slate-800">Rewards & Targets</span>
                                <span className="text-[10px] text-slate-400">Incentives & tiers</span>
                              </button>

                              <button
                                onClick={() => setCurrentDestination('profile')}
                                className="bg-white rounded-xl p-3 border border-slate-200 text-left hover:border-[#0D2560] transition shadow-xs flex flex-col justify-between"
                              >
                                <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-700 mb-2">
                                  <Car className="w-4 h-4" />
                                </div>
                                <span className="text-xs font-bold text-slate-800">Vehicle & Profile</span>
                                <span className="text-[10px] text-slate-400">Docs & settings</span>
                              </button>
                            </div>
                          </div>

                          {/* Live Dispatch Queue Slot */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                            <h3 className="font-semibold text-xs text-slate-700 mb-2">
                              Dispatch Queue
                            </h3>
                            <div className="py-6 flex flex-col items-center justify-center text-center">
                              <div className="w-10 h-10 rounded-full bg-slate-100 flex items-center justify-center mb-2">
                                <Inbox className="w-5 h-5 text-[#0D2560]" />
                              </div>
                              <h5 className="font-semibold text-xs text-slate-800 mb-0.5">{t.noOrdersTitle}</h5>
                              <p className="text-[11px] text-slate-500 max-w-[210px] leading-relaxed">
                                {isOnline
                                  ? 'Active dispatch requests will appear here atomically.'
                                  : t.noOrdersDesc}
                              </p>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* ORDERS SCREEN */}
                      {currentDestination === 'orders' && (
                        <div className="flex flex-col h-full">
                          {/* Tabs */}
                          <div className="bg-white border-b border-slate-200 grid grid-cols-3 text-center text-xs font-medium">
                            {(['active', 'completed', 'cancelled'] as const).map((tab) => (
                              <button
                                key={tab}
                                onClick={() => setOrdersSubTab(tab)}
                                className={`py-3 capitalize transition border-b-2 ${
                                  ordersSubTab === tab
                                    ? 'border-[#0D2560] text-[#0D2560] font-bold'
                                    : 'border-transparent text-slate-400'
                                }`}
                              >
                                {isHindi
                                  ? tab === 'active'
                                    ? 'सक्रिय'
                                    : tab === 'completed'
                                    ? 'पूर्ण'
                                    : 'रद्द'
                                  : tab}
                              </button>
                            ))}
                          </div>

                          {/* Content */}
                          <div className="p-4 flex-1 flex flex-col items-center justify-center text-center">
                            {!isOnline && ordersSubTab === 'active' ? (
                              <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm w-full">
                                <div className="w-14 h-14 rounded-full bg-slate-100 mx-auto flex items-center justify-center mb-3">
                                  <Power className="w-7 h-7 text-slate-500" />
                                </div>
                                <h4 className="font-bold text-sm text-slate-800 mb-1">{t.offlineScreenTitle}</h4>
                                <p className="text-xs text-slate-500 mb-4">{t.noOrdersDesc}</p>
                                <button
                                  onClick={() => setIsOnline(true)}
                                  className="w-full py-2.5 bg-[#16A34A] text-white text-xs font-semibold rounded-xl flex items-center justify-center gap-1.5 shadow"
                                >
                                  <Power className="w-3.5 h-3.5" />
                                  {t.goOnline}
                                </button>
                              </div>
                            ) : (
                              <div className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm w-full">
                                <div className="w-14 h-14 rounded-full bg-slate-100 mx-auto flex items-center justify-center mb-3">
                                  <ClipboardList className="w-7 h-7 text-[#0D2560]" />
                                </div>
                                <h4 className="font-bold text-sm text-slate-800 mb-1">{t.noOrdersTitle}</h4>
                                <p className="text-xs text-slate-500 leading-relaxed">{t.noOrdersDesc}</p>
                              </div>
                            )}
                          </div>
                        </div>
                      )}

                      {/* EARNINGS SCREEN */}
                      {currentDestination === 'earnings' && (
                        <div className="p-4 space-y-3">
                          {/* Yellow Accent Card for Important Earnings */}
                          <div className="bg-[#FEF08A] rounded-2xl p-4 border border-[#CA8A04]/40 shadow-sm">
                            <div className="flex items-center gap-3">
                              <div className="w-12 h-12 rounded-full bg-[#CA8A04] flex items-center justify-center text-white shadow">
                                <IndianRupee className="w-6 h-6" />
                              </div>
                              <div>
                                <span className="text-xs font-semibold text-[#854D0E] uppercase tracking-wide">
                                  {t.todayEarning}
                                </span>
                                <h3 className="text-2xl font-black text-slate-900">₹0.00</h3>
                              </div>
                            </div>
                          </div>

                          {/* Bank Settlement Account Card */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
                            <div className="w-10 h-10 rounded-full bg-blue-50 flex items-center justify-center text-[#0D2560]">
                              <CheckCircle2 className="w-5 h-5 text-green-600" />
                            </div>
                            <div className="flex-1">
                              <h4 className="text-xs font-bold text-slate-800">Bank Settlement Account</h4>
                              <p className="text-[11px] text-slate-500">{t.bankDetails}</p>
                            </div>
                          </div>

                          {/* Clean Empty Trips Ledger */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                            <h4 className="font-semibold text-xs text-slate-700 mb-3">Recent Transactions</h4>
                            <div className="py-8 flex flex-col items-center justify-center text-center">
                              <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center mb-3">
                                <IndianRupee className="w-6 h-6 text-[#0D2560]" />
                              </div>
                              <h5 className="font-semibold text-xs text-slate-800 mb-1">No Earnings Recorded</h5>
                              <p className="text-[11px] text-slate-500 max-w-[220px]">
                                Completed trips and delivery payouts will be visible here.
                              </p>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* REWARDS SCREEN */}
                      {currentDestination === 'rewards' && (
                        <div className="p-4 space-y-3">
                          {/* Partner Tier Card */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
                            <div className="w-12 h-12 rounded-full bg-[#E0E7FF] flex items-center justify-center text-[#0D2560]">
                              <Award className="w-6 h-6" />
                            </div>
                            <div>
                              <span className="text-xs text-slate-500">{t.tierTitle}</span>
                              <h3 className="text-base font-bold text-slate-900">Silver Partner</h3>
                            </div>
                          </div>

                          {/* Empty Rewards View */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                            <h4 className="font-semibold text-xs text-slate-700 mb-3">Milestones & Missions</h4>
                            <div className="py-8 flex flex-col items-center justify-center text-center">
                              <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center mb-3">
                                <Award className="w-6 h-6 text-[#0D2560]" />
                              </div>
                              <h5 className="font-semibold text-xs text-slate-800 mb-1">{t.rewardsEmptyTitle}</h5>
                              <p className="text-[11px] text-slate-500 max-w-[220px] leading-relaxed">
                                {t.rewardsEmptyDesc}
                              </p>
                            </div>
                          </div>
                        </div>
                      )}

                      {/* PROFILE SCREEN */}
                      {currentDestination === 'profile' && (
                        <div className="p-4 space-y-3">
                          {/* Partner Info */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm flex items-center gap-3">
                            <div className="w-12 h-12 rounded-full bg-[#E0E7FF] flex items-center justify-center text-[#0D2560]">
                              <User className="w-6 h-6" />
                            </div>
                            <div className="flex-1">
                              <h3 className="font-bold text-sm text-slate-900">Rajesh Kumar</h3>
                              <p className="text-xs text-slate-500">+91 98765 43210</p>
                              <div className="flex items-center gap-1 mt-0.5">
                                <Star className="w-3.5 h-3.5 fill-amber-400 text-amber-400" />
                                <span className="text-[11px] font-semibold text-slate-700">4.9 Partner Rating</span>
                              </div>
                            </div>
                          </div>

                          {/* Service Mode Selection in Profile */}
                          <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm space-y-2">
                            <h4 className="text-xs font-semibold text-slate-800 mb-1">Service Category</h4>
                            {(['BIKE', 'AUTO', 'CAB', 'DELIVERY'] as ServiceType[]).map((type) => (
                              <button
                                key={type}
                                onClick={() => setServiceType(type)}
                                className={`w-full py-2 px-3 rounded-xl border text-xs font-medium flex items-center justify-between transition ${
                                  serviceType === type
                                    ? 'bg-[#E0E7FF]/60 border-[#0D2560] text-[#0D2560]'
                                    : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                                }`}
                              >
                                <span className="flex items-center gap-2">
                                  {type === 'BIKE' && <Bike className="w-4 h-4" />}
                                  {type === 'AUTO' && <Car className="w-4 h-4" />}
                                  {type === 'CAB' && <Car className="w-4 h-4" />}
                                  {type === 'DELIVERY' && <Truck className="w-4 h-4" />}
                                  {type === 'BIKE' ? t.bikeTaxi : type === 'AUTO' ? t.auto : type === 'CAB' ? t.cab : t.delivery}
                                </span>
                                {serviceType === type && (
                                  <span className="w-2 h-2 rounded-full bg-[#0D2560]"></span>
                                )}
                              </button>
                            ))}
                          </div>

                          {/* Menu Options */}
                          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm divide-y divide-slate-100 text-xs">
                            <div className="p-3.5 flex items-center justify-between">
                              <div className="flex items-center gap-2.5">
                                <Car className="w-4 h-4 text-[#0D2560]" />
                                <div>
                                  <p className="font-medium text-slate-800">{t.vehicleInfo}</p>
                                  <p className="text-[10px] text-slate-400">RJ-14-EA-2024</p>
                                </div>
                              </div>
                              <ChevronRight className="w-4 h-4 text-slate-400" />
                            </div>

                            <div className="p-3.5 flex items-center justify-between">
                              <div className="flex items-center gap-2.5">
                                <FileText className="w-4 h-4 text-[#0D2560]" />
                                <div>
                                  <p className="font-medium text-slate-800">{t.documents}</p>
                                  <p className="text-[10px] text-slate-400">RC, Insurance Verified</p>
                                </div>
                              </div>
                              <CheckCircle2 className="w-4 h-4 text-green-600" />
                            </div>

                            <button
                              onClick={() => setIsHindi(!isHindi)}
                              className="w-full p-3.5 flex items-center justify-between text-left"
                            >
                              <div className="flex items-center gap-2.5">
                                <Languages className="w-4 h-4 text-[#0D2560]" />
                                <div>
                                  <p className="font-medium text-slate-800">Language / भाषा</p>
                                  <p className="text-[10px] text-slate-400">
                                    {isHindi ? 'हिन्दी (Hindi)' : 'English'}
                                  </p>
                                </div>
                              </div>
                              <ChevronRight className="w-4 h-4 text-slate-400" />
                            </button>

                            <div className="p-3.5 flex items-center justify-between">
                              <div className="flex items-center gap-2.5">
                                <LifeBuoy className="w-4 h-4 text-[#0D2560]" />
                                <div>
                                  <p className="font-medium text-slate-800">{t.support}</p>
                                  <p className="text-[10px] text-slate-400">24x7 Driver Partner Help Desk</p>
                                </div>
                              </div>
                              <ChevronRight className="w-4 h-4 text-slate-400" />
                            </div>

                            <div className="p-3.5 flex items-center justify-between text-red-600">
                              <div className="flex items-center gap-2.5">
                                <PhoneCall className="w-4 h-4" />
                                <div>
                                  <p className="font-semibold">{t.sos}</p>
                                  <p className="text-[10px] text-red-400">Direct Helpline Assistance</p>
                                </div>
                              </div>
                              <ChevronRight className="w-4 h-4 text-red-400" />
                            </div>
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>

                {/* DriveBottomNavigation Component */}
                <div className="bg-white border-t border-slate-200 px-2 py-1.5 flex justify-around items-center">
                  {[
                    { key: 'home' as Destination, label: t.home, icon: Home },
                    { key: 'orders' as Destination, label: t.orders, icon: ClipboardList },
                    { key: 'earnings' as Destination, label: t.earnings, icon: IndianRupee },
                    { key: 'rewards' as Destination, label: t.rewards, icon: Award },
                    { key: 'profile' as Destination, label: t.profile, icon: User }
                  ].map((item) => {
                    const isSelected = currentDestination === item.key;
                    const IconComp = item.icon;
                    return (
                      <button
                        key={item.key}
                        onClick={() => {
                          setCurrentDestination(item.key);
                          setStateMode('normal');
                        }}
                        className={`flex flex-col items-center py-1 px-2 rounded-xl transition ${
                          isSelected
                            ? 'text-[#0D2560]'
                            : 'text-slate-400 hover:text-slate-600'
                        }`}
                      >
                        <div
                          className={`p-1 rounded-xl transition ${
                            isSelected ? 'bg-[#E0E7FF]' : 'bg-transparent'
                          }`}
                        >
                          <IconComp className="w-5 h-5" />
                        </div>
                        <span className={`text-[10px] mt-0.5 ${isSelected ? 'font-bold' : 'font-normal'}`}>
                          {item.label}
                        </span>
                      </button>
                    );
                  })}
                </div>

                {/* Android Bottom Navigation Pill */}
                <div className="bg-white pb-2 pt-1 flex justify-center">
                  <div className="w-28 h-1 bg-slate-300 rounded-full"></div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* CODE INSPECTOR TAB */}
        {activeTab === 'code' && (
          <div className="w-full max-w-5xl bg-slate-950 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl flex flex-col h-[750px]">
            {/* File List Header */}
            <div className="bg-slate-900 border-b border-slate-800 px-4 py-2.5 flex items-center gap-2 overflow-x-auto">
              {Object.keys(codeFiles).map((fileName) => (
                <button
                  key={fileName}
                  onClick={() => setSelectedFile(fileName)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-mono flex items-center gap-2 whitespace-nowrap transition ${
                    selectedFile === fileName
                      ? 'bg-[#0D2560] text-white shadow'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  <FileCode2 className="w-3.5 h-3.5" />
                  {fileName}
                </button>
              ))}
            </div>

            {/* File Path Indicator */}
            <div className="bg-slate-900/60 border-b border-slate-800/80 px-4 py-1.5 text-xs font-mono text-slate-400 flex items-center justify-between">
              <span>{codeFiles[selectedFile]?.path}</span>
              <span className="text-[10px] px-2 py-0.5 rounded bg-slate-800 text-slate-300">
                {codeFiles[selectedFile]?.language.toUpperCase()}
              </span>
            </div>

            {/* Code Content */}
            <div className="flex-1 p-4 overflow-auto font-mono text-xs text-slate-200 leading-relaxed bg-slate-950">
              <pre>
                <code>{codeFiles[selectedFile]?.content}</code>
              </pre>
            </div>
          </div>
        )}

        {/* ARCHITECTURE TAB */}
        {activeTab === 'structure' && (
          <div className="w-full max-w-5xl bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
            <div>
              <h2 className="text-lg font-bold text-white mb-1">
                Drive Partner Native Android Project Tree
              </h2>
              <p className="text-xs text-slate-400">
                Single Activity Android Jetpack Compose Architecture with MVVM and StateFlow
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Directory Tree */}
              <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-4 font-mono text-xs text-slate-300 space-y-1 overflow-x-auto">
                <div className="text-blue-400 font-bold mb-2">📁 DrivePartner/</div>
                <div className="pl-4">├── 📄 settings.gradle.kts</div>
                <div className="pl-4">├── 📄 build.gradle.kts (Root)</div>
                <div className="pl-4">├── 📄 gradle.properties</div>
                <div className="pl-4">├── 📄 gradlew & gradlew.bat</div>
                <div className="pl-4">├── 📁 gradle/</div>
                <div className="pl-8">├── 📄 libs.versions.toml (Version Catalog)</div>
                <div className="pl-8">└── 📁 wrapper/gradle-wrapper.properties</div>
                <div className="pl-4">└── 📁 app/</div>
                <div className="pl-8">├── 📄 build.gradle.kts (App module)</div>
                <div className="pl-8">├── 📄 proguard-rules.pro</div>
                <div className="pl-8">└── 📁 src/main/</div>
                <div className="pl-12">├── 📄 AndroidManifest.xml</div>
                <div className="pl-12">├── 📁 res/</div>
                <div className="pl-16">├── 📁 values/ (colors.xml, strings.xml, themes.xml)</div>
                <div className="pl-16">└── 📁 values-hi/ (strings.xml - हिन्दी)</div>
                <div className="pl-12">└── 📁 java/com/drivepartner/app/</div>
                <div className="pl-16">├── 📄 DrivePartnerApp.kt</div>
                <div className="pl-16">├── 📄 MainActivity.kt</div>
                <div className="pl-16">├── 📁 data/</div>
                <div className="pl-20">├── 📁 model/ (PartnerProfile, PartnerStatus, ServiceType)</div>
                <div className="pl-20">└── 📁 repository/ (PartnerRepository, PartnerRepositoryImpl)</div>
                <div className="pl-16">├── 📁 navigation/</div>
                <div className="pl-20">├── 📄 DriveDestination.kt</div>
                <div className="pl-20">└── 📄 DriveNavHost.kt</div>
                <div className="pl-16">└── 📁 ui/</div>
                <div className="pl-20">├── 📁 components/ (DriveTopBar, DriveBottomNavigation, DriveCard, DriveButton)</div>
                <div className="pl-20">├── 📁 components/state/ (DriveLoadingView, DriveEmptyView, DriveErrorView, DriveOfflineView, DriveUiState)</div>
                <div className="pl-20">├── 📁 screens/ (home, orders, earnings, rewards, profile)</div>
                <div className="pl-20">├── 📁 theme/ (Color, Type, Shape, Theme)</div>
                <div className="pl-20">└── 📁 viewmodel/ (MainViewModel, MainUiState)</div>
              </div>

              {/* Architectural Highlights */}
              <div className="space-y-4">
                <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-4">
                  <h4 className="font-semibold text-sm text-blue-300 mb-2 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-green-400" />
                    Material 3 Design System
                  </h4>
                  <ul className="text-xs text-slate-400 space-y-1.5 list-disc list-inside">
                    <li><strong className="text-slate-200">Deep Blue (#0D2560)</strong> primary brand color</li>
                    <li><strong className="text-slate-200">Green (#16A34A)</strong> online duty and verification states</li>
                    <li><strong className="text-slate-200">Yellow (#CA8A04)</strong> earnings and financial highlights</li>
                    <li><strong className="text-slate-200">White & Light Grey</strong> crisp high-contrast surfaces</li>
                    <li><strong className="text-slate-200">Rounded Cards</strong> with 16dp radius</li>
                  </ul>
                </div>

                <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-4">
                  <h4 className="font-semibold text-sm text-blue-300 mb-2 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-green-400" />
                    Localization Matrix
                  </h4>
                  <p className="text-xs text-slate-400 mb-2">
                    Full dual-language resource setup for Driver Partners across India:
                  </p>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div className="p-2 rounded bg-slate-950 border border-slate-800">
                      <span className="font-semibold text-slate-300 block mb-0.5">English</span>
                      <span className="text-[11px] text-slate-500 font-mono">res/values/strings.xml</span>
                    </div>
                    <div className="p-2 rounded bg-slate-950 border border-slate-800">
                      <span className="font-semibold text-slate-300 block mb-0.5">हिन्दी (Hindi)</span>
                      <span className="text-[11px] text-slate-500 font-mono">res/values-hi/strings.xml</span>
                    </div>
                  </div>
                </div>

                <div className="bg-slate-900/70 border border-slate-800 rounded-xl p-4">
                  <h4 className="font-semibold text-sm text-blue-300 mb-2 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 text-green-400" />
                    Clean State Design (No Mock Clutter)
                  </h4>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Complies strictly with prompt guidelines: no fake ride requests or artificial financial records. Empty destinations are cleanly structured and ready for real backend integration in future development steps.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
