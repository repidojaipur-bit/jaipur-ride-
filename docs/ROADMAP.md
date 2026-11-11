# Drive Partner — Implementation Roadmap & Phase Master Blueprint

**Document ID:** DP-ROADMAP-001  
**Version:** 1.0.0  
**Status:** Approved Roadmap & Phase Execution Plan  
**Target Platform:** Native Android (Kotlin + Jetpack Compose + Material 3)  

---

## 1. Executive Summary & Strategy

This roadmap details the sequential, dependency-aware phase execution plan for the **Drive Partner** application. Development is strictly phased to ensure clean architecture, non-regressive compilation, zero-fake-data discipline, and reliable state management.

### Architectural Principles & Boundaries
1. **Platform Boundary**: The Android client workspace owns client-side Kotlin/Compose code, local state repositories, and UI navigation. The backend API is a separate service owning order dispatch, fare calculation, ledger posting, and driver eligibility.
2. **Sequential Phase Execution**: Phases must be executed strictly in order. Prerequisite phases must pass compilation, static analysis, and verification before advancing to the next phase.
3. **Evidence-Based Progress Tracking**: Unfinished features are never marked as complete without compilation and lint verification.

---

## 2. Dependency-Aware Phase Sequence

```
[Phase 1: Project Setup & Core Design System]
         │
         ▼
[Phase 2: Data Architecture & Repository Baseline]
         │
         ▼
[Phase 3: Authentication & Onboarding Workflows]
         │
         ▼
[Phase 4: Home Cockpit, Duty Control & Service Zone Preferences]
         │
         ▼
[Phase 5: Real-Time Dispatch & Atomic Order Offer Acceptance]
         │
         ▼
[Phase 6: Active Trip Lifecycle State Machine]
         │
         ▼
[Phase 7: Order History, Earnings Ledger & Wallet Payouts]
         │
         ▼
[Phase 8: Rewards, Incentives & Referrals]
         │
         ▼
[Phase 9: Safety Center, Emergency SOS & Support Tickets]
         │
         ▼
[Phase 10: System Integration, Performance & End-to-End Verification]
```

---

## 3. Comprehensive Phase Specifications

---

### Phase 1: Project Setup & Core Design System Baseline
- **Goal**: Establish the native Android project structure, Material 3 design tokens, theme, and 13 reusable UI components.
- **Scope & Features**:
  - Gradle Kotlin DSL configuration, AndroidManifest, string resources (English & Hindi).
  - Material 3 Design System (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`).
  - 13 Reusable Compose Components (`PrimaryButton`, `SecondaryButton`, `StatusChip`, `EarningsCard`, `OrderRequestCard`, `NavigationBar`, `OnlineDutySwitch`, `ProfileCard`, `RewardCard`, `TransactionCard`, `LoadingIndicator`, `EmptyState`, `ErrorState`).
  - Navigation skeleton (`DriveDestination.kt`, `DriveNavHost.kt`).
- **Files Created / Modified**:
  - `app/build.gradle.kts`, `res/values/strings.xml`, `res/values-hi/strings.xml`.
  - `ui/theme/*`, `ui/components/*`, `navigation/*`.
- **Technical & Backend Dependencies**: Android SDK 35, Jetpack Compose, Material 3, Navigation Compose.
- **Acceptance Criteria**: App compiles cleanly (`compile_applet`), zero lint errors (`lint_applet`), light/dark preview support.
- **Status**: **Completed & Verified**.

---

### Phase 2: Data Architecture & Repository Baseline
- **Goal**: Implement core domain models, state holders, location management, and repository layer using Kotlin Coroutines and StateFlow.
- **Scope & Features**:
  - Domain models: `PartnerProfile`, `PartnerStatus`, `ServiceType`.
  - Repositories: `PartnerRepository` (interface), `PartnerRepositoryImpl` (StateFlow state manager).
  - Location Services: `LocationManager` and `PartnerLocation` using Google Play Services FusedLocationProviderClient.
  - ViewModel: `MainViewModel` for global UI state synchronization.
- **Files Created / Modified**:
  - `data/model/PartnerProfile.kt`, `PartnerStatus.kt`, `ServiceType.kt`.
  - `data/repository/PartnerRepository.kt`, `PartnerRepositoryImpl.kt`.
  - `data/location/LocationManager.kt`, `PartnerLocation.kt`.
  - `ui/viewmodel/MainViewModel.kt`.
- **Technical & Backend Dependencies**: `play-services-location`, `kotlinx-coroutines-core`, `lifecycle-viewmodel-compose`.
- **Acceptance Criteria**: StateFlow emits updates asynchronously; LocationManager safely handles location permission checks.
- **Status**: **Completed & Verified**.

---

### Phase 3: Driver Authentication & Onboarding Workflows
- **Goal**: Implement complete driver onboarding funnel from splash to document review status.
- **Scope & Features**:
  - `S-01 Splash`: Session restoration & environment check.
  - `S-02 Language Selection`: English / Hindi selection stored in DataStore.
  - `S-03 Phone Login`: 10-digit phone entry with validation.
  - `S-04 OTP Verification`: 6-digit OTP verification with 60s countdown timer.
  - `S-05 Driver Registration`: Personal details, photo picker, DOB >= 18 validation.
  - `S-06 Vehicle Registration`: Service category selection (Bike, Auto, Cab, Delivery), number plate formatting.
  - `S-07 Document Upload`: DL, RC, Aadhaar, PAN, Insurance upload with cropping and status badges.
  - `S-08 Verification Status`: Under Review, Action Required (rejections), and Blocked states.
- **Files to Create**:
  - `ui/screens/auth/SplashScreen.kt`, `LanguageSelectionScreen.kt`, `PhoneLoginScreen.kt`, `OtpVerificationScreen.kt`.
  - `ui/screens/onboarding/DriverRegistrationScreen.kt`, `VehicleRegistrationScreen.kt`, `DocumentUploadScreen.kt`, `VerificationStatusScreen.kt`.
  - `ui/viewmodel/AuthViewModel.kt`, `OnboardingViewModel.kt`.
- **Backend Dependencies**: `POST /v1/auth/phone/otp/request`, `/verify`, `POST /v1/documents/upload-url`, `GET /v1/driver/verification-status`.
- **Acceptance Criteria**: Smooth multi-step flow; draft state preserved locally and synced; invalid inputs blocked.
- **Status**: **Pending Execution (Ready to Begin)**.

---

### Phase 4: Home Cockpit, Duty Control & Service Zone Preferences
- **Goal**: Wire driver home dashboard, duty availability switch, today's summary metrics, quick shortcuts, and service zone preferences.
- **Scope & Features**:
  - `S-09 Home Dashboard`: Partner identity, duty switch, today's summary card, shortcut grid.
  - `S-10 Service Area`: Operating cluster map boundary, sub-zone toggles, Home Route destination picker (max 2/day), Auto Boost toggle.
  - `S-11 Duty Control`: Pre-flight verification (GPS accuracy, document validity, location permission check) before going Online.
- **Files Created / Modified**:
  - `ui/screens/home/HomeScreen.kt`, `TodaySummaryCard.kt`, `DispatchQueueCard.kt`.
  - `ui/screens/home/ServiceAreaScreen.kt`, `DutyStatusSheet.kt`.
- **Backend Dependencies**: `GET /v1/driver/dashboard/today`, `POST /v1/driver/duty/online`, `/offline`, `GET /v1/service-areas`.
- **Acceptance Criteria**: Duty toggle checks pre-flight permissions; going online starts foreground location tracking; offline stops tracking immediately.
- **Status**: **In Progress (UI components completed, API integration pending in Phase 4 execution)**.

---

### Phase 5: Real-Time Dispatch & Atomic Order Offer Acceptance
- **Goal**: Full-screen atomic incoming booking request modal with 15-second countdown timer and double-tap prevention.
- **Scope & Features**:
  - `S-19 Incoming Offer Overlay`: Full-screen dialog with service category badge, pickup distance, drop address, trip distance, guaranteed fare, and 15s radial countdown.
  - `S-18 Priority Orders`: Scheduled/airport priority dispatch list.
  - Atomic acceptance handling: Sends `X-Idempotency-Key` header; disables buttons immediately upon press.
  - System back-button suppression via `BackHandler` during offer ring.
- **Files to Create**:
  - `ui/screens/dispatch/IncomingOfferOverlay.kt`, `PriorityOrdersScreen.kt`.
  - `ui/viewmodel/DispatchViewModel.kt`.
- **Backend Dependencies**: WebSocket/FCM `driver/{id}/offers`, `POST /v1/dispatch/offers/{id}/accept`, `/reject`.
- **Acceptance Criteria**: Offer countdown syncs with server expiry time; double taps prevented; accepted offers navigate immediately to Active Trip.
- **Status**: **Pending Execution**.

---

### Phase 6: Active Trip Lifecycle State Machine
- **Goal**: Complete state-machine trip execution workflow with live GPS route tracking.
- **Scope & Features**:
  - `S-20 Active Trip`: Sequential state transitions: `Assigned` ➔ `En-Route to Pickup` ➔ `Arrived` ➔ `In-Progress` (requires 4-digit start OTP) ➔ `Completed`.
  - `S-21 Trip Completion Receipt`: Itemized fare breakdown, Cash collection prompt ("COLLECT CASH: ₹145" vs "PAID ONLINE — DO NOT COLLECT"), customer rating.
  - Customer contact bar (masked call & in-app chat).
  - Cancellation bottom sheet with regulatory reason selector.
- **Files to Create**:
  - `ui/screens/trip/ActiveTripScreen.kt`, `TripCompletionScreen.kt`, `OtpVerificationDialog.kt`.
  - `ui/viewmodel/TripViewModel.kt`.
- **Backend Dependencies**: `POST /v1/trips/{id}/arrived`, `/start`, `/complete`, `/cancel`, `/payment-confirm`.
- **Acceptance Criteria**: Process death restores active trip stage; invalid start OTP blocked; fare calculation strictly server-controlled.
- **Status**: **Pending Execution**.

---

### Phase 7: Order History, Earnings Ledger & Wallet Payouts
- **Goal**: Order history tabs, gross/net earnings breakdown, wallet ledger, and secure payout withdrawals.
- **Scope & Features**:
  - `S-12 Order History`: Active, Completed, Cancelled tabs with date range filtering.
  - `S-13 Earnings Dashboard`: Today/Weekly/Monthly totals, base fare vs incentives breakdown.
  - `S-14 Wallet`: Available withdrawable balance, pending clearance, linked bank account/UPI ID.
  - `S-15 Withdrawals`: Amount form, preset chips (`₹500`, `₹1000`, `All`), transfer fee breakdown, idempotent payout confirmation.
- **Files to Create**:
  - `ui/screens/orders/OrdersScreen.kt`, `OrderDetailSheet.kt`.
  - `ui/screens/earnings/EarningsScreen.kt`, `WalletScreen.kt`, `WithdrawalsScreen.kt`.
  - `ui/viewmodel/WalletViewModel.kt`.
- **Backend Dependencies**: `GET /v1/driver/orders`, `GET /v1/driver/earnings/summary`, `GET /v1/driver/wallet/balance`, `POST /v1/driver/wallet/withdraw`.
- **Acceptance Criteria**: Withdrawals validate against minimum limits (>= ₹100) and available balance; double payouts prevented via idempotency key.
- **Status**: **Pending Execution**.

---

### Phase 8: Rewards, Incentives & Referrals
- **Goal**: Milestone target tracking, incentive campaign tiers, and driver referral program.
- **Scope & Features**:
  - `S-16 Rewards & Incentives`: Daily/Weekly milestone cards, progress bars (% completed), qualification criteria, claim actions.
  - `S-17 Refer and Earn`: Unique driver referral code, WhatsApp share sheet trigger, referral pipeline status tracker.
- **Files to Create**:
  - `ui/screens/rewards/RewardsScreen.kt`, `ReferAndEarnScreen.kt`.
  - `ui/viewmodel/RewardsViewModel.kt`.
- **Backend Dependencies**: `GET /v1/driver/rewards/campaigns`, `GET /v1/driver/referrals/summary`, `/code`.
- **Acceptance Criteria**: Rewards awarded only after backend qualification; share sheet launches correctly; referral code copy works with toast.
- **Status**: **Pending Execution**.

---

### Phase 9: Safety Center, Emergency SOS & Support Tickets
- **Goal**: Driver emergency SOS alert, police quick-dial (112), insurance overview, and support ticket management.
- **Scope & Features**:
  - `S-22 Safety Center`: 3-second hold-to-activate emergency SOS button, direct 112 dialer, emergency contact sharing, insurance policy details.
  - `S-24 Help & Support`: Categorized FAQ search, active support tickets list with status chips, new ticket creation with photo attachment.
- **Files to Create**:
  - `ui/screens/safety/SafetyCenterScreen.kt`, `ActiveEmergencyOverlay.kt`.
  - `ui/screens/support/HelpAndSupportScreen.kt`, `CreateTicketDialog.kt`.
  - `ui/viewmodel/SafetyViewModel.kt`, `SupportViewModel.kt`.
- **Backend Dependencies**: `POST /v1/safety/sos/trigger`, `GET /v1/support/tickets`, `POST /v1/support/tickets/create`.
- **Acceptance Criteria**: 3-second hold prevents accidental SOS triggers; offline fallback provided if network fails during emergency; support attachments uploaded securely.
- **Status**: **Pending Execution**.

---

### Phase 10: System Integration, Performance & End-to-End Verification
- **Goal**: Final app hardening, edge-to-edge layout checks, cold launch optimizations, memory leak audits, and release build verification.
- **Scope & Features**:
  - Complete end-to-end user journey verification from authentication to trip completion and payout.
  - Memory leak inspection and bitmap optimization for document camera captures.
  - Production ProGuard / R8 rule verification.
  - Edge-to-edge inset handling across various screen aspect ratios.
- **Acceptance Criteria**: `compile_applet` and `lint_applet` pass with 0 errors; zero memory leaks; cold launch under 1.5 seconds.
- **Status**: **Pending Execution**.

---

## 4. Current Progress & Ready Next Phase Confirmation

- **Current State**:
  - **Phase 1 (Project Setup & Design System)**: Complete.
  - **Phase 2 (Data Architecture & Repository Baseline)**: Complete.
  - **Phase 4 UI Components**: `HomeScreen`, `TodaySummaryCard`, and `DispatchQueueCard` UI composables written and integrated.
- **Phase Ready to Begin**: **Phase 3: Driver Authentication & Onboarding Workflows**.
